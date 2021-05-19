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

import org.gradle.api.Plugin;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.plugins.GroovyBasePlugin;
import org.gradle.api.plugins.quality.internal.AbstractCodeQualityPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.GroovySourceDirectorySet;
import org.gradle.api.tasks.SourceSet;

import java.io.File;

import static org.gradle.api.internal.lambdas.SerializableLambdas.action;

/**
 * CodeNarc Plugin.
 *
 * @see <a href="https://docs.gradle.org/current/userguide/codenarc_plugin.html">CodeNarc plugin reference</a>
 */
public class CodeNarcPlugin extends AbstractCodeQualityPlugin<CodeNarc> {

    public static final String DEFAULT_CODENARC_VERSION = "2.0.0";
    private CodeNarcExtension extension;

    @Override
    protected String getToolName() {
        return "CodeNarc";
    }

    @Override
    protected Class<CodeNarc> getTaskType() {
        return CodeNarc.class;
    }

    @Override
    protected Class<? extends Plugin> getBasePlugin() {
        return GroovyBasePlugin.class;
    }

    @Override
    protected CodeQualityExtension createExtension() {
        extension = project.getExtensions().create("codenarc", CodeNarcExtension.class, project);
        extension.setToolVersion(DEFAULT_CODENARC_VERSION);
        extension.setConfig(project.getResources().getText().fromFile(project.getRootProject().file("config/codenarc/codenarc.xml")));
        extension.setMaxPriority1Violations(0);
        extension.setMaxPriority2Violations(0);
        extension.setMaxPriority3Violations(0);
        extension.setReportFormat("html");
        return extension;
    }

    @Override
    protected void configureConfiguration(Configuration configuration) {
        configureDefaultDependencies(configuration);
    }

    @Override
    protected void configureTaskDefaults(CodeNarc task, String baseName) {
        Configuration configuration = project.getConfigurations().getAt(getConfigurationName());
        configureTaskConventionMapping(configuration, task);
        configureReportsConventionMapping(task, baseName);
    }

    private void configureDefaultDependencies(Configuration configuration) {
        configuration.defaultDependencies(dependencies ->
            dependencies.add(project.getDependencies().create("org.codenarc:CodeNarc:" + extension.getToolVersion()))
        );
    }

    private void configureTaskConventionMapping(Configuration configuration, CodeNarc task) {
        ConventionMapping taskMapping = task.getConventionMapping();
        taskMapping.map("codenarcClasspath", () -> configuration);
        taskMapping.map("config", () -> extension.getConfig());
        taskMapping.map("maxPriority1Violations", () -> extension.getMaxPriority1Violations());
        taskMapping.map("maxPriority2Violations", () -> extension.getMaxPriority2Violations());
        taskMapping.map("maxPriority3Violations", () -> extension.getMaxPriority3Violations());
        taskMapping.map("ignoreFailures", () -> extension.isIgnoreFailures());
    }

    private void configureReportsConventionMapping(CodeNarc task, final String baseName) {
        ProjectLayout layout = project.getLayout();
        ProviderFactory providers = project.getProviders();
        Provider<String> reportFormat = providers.provider(() -> extension.getReportFormat());
        Provider<RegularFile> reportsDir = layout.file(providers.provider(() -> extension.getReportsDir()));
        task.getReports().all(action(report -> {
            report.getRequired().convention(providers.provider(() -> report.getName().equals(reportFormat.get())));
            report.getOutputLocation().convention(layout.getProjectDirectory().file(providers.provider(() -> {
                String fileSuffix = report.getName().equals("text") ? "txt" : report.getName();
                return new File(reportsDir.get().getAsFile(), baseName + "." + fileSuffix).getAbsolutePath();
            })));
        }));
    }

    @Override
    protected void configureForSourceSet(final SourceSet sourceSet, CodeNarc task) {
        task.setDescription("Run CodeNarc analysis for " + sourceSet.getName() + " classes");
        SourceDirectorySet groovySourceSet =  sourceSet.getExtensions().getByType(GroovySourceDirectorySet.class);
        task.setSource(groovySourceSet.matching(filter -> filter.include("**/*.groovy")));
    }
}
