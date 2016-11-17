/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.quality;

import com.google.common.util.concurrent.Callables;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.plugins.quality.internal.AbstractCodeQualityPlugin;
import org.gradle.api.reporting.SingleFileReport;
import org.gradle.api.resources.TextResource;
import org.gradle.api.tasks.SourceSet;

import java.io.File;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Checkstyle Plugin.
 */
public class CheckstylePlugin extends AbstractCodeQualityPlugin<Checkstyle> {

    public static final String DEFAULT_CHECKSTYLE_VERSION = "5.9";
    private CheckstyleExtension extension;

    @Override
    protected String getToolName() {
        return "Checkstyle";
    }

    @Override
    protected Class<Checkstyle> getTaskType() {
        return Checkstyle.class;
    }

    @Override
    protected CodeQualityExtension createExtension() {
        extension = project.getExtensions().create("checkstyle", CheckstyleExtension.class, project);
        extension.setToolVersion(DEFAULT_CHECKSTYLE_VERSION);
        extension.setConfig(project.getResources().getText().fromFile("config/checkstyle/checkstyle.xml"));
        return extension;
    }

    @Override
    protected void configureTaskDefaults(Checkstyle task, final String baseName) {
        Configuration configuration = project.getConfigurations().getAt("checkstyle");
        configureDefaultDependencies(configuration);
        configureTaskConventionMapping(configuration, task);
        configureReportsConventionMapping(task, baseName);
    }

    private void configureDefaultDependencies(Configuration configuration) {
        configuration.defaultDependencies(new Action<DependencySet>() {
            @Override
            public void execute(DependencySet dependencies) {
                dependencies.add(project.getDependencies().create("com.puppycrawl.tools:checkstyle:" + extension.getToolVersion()));
            }
        });
    }

    private void configureTaskConventionMapping(Configuration configuration, Checkstyle task) {
        ConventionMapping taskMapping = task.getConventionMapping();
        taskMapping.map("checkstyleClasspath", Callables.returning(configuration));
        taskMapping.map("config", new Callable<TextResource>() {
            @Override
            public TextResource call() {
                return extension.getConfig();
            }
        });
        taskMapping.map("configProperties", new Callable<Map<String, Object>>() {
            @Override
            public Map<String, Object> call() {
                return extension.getConfigProperties();
            }
        });
        taskMapping.map("ignoreFailures", new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return extension.isIgnoreFailures();
            }
        });
        taskMapping.map("showViolations", new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return extension.isShowViolations();
            }
        });
    }

    private void configureReportsConventionMapping(Checkstyle task, final String baseName) {
        task.getReports().all(new Action<SingleFileReport>() {
            @Override
            public void execute(final SingleFileReport report) {
                ConventionMapping reportMapping = conventionMappingOf(report);
                reportMapping.map("enabled", Callables.returning(true));
                reportMapping.map("destination", new Callable<File>() {
                    @Override
                    public File call() {
                        return new File(extension.getReportsDir(), baseName + "." + report.getName());
                    }
                });
            }
        });
    }

    @Override
    protected void configureForSourceSet(final SourceSet sourceSet, Checkstyle task) {
        task.setDescription("Run Checkstyle analysis for " + sourceSet.getName() + " classes");
        task.setClasspath(sourceSet.getOutput().plus(sourceSet.getCompileClasspath()));
        task.setSource(sourceSet.getAllJava());
    }
}
