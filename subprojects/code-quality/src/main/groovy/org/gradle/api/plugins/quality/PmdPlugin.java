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
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.plugins.jvm.internal.JvmPluginServices;
import org.gradle.api.plugins.quality.internal.AbstractCodeQualityPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.util.internal.VersionNumber;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

import static org.gradle.api.internal.lambdas.SerializableLambdas.action;

/**
 * A plugin for the <a href="https://pmd.github.io/">PMD</a> source code analyzer.
 * <p>
 * Declares a <code>pmd</code> configuration which needs to be configured with the PMD library to be used.
 * <p>
 * Declares a <code>pmdAux</code> configuration to add transitive compileOnly dependencies to the PMD's auxclasspath. This is only needed if PMD complains about NoClassDefFoundError during type
 * resolution.
 * <p>
 * For each source set that is to be analyzed, a {@link Pmd} task is created and configured to analyze all Java code.
 * <p>
 * All PMD tasks (including user-defined ones) are added to the <code>check</code> lifecycle task.
 *
 * @see PmdExtension
 * @see Pmd
 * @see <a href="https://docs.gradle.org/current/userguide/pmd_plugin.html">PMD plugin reference</a>
 */
public class PmdPlugin extends AbstractCodeQualityPlugin<Pmd> {

    public static final String DEFAULT_PMD_VERSION = "6.36.0";
    private static final String PMD_ADDITIONAL_AUX_DEPS_CONFIGURATION = "pmdAux";

    private PmdExtension extension;

    @Inject
    protected JvmPluginServices getJvmPluginServices() {
        // Constructor injection is not used to keep binary compatibility
        throw new UnsupportedOperationException();
    }

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
        extension.setRuleSets(new ArrayList<>(Collections.singletonList("category/java/errorprone.xml")));
        extension.setRuleSetFiles(project.getLayout().files());
        conventionMappingOf(extension).map("targetJdk", () ->
            getDefaultTargetJdk(getJavaPluginExtension().getSourceCompatibility()));
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
    protected void createConfigurations() {
        super.createConfigurations();
        project.getConfigurations().create(PMD_ADDITIONAL_AUX_DEPS_CONFIGURATION, additionalAuxDepsConfiguration -> {
            additionalAuxDepsConfiguration.setDescription("The additional libraries that are available for type resolution during analysis");
            additionalAuxDepsConfiguration.setCanBeResolved(false);
            additionalAuxDepsConfiguration.setCanBeConsumed(false);
            additionalAuxDepsConfiguration.setVisible(false);
        });
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
        taskMapping.map("ruleSets", () -> extension.getRuleSets());
        taskMapping.map("ruleSetConfig", () -> extension.getRuleSetConfig());
        taskMapping.map("ruleSetFiles", () -> extension.getRuleSetFiles());
        taskMapping.map("ignoreFailures", () -> extension.isIgnoreFailures());
        taskMapping.map("consoleOutput", () -> extension.isConsoleOutput());
        taskMapping.map("targetJdk", () -> extension.getTargetJdk());

        task.getRulesMinimumPriority().convention(extension.getRulesMinimumPriority());
        task.getMaxFailures().convention(extension.getMaxFailures());
        task.getIncrementalAnalysis().convention(extension.getIncrementalAnalysis());
    }

    private void configureReportsConventionMapping(Pmd task, final String baseName) {
        ProjectLayout layout = project.getLayout();
        ProviderFactory providers = project.getProviders();
        Provider<RegularFile> reportsDir = layout.file(providers.provider(() -> extension.getReportsDir()));
        task.getReports().all(action(report -> {
            report.getRequired().convention(true);
            report.getOutputLocation().convention(
                layout.getProjectDirectory().file(providers.provider(() -> {
                    String reportFileName = baseName + "." + report.getName();
                    return new File(reportsDir.get().getAsFile(), reportFileName).getAbsolutePath();
                }))
            );
        }));
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
        ConfigurationContainer configurations = project.getConfigurations();

        Configuration compileClasspath = configurations.getByName(sourceSet.getCompileClasspathConfigurationName());
        Configuration pmdAdditionalAuxDepsConfiguration = configurations.getByName(PMD_ADDITIONAL_AUX_DEPS_CONFIGURATION);

        // TODO: Consider checking if the resolution consistency is enabled for compile/runtime.
        Configuration pmdAuxClasspath = configurations.create(sourceSet.getName() + "PmdAuxClasspath");
        pmdAuxClasspath.extendsFrom(compileClasspath, pmdAdditionalAuxDepsConfiguration);
        pmdAuxClasspath.setCanBeConsumed(false);
        pmdAuxClasspath.setVisible(false);
        // This is important to get transitive implementation dependencies. PMD may load referenced classes for analysis so it expects the classpath to be "closed" world.
        getJvmPluginServices().configureAsRuntimeClasspath(pmdAuxClasspath);

        // We have to explicitly add compileClasspath here because it may contain classes that aren't part of the compileClasspathConfiguration. In particular, compile
        // classpath of the test sourceSet contains output of the main sourceSet.
        taskMapping.map("classpath", () -> {
            // It is important to subtract compileClasspath and not pmdAuxClasspath here because these configurations are resolved differently (as a compile and as a
            // runtime classpath). Compile and runtime entries for the same dependency may resolve to different files (e.g. compiled classes directory vs. jar).
            FileCollection nonConfigurationClasspathEntries = sourceSet.getCompileClasspath().minus(compileClasspath);
            return sourceSet.getOutput().plus(nonConfigurationClasspathEntries).plus(pmdAuxClasspath);
        });
    }
}
