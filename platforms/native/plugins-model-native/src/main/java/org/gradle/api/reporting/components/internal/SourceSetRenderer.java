/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.reporting.components.internal;

import org.apache.commons.lang3.StringUtils;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder;
import org.gradle.reporting.ReportRenderer;

import java.io.File;
import java.util.Comparator;
import java.util.Set;

@SuppressWarnings("deprecation")
class SourceSetRenderer extends ReportRenderer<org.gradle.language.base.LanguageSourceSet, TextReportBuilder> {
    static final Comparator<org.gradle.language.base.LanguageSourceSet> SORT_ORDER = (o1, o2) -> o1.getDisplayName().compareToIgnoreCase(o2.getDisplayName());

    @Override
    public void render(org.gradle.language.base.LanguageSourceSet sourceSet, TextReportBuilder builder) {
        builder.heading(StringUtils.capitalize(sourceSet.getDisplayName()));
        renderSourceSetDirectories(sourceSet, builder);
        renderSourceSetDependencies(sourceSet, builder);
    }

    private void renderSourceSetDirectories(org.gradle.language.base.LanguageSourceSet sourceSet, TextReportBuilder builder) {
        Set<File> srcDirs = sourceSet.getSource().getSrcDirs();
        if (srcDirs.isEmpty()) {
            builder.item("No source directories");
        } else {
            for (File file : srcDirs) {
                builder.item("srcDir", file);
            }
            SourceDirectorySet source = sourceSet.getSource();
            Set<String> includes = source.getIncludes();
            if (!includes.isEmpty()) {
                builder.item("includes", includes);
            }
            Set<String> excludes = source.getExcludes();
            if (!excludes.isEmpty()) {
                builder.item("excludes", excludes);
            }
            Set<String> filterIncludes = source.getFilter().getIncludes();
            if (!filterIncludes.isEmpty()) {
                builder.item("limit to", filterIncludes);
            }
        }
    }

    private void renderSourceSetDependencies(org.gradle.language.base.LanguageSourceSet sourceSet, TextReportBuilder builder) {
        if (sourceSet instanceof org.gradle.language.base.DependentSourceSet) {
            org.gradle.platform.base.DependencySpecContainer dependencies = ((org.gradle.language.base.DependentSourceSet) sourceSet).getDependencies();
            if (!dependencies.isEmpty()) {
                builder.collection("dependencies", dependencies.getDependencies(), new ReportRenderer<org.gradle.platform.base.DependencySpec, TextReportBuilder>() {
                    @Override
                    public void render(org.gradle.platform.base.DependencySpec model, TextReportBuilder output) {
                        if (model instanceof org.gradle.platform.base.ProjectDependencySpec) {
                            output.item(model.getDisplayName());
                        }
                    }
                }, "dependencies");
            }
        }
    }
}
