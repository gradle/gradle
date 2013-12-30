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
package org.gradle.nativebinaries.internal.configure;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.language.HeaderExportingSourceSet;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetInternal;

/**
 * Wires generator tasks into language source sets.
 */
public class ConfigureGeneratedSourceSets implements Action<ProjectInternal> {
    public void execute(ProjectInternal project) {
        ProjectSourceSet projectSourceSet = project.getExtensions().getByType(ProjectSourceSet.class);
        for (FunctionalSourceSet functionalSourceSet : projectSourceSet) {
            for (LanguageSourceSetInternal languageSourceSet : functionalSourceSet.withType(LanguageSourceSetInternal.class)) {
                Task generatorTask = languageSourceSet.getGeneratorTask();
                if (generatorTask != null) {
                    languageSourceSet.builtBy(generatorTask);
                    maybeSetSourceDir(languageSourceSet.getSource(), generatorTask, "sourceDir");
                    if (languageSourceSet instanceof HeaderExportingSourceSet) {
                        maybeSetSourceDir(((HeaderExportingSourceSet) languageSourceSet).getExportedHeaders(), generatorTask, "headerDir");
                    }
                }
            }
        }
    }

    private void maybeSetSourceDir(SourceDirectorySet sourceSet, Task task, String propertyName) {
        // TODO:DAZ Handle multiple output directories
        Object value = task.property(propertyName);
        if (value != null) {
            sourceSet.srcDir(value);
        }
    }
}
