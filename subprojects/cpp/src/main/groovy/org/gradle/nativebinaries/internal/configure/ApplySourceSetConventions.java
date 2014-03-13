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
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.language.HeaderExportingSourceSet;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.ProjectSourceSet;

/**
 * Applies source set conventions when no source directories are explicitly configured.
 */
public class ApplySourceSetConventions implements Action<ProjectInternal> {
    public void execute(ProjectInternal project) {
        ProjectSourceSet projectSourceSet = project.getExtensions().getByType(ProjectSourceSet.class);
        for (FunctionalSourceSet functionalSourceSet : projectSourceSet) {
            for (LanguageSourceSet languageSourceSet : functionalSourceSet) {
                // Only apply default locations when none explicitly configured
                if (languageSourceSet.getSource().getSrcDirs().isEmpty()) {
                    languageSourceSet.getSource().srcDir(String.format("src/%s/%s", functionalSourceSet.getName(), languageSourceSet.getName()));
                }
            }
            for (HeaderExportingSourceSet headerSourceSet : functionalSourceSet.withType(HeaderExportingSourceSet.class)) {
                // Only apply default locations when none explicitly configured
                if (headerSourceSet.getExportedHeaders().getSrcDirs().isEmpty()) {
                    headerSourceSet.getExportedHeaders().srcDir(String.format("src/%s/headers", functionalSourceSet.getName()));
                }

                headerSourceSet.getImplicitHeaders().setSrcDirs(headerSourceSet.getSource().getSrcDirs());
                headerSourceSet.getImplicitHeaders().include("**/*.h");
            }
        }
    }
}
