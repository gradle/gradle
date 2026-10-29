/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.plugins.quality.internal.codenarc;

import org.codenarc.analyzer.AbstractSourceAnalyzer;
import org.codenarc.results.DirectoryResults;
import org.codenarc.results.FileResults;
import org.codenarc.results.Results;
import org.codenarc.rule.Violation;
import org.codenarc.ruleset.RuleSet;
import org.codenarc.source.SourceCode;
import org.codenarc.source.SourceFile;
import org.codenarc.util.PathUtil;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Analyzes source files given as relative paths per source directory, like the {@code AntFileSetSourceAnalyzer} of the CodeNarc Ant task.
 * <p>
 * Runs against all CodeNarc versions, so it must only use CodeNarc API that exists since CodeNarc 0.24.1.
 * API added later is detected and used reflectively or behind a check.
 */
class GradleSourceAnalyzer extends AbstractSourceAnalyzer {

    private static final Logger LOGGER = LoggerFactory.getLogger(GradleSourceAnalyzer.class);
    private static final int POOL_TIMEOUT_SECONDS = 60 * 60;

    private final Map<File, List<String>> sourceTrees;

    /**
     * Present in CodeNarc 3.0 and older, where directory results count their files and
     * only files with violations get file results.
     */
    @Nullable
    private final Method setNumberOfFilesInThisDirectory;

    /**
     * Whether file results carry their source code, which CodeNarc plugins like the one for
     * {@code codenarc-disable} comments need. Supported since CodeNarc 2.0.
     */
    private final boolean fileResultsHaveSourceCode;

    private final ConcurrentMap<String, List<FileResults>> resultsMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> fileCountMap = new ConcurrentHashMap<>();
    private final AtomicLong sourceFileErrors = new AtomicLong();

    GradleSourceAnalyzer(Map<File, List<String>> sourceTrees) {
        this.sourceTrees = sourceTrees;
        this.setNumberOfFilesInThisDirectory = findMethod(DirectoryResults.class, "setNumberOfFilesInThisDirectory", int.class);
        this.fileResultsHaveSourceCode = hasConstructor(FileResults.class, String.class, List.class, SourceCode.class);
    }

    @Override
    public Results analyze(RuleSet ruleSet) {
        long startTime = System.currentTimeMillis();
        int numThreads = Math.max(Runtime.getRuntime().availableProcessors() - 1, 1);
        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        for (Map.Entry<File, List<String>> sourceTree : sourceTrees.entrySet()) {
            File dir = sourceTree.getKey();
            for (String filePath : sourceTree.getValue()) {
                pool.execute(() -> {
                    try {
                        processFile(dir, filePath, ruleSet);
                    } catch (Throwable t) {
                        LOGGER.warn("Error processing file: '" + filePath + "'; " + t);
                        sourceFileErrors.incrementAndGet();
                    }
                });
            }
        }
        waitForThreadsToComplete(pool);

        DirectoryResults reportResults = new DirectoryResults();
        addDirectoryResults(reportResults);
        LOGGER.info("Analysis time=" + (System.currentTimeMillis() - startTime) + "ms; Files with analysis errors: " + sourceFileErrors.get());
        return reportResults;
    }

    @Override
    public List<String> getSourceDirectories() {
        // The Ant task shows them relative to the Ant project's base directory, which is the worker's working directory
        // in Gradle and thus never contains them
        List<String> result = new ArrayList<>();
        for (File dir : sourceTrees.keySet()) {
            result.add(dir.getAbsolutePath());
        }
        return result;
    }

    private static void waitForThreadsToComplete(ExecutorService pool) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(POOL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Thread Pool terminated before completion");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Thread Pool interrupted before completion", e);
        }
    }

    private void processFile(File dir, String filePath, RuleSet ruleSet) {
        SourceFile sourceFile = new SourceFile(new File(dir, filePath));
        List<Violation> violations = collectViolations(sourceFile, ruleSet);
        String parentPath = PathUtil.getParentPath(filePath);
        String safeParentPath = parentPath != null ? parentPath : "";
        List<FileResults> dirResults = resultsMap.computeIfAbsent(safeParentPath, path -> Collections.synchronizedList(new ArrayList<>()));
        if (setNumberOfFilesInThisDirectory == null || (violations != null && !violations.isEmpty())) {
            dirResults.add(newFileResults(PathUtil.normalizePath(filePath), violations, sourceFile));
        }
        fileCountMap.computeIfAbsent(safeParentPath, path -> new AtomicInteger()).incrementAndGet();
    }

    private FileResults newFileResults(String path, List<Violation> violations, SourceFile sourceFile) {
        return fileResultsHaveSourceCode ? new FileResults(path, violations, sourceFile) : new FileResults(path, violations);
    }

    private void addDirectoryResults(DirectoryResults reportResults) {
        List<String> allPaths = new ArrayList<>(resultsMap.keySet());
        Collections.sort(allPaths);
        for (String path : allPaths) {
            DirectoryResults dirResults = new DirectoryResults(path);
            List<FileResults> allResults = resultsMap.get(path);
            allResults.sort(Comparator.comparing(FileResults::getPath));
            for (FileResults child : allResults) {
                dirResults.addChild(child);
            }
            if (setNumberOfFilesInThisDirectory != null) {
                AtomicInteger count = fileCountMap.get(path);
                invoke(setNumberOfFilesInThisDirectory, dirResults, count != null ? count.get() : 0);
            }
            addToParentResults(reportResults, dirResults);
        }
    }

    private static void addToParentResults(DirectoryResults reportResults, Results results) {
        String parentPath = PathUtil.getParentPath(results.getPath());
        if (parentPath == null) {
            reportResults.addChild(results);
            return;
        }
        DirectoryResults parent = (DirectoryResults) reportResults.findResultsForPath(parentPath);
        if (parent == null) {
            parent = new DirectoryResults(parentPath);
            addToParentResults(reportResults, parent);
        }
        parent.addChild(results);
    }

    @Nullable
    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static boolean hasConstructor(Class<?> type, Class<?>... parameterTypes) {
        try {
            return type.getConstructor(parameterTypes) != null;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static void invoke(Method method, Object target, Object... args) {
        try {
            method.invoke(target, args);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e.getCause());
        }
    }
}
