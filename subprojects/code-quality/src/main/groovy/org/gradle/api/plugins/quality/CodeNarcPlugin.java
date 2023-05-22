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

import groovy.lang.GroovySystem;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.plugins.GroovyBasePlugin;
import org.gradle.api.plugins.quality.internal.AbstractCodeQualityPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.GroovySourceDirectorySet;
import org.gradle.api.tasks.SourceSet;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.util.internal.VersionNumber;

import java.io.File;
import java.util.Collections;
import java.util.Set;

/**
 * CodeNarc Plugin.
 *
 * @see <a href="https://docs.gradle.org/current/userguide/codenarc_plugin.html">CodeNarc plugin reference</a>
 */
public abstract class CodeNarcPlugin extends AbstractCodeQualityPlugin<CodeNarc> {

    public static final String DEFAULT_CODENARC_VERSION = appropriateCodeNarcVersion();
    static final String STABLE_VERSION = "3.2.0";
    static final String STABLE_VERSION_WITH_GROOVY4_SUPPORT = "3.2.0-groovy-4.0";

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
    protected CodeQualityExtension createExtension(Project project) {
        project.getPlugins().apply(GroovyBasePlugin.class);

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
    protected void configureTaskDefaults(CodeNarc task, String baseName, FileCollection toolClasspath, Provider<JavaLauncher> toolchain) {
        configureTaskConventionMapping(toolClasspath, task);
        configureReportsConventionMapping(task, baseName);
        task.getJavaLauncher().convention(toolchain);
    }

    @Override
    protected Set<Dependency> getToolDependencies() {
        return Collections.singleton(getDependencyFactory().create("org.codenarc:CodeNarc:" + extension.getToolVersion()));
    }

    private void configureTaskConventionMapping(FileCollection toolClasspath, CodeNarc task) {
        ConventionMapping taskMapping = task.getConventionMapping();
        taskMapping.map("codenarcClasspath", () -> toolClasspath);
        taskMapping.map("config", () -> extension.getConfig());
        taskMapping.map("maxPriority1Violations", () -> extension.getMaxPriority1Violations());
        taskMapping.map("maxPriority2Violations", () -> extension.getMaxPriority2Violations());
        taskMapping.map("maxPriority3Violations", () -> extension.getMaxPriority3Violations());
        taskMapping.map("ignoreFailures", () -> extension.isIgnoreFailures());
    }

    private void configureReportsConventionMapping(CodeNarc task, final String baseName) {
        Provider<String> reportFormat = getProviders().provider(() -> extension.getReportFormat());
        Provider<RegularFile> reportsDir = getProjectLayout().file(getProviders().provider(() -> extension.getReportsDir()));
        task.getReports().all(report -> {
            report.getRequired().convention(getProviders().provider(() -> report.getName().equals(reportFormat.get())));
            report.getOutputLocation().convention(getProjectLayout().getProjectDirectory().file(getProviders().provider(() -> {
                String fileSuffix = report.getName().equals("text") ? "txt" : report.getName();
                return new File(reportsDir.get().getAsFile(), baseName + "." + fileSuffix).getAbsolutePath();
            })));
        });
    }

    @Override
    protected void configureForSourceSet(final SourceSet sourceSet, CodeNarc task) {
        task.setDescription("Run CodeNarc analysis for " + sourceSet.getName() + " sources");
        SourceDirectorySet groovySourceSet =  sourceSet.getExtensions().getByType(GroovySourceDirectorySet.class);
        task.setSource(groovySourceSet.matching(filter -> filter.include("**/*.groovy")));
    }

    private static String appropriateCodeNarcVersion() {
        int groovyMajorVersion = VersionNumber.parse(GroovySystem.getVersion()).getMajor();
        return groovyMajorVersion < 4 ? STABLE_VERSION : STABLE_VERSION_WITH_GROOVY4_SUPPORT;
    }
}
