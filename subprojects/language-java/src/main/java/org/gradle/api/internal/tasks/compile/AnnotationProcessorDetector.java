/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.compile;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.hash.HashCode;
import org.apache.tools.zip.ZipFile;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.api.internal.tasks.execution.TaskOutputsGenerationListener;
import org.gradle.api.tasks.compile.CompileOptions;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AnnotationProcessorDetector implements TaskOutputsGenerationListener {
    private final FileCollectionFactory fileCollectionFactory;
    private final FileHasher fileHasher;
    private final Map<File, Boolean> cache = new ConcurrentHashMap<File, Boolean>();
    private final Map<HashCode, Boolean> jarCache = new ConcurrentHashMap<HashCode, Boolean>();

    public AnnotationProcessorDetector(FileCollectionFactory fileCollectionFactory, FileHasher fileHasher) {
        this.fileCollectionFactory = fileCollectionFactory;
        this.fileHasher = fileHasher;
    }

    @Override
    public void beforeTaskOutputsGenerated() {
        // A very dumb strategy for invalidating cache
        cache.clear();
    }

    /**
     * Calculates the annotation processor path to use given some compile options and compile classpath.
     *
     * @return An empty collection when annotation processing should not be performed, non-empty when it should.
     */
    public FileCollection getEffectiveAnnotationProcessorClasspath(CompileOptions compileOptions, final FileCollection compileClasspath) {
        if (compileOptions.getCompilerArgs().contains("-proc:none")) {
            return fileCollectionFactory.empty("annotation processor path");
        }
        if (compileOptions.getAnnotationProcessorPath() != null) {
            return compileOptions.getAnnotationProcessorPath();
        }
        int pos = compileOptions.getCompilerArgs().indexOf("-processorpath");
        if (pos >= 0) {
            if (pos == compileOptions.getCompilerArgs().size() - 1) {
                throw new InvalidUserDataException("No path provided for compiler argument -processorpath in requested compiler args: " + Joiner.on(" ").join(compileOptions.getCompilerArgs()));
            }
            List<File> files = new ArrayList<File>();
            for (String path : Splitter.on(File.pathSeparatorChar).splitToList(compileOptions.getCompilerArgs().get(pos + 1))) {
                files.add(new File(path));
            }
            return fileCollectionFactory.fixed("annotation processor path", files);
        }

        return fileCollectionFactory.create(compileClasspath.getBuildDependencies(), new MinimalFileSet() {
            @Override
            public Set<File> getFiles() {
                for (File file : compileClasspath) {
                    Boolean hasServices = cache.get(file);
                    if (hasServices != null) {
                        if (hasServices) {
                            return compileClasspath.getFiles();
                        }
                        continue;
                    }

                    if (file.isDirectory()) {
                        if (new File(file, "META-INF/services/javax.annotation.processing.Processor").isFile()) {
                            cache.put(file, true);
                            return compileClasspath.getFiles();
                        }
                        cache.put(file, false);
                        continue;
                    }

                    if (file.isFile()) {
                        try {
                            HashCode hash = fileHasher.hash(file);
                            hasServices = jarCache.get(hash);
                            if (hasServices != null) {
                                cache.put(file, hasServices);
                                if (hasServices) {
                                    return compileClasspath.getFiles();
                                }
                                continue;
                            }

                            ZipFile zipFile = new ZipFile(file);
                            try {
                                if (zipFile.getEntry("META-INF/services/javax.annotation.processing.Processor") != null) {
                                    cache.put(file, true);
                                    jarCache.put(hash, true);
                                    return compileClasspath.getFiles();
                                }
                                cache.put(file, false);
                                jarCache.put(hash, false);
                            } finally {
                                zipFile.close();
                            }
                        } catch (IOException e) {
                            throw new UncheckedIOException("Could not read service definition from JAR " + file, e);
                        }
                    }
                }
                return Collections.emptySet();
            }

            @Override
            public String getDisplayName() {
                return "annotation processor path";
            }
        });
    }

}
