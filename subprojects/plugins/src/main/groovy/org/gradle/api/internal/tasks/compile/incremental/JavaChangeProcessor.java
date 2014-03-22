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

import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependencyInfo;
import org.gradle.api.internal.tasks.compile.incremental.deps.DependentsSet;
import org.gradle.api.tasks.incremental.InputFileDetails;

public class JavaChangeProcessor {

    private final ClassDependencyInfo dependencyInfo;
    private final SourceToNameConverter sourceToNameConverter;

    public JavaChangeProcessor(ClassDependencyInfo dependencyInfo, SourceToNameConverter sourceToNameConverter) {
        this.dependencyInfo = dependencyInfo;
        this.sourceToNameConverter = sourceToNameConverter;
    }

    public void processChange(InputFileDetails input, DefaultRecompilationSpec spec) {
        String className = sourceToNameConverter.getClassName(input.getFile());
        spec.classesToCompile.add(className);
        DependentsSet actualDependents = dependencyInfo.getRelevantDependents(className);
        if (actualDependents.isDependencyToAll()) {
            spec.fullRebuildCause = input.getFile();
            return;
        }
        spec.classesToCompile.addAll(actualDependents.getDependentClasses());
    }
}