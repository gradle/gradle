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
import org.apache.tools.ant.Main;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.plugins.quality.internal.AbstractCodeQualityPlugin;
import org.gradle.api.reporting.SingleFileReport;
import org.gradle.api.tasks.SourceSet;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * A {@link org.gradle.api.Plugin} that generates design quality metrics by scanning your source packages.
 *
 * This is done using the JDepend tool. This plugin will automatically generate a task for each Java source set.
 *
 * See <a href="http://www.clarkware.com/software/JDepend.html">JDepend</a> for more information.
 *
 * @see JDependExtension
 * @see JDepend
 */
public class JDependPlugin extends AbstractCodeQualityPlugin<JDepend> {

    public static final String DEFAULT_JDEPEND_VERSION = "2.9.1";
    private JDependExtension extension;

    @Override
    protected String getToolName() {
        return "JDepend";
    }

    @Override
    protected Class<JDepend> getTaskType() {
        return JDepend.class;
    }

    @Override
    protected CodeQualityExtension createExtension() {
        extension = project.getExtensions().create("jdepend", JDependExtension.class);
        extension.setToolVersion(DEFAULT_JDEPEND_VERSION);
        return extension;
    }

    @Override
    protected void configureTaskDefaults(JDepend task, String baseName) {
        Configuration configuration = project.getConfigurations().getAt("jdepend");
        configureDefaultDependencies(configuration);
        configureTaskConventionMapping(configuration, task);
        configureReportsConventionMapping(task, baseName);
    }

    private void configureDefaultDependencies(Configuration configuration) {
        configuration.defaultDependencies(new Action<DependencySet>() {
            @Override
            public void execute(DependencySet dependencies) {
                dependencies.add(project.getDependencies().create("jdepend:jdepend:" + extension.getToolVersion()));
                Class<Main> antMain = Main.class;
                String antVersion = antMain.getPackage().getImplementationVersion();
                dependencies.add(project.getDependencies().create("org.apache.ant:ant-jdepend:" + antVersion));
            }
        });
    }

    private void configureTaskConventionMapping(Configuration configuration, JDepend task) {
        conventionMappingOf(task).map("jdependClasspath", Callables.returning(configuration));
    }

    private void configureReportsConventionMapping(JDepend task, final String baseName) {
        task.getReports().all(new Action<SingleFileReport>() {
            @Override
            public void execute(final SingleFileReport report) {
                ConventionMapping reportMapping = conventionMappingOf(report);
                reportMapping.map("enabled", new Callable<Boolean>() {
                    @Override
                    public Boolean call() {
                        return report.getName().equals("xml");
                    }
                });
                reportMapping.map("destination", new Callable<File>() {
                    @Override
                    public File call() {
                        final String fileSuffix = report.getName().equals("text") ? "txt" : report.getName();
                        return new File(extension.getReportsDir(), baseName + "." + fileSuffix);
                    }
                });
            }
        });
    }

    @Override
    protected void configureForSourceSet(final SourceSet sourceSet, JDepend task) {
        task.dependsOn(sourceSet.getOutput());
        task.setDescription("Run JDepend analysis for " + sourceSet.getName() + " classes");
        task.setClassesDirs(sourceSet.getOutput().getClassesDirs());
    }
}
