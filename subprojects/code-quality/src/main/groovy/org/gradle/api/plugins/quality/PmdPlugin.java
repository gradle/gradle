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

import org.gradle.api.JavaVersion;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.plugins.quality.internal.AbstractCodeQualityPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.util.VersionNumber;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * A plugin for the <a href="https://pmd.github.io/">PMD</a> source code analyzer.
 * <p>
 * Declares a <tt>pmd</tt> configuration which needs to be configured with the PMD library to be used.
 * <p>
 * For each source set that is to be analyzed, a {@link Pmd} task is created and configured to analyze all Java code.
 * <p>
 * All PMD tasks (including user-defined ones) are added to the <tt>check</tt> lifecycle task.
 *
 * @see PmdExtension
 * @see Pmd
 */
public class PmdPlugin extends AbstractCodeQualityPlugin<Pmd> {

    public static final String DEFAULT_PMD_VERSION = "6.26.0";
    private PmdExtension extension;

    @Override
    protected String getToolName() {
        return "PMD";
    }

    @Override
    protected Class<Pmd> getTaskType() {
        return Pmd.class;
    }

    @Override
    protected CodeQualityExtension createExtension() {
        extension = project.getExtensions().create("pmd", PmdExtension.class, project);
        extension.setToolVersion(DEFAULT_PMD_VERSION);
        extension.setRuleSets(new ArrayList<String>(Arrays.asList("category/java/errorprone.xml")));
        extension.setRuleSetFiles(project.getLayout().files());
        conventionMappingOf(extension).map("targetJdk", () ->
            getDefaultTargetJdk(getJavaPluginConvention().getSourceCompatibility()));
        return extension;
    }

    public TargetJdk getDefaultTargetJdk(JavaVersion javaVersion) {
        try {
            return TargetJdk.toVersion(javaVersion.toString());
        } catch (IllegalArgumentException ignored) {
            // TargetJDK does not include 1.1, 1.2 and 1.8;
            // Use same fallback as PMD
            return TargetJdk.VERSION_1_4;
        }
    }

    @Override
    protected void configureConfiguration(Configuration configuration) {
        configureDefaultDependencies(configuration);
    }

    @Override
    protected void configureTaskDefaults(Pmd task, String baseName) {
        Configuration configuration = project.getConfigurations().getAt(getConfigurationName());
        configureTaskConventionMapping(configuration, task);
        configureReportsConventionMapping(task, baseName);
    }

    private void configureDefaultDependencies(Configuration configuration) {
        configuration.defaultDependencies(dependencies -> {
                VersionNumber version = VersionNumber.parse(extension.getToolVersion());
                String dependency = calculateDefaultDependencyNotation(version);
                dependencies.add(project.getDependencies().create(dependency));
            }
        );
    }

    private void configureTaskConventionMapping(Configuration configuration, final Pmd task) {
        ConventionMapping taskMapping = task.getConventionMapping();
        taskMapping.map("pmdClasspath", () -> configuration);
        taskMapping.map("ruleSets", () ->  extension.getRuleSets());
        taskMapping.map("ruleSetConfig", () ->  extension.getRuleSetConfig());
        taskMapping.map("ruleSetFiles", () ->  extension.getRuleSetFiles());
        taskMapping.map("ignoreFailures", () -> extension.isIgnoreFailures());
        taskMapping.map("rulePriority", () ->  extension.getRulePriority());
        taskMapping.map("consoleOutput", () ->  extension.isConsoleOutput());
        taskMapping.map("targetJdk", () ->  extension.getTargetJdk());

        task.getMaxFailures().convention(extension.getMaxFailures());
        task.getIncrementalAnalysis().convention(extension.getIncrementalAnalysis());
    }

    private void configureReportsConventionMapping(Pmd task, final String baseName) {
        task.getReports().all(report -> {
            report.getRequired().convention(true);
            report.getOutputLocation().convention(project.getLayout().getProjectDirectory().file(project.provider(() -> new File(extension.getReportsDir(), baseName + "." + report.getName()).getAbsolutePath())));
        });
    }

    private String calculateDefaultDependencyNotation(VersionNumber toolVersion) {
        if (toolVersion.compareTo(VersionNumber.version(5)) < 0) {
            return "pmd:pmd:" + extension.getToolVersion();
        } else if (toolVersion.compareTo(VersionNumber.parse("5.2.0")) < 0) {
            return "net.sourceforge.pmd:pmd:" + extension.getToolVersion();
        }
        return "net.sourceforge.pmd:pmd-java:" + extension.getToolVersion();
    }

    @Override
    protected void configureForSourceSet(final SourceSet sourceSet, final Pmd task) {
        task.setDescription("Run PMD analysis for " + sourceSet.getName() + " classes");
        task.setSource(sourceSet.getAllJava());
        ConventionMapping taskMapping = task.getConventionMapping();
        taskMapping.map("classpath", () ->
            sourceSet.getOutput().plus(sourceSet.getCompileClasspath()));
    }
}
