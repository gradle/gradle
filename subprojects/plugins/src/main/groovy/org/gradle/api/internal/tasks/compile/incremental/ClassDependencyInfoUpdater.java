/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental;

import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependencyInfo;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependencyInfoExtractor;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependencyInfoWriter;
import org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationNotNecessary;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.util.Clock;

public class ClassDependencyInfoUpdater {

    private final static Logger LOG = Logging.getLogger(ClassDependencyInfoUpdater.class);

    private final ClassDependencyInfoWriter writer;
    private final FileOperations fileOperations;
    private final ClassDependencyInfoExtractor extractor;

    public ClassDependencyInfoUpdater(ClassDependencyInfoWriter writer, FileOperations fileOperations, ClassDependencyInfoExtractor extractor) {
        this.writer = writer;
        this.fileOperations = fileOperations;
        this.extractor = extractor;
    }

    public void updateInfo(JavaCompileSpec spec, WorkResult compilationResult) {
        if (compilationResult instanceof RecompilationNotNecessary) {
            //performance tweak, update not necessary
            return;
        }

        Clock clock = new Clock();
        FileTree tree = fileOperations.fileTree(spec.getDestinationDir());
        tree.visit(extractor);
        ClassDependencyInfo info = extractor.getDependencyInfo();
        writer.writeInfo(info);
        LOG.lifecycle("Performed class dependency analysis in {}, wrote results into {}", clock.getTime(), writer);
    }
}
