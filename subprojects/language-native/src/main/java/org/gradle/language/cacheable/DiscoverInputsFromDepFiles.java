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

package org.gradle.language.cacheable;

import org.apache.commons.io.IOUtils;
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.internal.changedetection.changes.IncrementalTaskInputsInternal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.internal.UncheckedException;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DiscoverInputsFromDepFiles extends AbstractNativeTask {
    private File dependencyFile;

    @Inject
    public DiscoverInputsFromDepFiles(WorkerExecutor workerExecutor) {
        super(workerExecutor);
    }

    @OutputFile
    public File getDependencyFile() {
        return dependencyFile;
    }

    public void setDependencyFile(File dependencyFile) {
        this.dependencyFile = dependencyFile;
    }

    @TaskAction
    public void preprocess(IncrementalTaskInputs incrementalTaskInputs) throws IOException {
        final File discoveredDependenciesDir = getTemporaryDir();
        final ConcurrentHashMap<String, Boolean> seenIncludes = new ConcurrentHashMap<String, Boolean>();
        final ConcurrentHashMap<String, Boolean> discoveredIncludeFiles = new ConcurrentHashMap<String, Boolean>();
        final IncrementalTaskInputsInternal inputs = (IncrementalTaskInputsInternal) incrementalTaskInputs;
        final Set<File> depFiles = new HashSet<File>();
        getSource().visit(new EmptyFileVisitor() {
            @Override
            public void visitFile(final FileVisitDetails fileVisitDetails) {
                String name = fileVisitDetails.getName();
                if (!(name.endsWith(".cpp") || name.endsWith(".c"))) {
                    return;
                }
                String base = name.replaceAll("\\.c(pp)?", "");
                String depName = base + ".d";
                final File depFile = fileVisitDetails.getRelativePath().getParent().append(true, depName).getFile(discoveredDependenciesDir);
                depFile.getParentFile().mkdirs();
                runGxx("-M",
                    "-o", depFile.getAbsolutePath(),
                    fileVisitDetails.getFile().getAbsolutePath());
                depFiles.add(depFile);
            }
        });
        getWorkerExecutor().await();
        for (File depFile : depFiles) {
            addDiscoveredInputs(inputs, depFile, seenIncludes, discoveredIncludeFiles);
        }
        DependenciesFile.write(discoveredIncludeFiles.keySet(), getDependencyFile());
    }


    public static void addDiscoveredInputs(final IncrementalTaskInputsInternal taskInputsInternal, File depFile, final ConcurrentHashMap<String, Boolean> seenIncludes, final ConcurrentHashMap<String, Boolean> discoveredIncludeFiles) {
        try {
            BufferedReader depFileReader = new BufferedReader(new InputStreamReader(new FileInputStream(depFile)));
            try {
                Collection<String> dependencies = DepFile.parseDepfile(depFileReader);
                for (String dependency : dependencies) {
                    if (seenIncludes.putIfAbsent(dependency, Boolean.TRUE) == null) {
                        File canonicalFile = new File(dependency).getCanonicalFile();
                        if (discoveredIncludeFiles.putIfAbsent(canonicalFile.getAbsolutePath(), Boolean.TRUE) == null) {
                            taskInputsInternal.newInput(canonicalFile);
                        }
                    }
                }
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            } finally {
                IOUtils.closeQuietly(depFileReader);
            }
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

}
