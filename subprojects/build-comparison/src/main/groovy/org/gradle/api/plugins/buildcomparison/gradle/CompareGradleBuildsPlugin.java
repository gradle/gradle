/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.buildcomparison.gradle;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.plugins.ReportingBasePlugin;
import org.gradle.api.reporting.ReportingExtension;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * Preconfigures the project to run a gradle build comparison.
 */
@Incubating
public class CompareGradleBuildsPlugin implements Plugin<Project> {
    public void apply(Project project) {
        project.getPluginManager().apply(ReportingBasePlugin.class);
        final ReportingExtension reportingExtension = project.getExtensions().findByType(ReportingExtension.class);

        project.getTasks().withType(CompareGradleBuilds.class, new Action<CompareGradleBuilds>() {
            @Override
            public void execute(final CompareGradleBuilds task) {
                ((IConventionAware) task).getConventionMapping().map("reportDir", new Callable<File>() {
                    @Override
                    public File call() throws Exception {
                        return reportingExtension.file(task.getName());
                    }
                });
            }
        });

        project.getTasks().create("compareGradleBuilds", CompareGradleBuilds.class);
    }
}
