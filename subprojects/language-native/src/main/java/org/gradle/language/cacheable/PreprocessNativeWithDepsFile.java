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

import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.internal.changedetection.changes.IncrementalTaskInputsInternal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PreprocessNativeWithDepsFile extends AbstractNativeTask {
    private File preprocessedSourcesDir;

    @OutputDirectory
    public File getPreprocessedSourcesDir() {
        return preprocessedSourcesDir;
    }

    public void setPreprocessedSourcesDir(File preprocessedSourcesDir) {
        this.preprocessedSourcesDir = preprocessedSourcesDir;
    }

    @Inject
    public PreprocessNativeWithDepsFile(WorkerExecutor workerExecutor) {
        super(workerExecutor);
    }

    @TaskAction
    public void preprocess(IncrementalTaskInputs incrementalTaskInputs) {
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
                String extension = name.endsWith(".cpp") ? "cpp" : "c";
                String base = name.replaceAll("\\.c(pp)?", "");
                String preprocessedName = base + (extension.equals("cpp") ? ".ii" : ".i");
                String depName = base + ".d";
                final File preprocessedFile = fileVisitDetails.getRelativePath().getParent().append(true, preprocessedName).getFile(getPreprocessedSourcesDir());
                final File depFile = fileVisitDetails.getRelativePath().getParent().append(true, depName).getFile(discoveredDependenciesDir);
                preprocessedFile.getParentFile().mkdirs();
                depFile.getParentFile().mkdirs();
                runGxx("-E", "-MD",
                    "-MF", depFile.getAbsolutePath(),
                    "-o", preprocessedFile.getAbsolutePath(),
                    fileVisitDetails.getFile().getAbsolutePath());
                depFiles.add(depFile);
            }
        });
        getWorkerExecutor().await();
        for (File depFile : depFiles) {
            DiscoverInputsFromDepFiles.addDiscoveredInputs(inputs, depFile, seenIncludes, discoveredIncludeFiles);
        }
    }

}
