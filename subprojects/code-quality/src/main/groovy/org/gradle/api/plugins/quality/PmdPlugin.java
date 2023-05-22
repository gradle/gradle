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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.quality.internal.AbstractCodeQualityPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.util.internal.VersionNumber;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
public abstract class PmdPlugin extends AbstractCodeQualityPlugin<Pmd> {

    // When updating DEFAULT_PMD_VERSION, also update links in Pmd and PmdExtension!
    public static final String DEFAULT_PMD_VERSION = "6.55.0";

    private static final String PMD_ADDITIONAL_AUX_DEPS_CONFIGURATION = "pmdAux";

    private Project project;
    private PmdExtension extension;
    private Configuration additionalAuxClasspath;

    @Override
    protected String getToolName() {
        return "PMD";
    }

    @Override
    protected Class<Pmd> getTaskType() {
        return Pmd.class;
    }

    @Override
    protected CodeQualityExtension createExtension(Project project) {
        this.project = project;

        RoleBasedConfigurationContainerInternal configurations = (RoleBasedConfigurationContainerInternal) project.getConfigurations();
        additionalAuxClasspath = configurations.bucket(PMD_ADDITIONAL_AUX_DEPS_CONFIGURATION);

        getJvmPluginServices().configureAsRuntimeClasspath(additionalAuxClasspath);
        additionalAuxClasspath.setDescription("The additional libraries that are available for type resolution during analysis");
        additionalAuxClasspath.setVisible(false);

        extension = project.getExtensions().create("pmd", PmdExtension.class, project);
        extension.setToolVersion(DEFAULT_PMD_VERSION);
        extension.getRulesMinimumPriority().convention(5);
        extension.getIncrementalAnalysis().convention(true);
        extension.getMaxFailures().convention(0);
        extension.getThreads().convention(1);
        extension.setRuleSetFiles(project.getLayout().files());
        extension.ruleSetsConvention(getProviders().provider(() -> ruleSetsConvention(extension)));
        new DslObject(extension).getConventionMapping().map("targetJdk", () ->
            getDefaultTargetJdkInternal(project.getExtensions().getByType(JavaPluginExtension.class).getSourceCompatibility()));

        return extension;
    }

    /**
     * This method is deprecated.
     *
     * @deprecated This was only intended to be used internally. This method will be removed in Gradle 9.0.
     */
    @Deprecated
    public TargetJdk getDefaultTargetJdk(JavaVersion javaVersion) {
        DeprecationLogger.deprecateMethod(PmdPlugin.class, "getDefaultTargetJdk(JavaVersion)")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecate_get_default_target_jdk_pmd")
            .nagUser();

        return getDefaultTargetJdkInternal(javaVersion);
    }

    private TargetJdk getDefaultTargetJdkInternal(JavaVersion javaVersion) {
        try {
            return TargetJdk.toVersion(javaVersion.toString());
        } catch (IllegalArgumentException ignored) {
            // TargetJDK does not include 1.1, 1.2 and 1.8;
            // Use same fallback as PMD
            return TargetJdk.VERSION_1_4;
        }
    }

    @Override
    protected void configureTaskDefaults(Pmd task, String baseName, FileCollection toolClasspath, Provider<JavaLauncher> toolchain) {
        configureTaskConventionMapping(toolClasspath, task);
        configureReportsConventionMapping(task, baseName);
        task.getJavaLauncher().convention(toolchain);
    }

    private static List<String> ruleSetsConvention(PmdExtension extension) {
        if (extension.getRuleSetConfig() == null && extension.getRuleSetFiles().isEmpty()) {
            return new ArrayList<>(Collections.singletonList("category/java/errorprone.xml"));
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    protected Set<Dependency> getToolDependencies() {
        return calculateDefaultDependencyNotation(extension.getToolVersion())
            .stream()
            .map(getDependencyFactory()::create)
            .collect(Collectors.toSet());
    }

    private void configureTaskConventionMapping(FileCollection toolClasspath, final Pmd task) {
        ConventionMapping taskMapping = task.getConventionMapping();
        taskMapping.map("pmdClasspath", () -> toolClasspath);
        taskMapping.map("ruleSets", () -> extension.getRuleSets());
        taskMapping.map("ruleSetConfig", () -> extension.getRuleSetConfig());
        taskMapping.map("ruleSetFiles", () -> extension.getRuleSetFiles());
        taskMapping.map("ignoreFailures", () -> extension.isIgnoreFailures());
        taskMapping.map("consoleOutput", () -> extension.isConsoleOutput());
        taskMapping.map("targetJdk", () -> extension.getTargetJdk());

        task.getRulesMinimumPriority().convention(extension.getRulesMinimumPriority());
        task.getMaxFailures().convention(extension.getMaxFailures());
        task.getIncrementalAnalysis().convention(extension.getIncrementalAnalysis());
        task.getThreads().convention(extension.getThreads());
    }

    private void configureReportsConventionMapping(Pmd task, final String baseName) {
        Provider<RegularFile> reportsDir = getProjectLayout().file(getProviders().provider(() -> extension.getReportsDir()));
        task.getReports().all(report -> {
            report.getRequired().convention(true);
            report.getOutputLocation().convention(
                getProjectLayout().getProjectDirectory().file(getProviders().provider(() -> {
                    String reportFileName = baseName + "." + report.getName();
                    return new File(reportsDir.get().getAsFile(), reportFileName).getAbsolutePath();
                }))
            );
        });
    }

    @VisibleForTesting
    static Set<String> calculateDefaultDependencyNotation(final String versionString) {
        final VersionNumber toolVersion = VersionNumber.parse(versionString);
        if (toolVersion.compareTo(VersionNumber.version(5)) < 0) {
            return Collections.singleton("pmd:pmd:" + versionString);
        } else if (toolVersion.compareTo(VersionNumber.parse("5.2.0")) < 0) {
            return Collections.singleton("net.sourceforge.pmd:pmd:" + versionString);
        } else if (toolVersion.compareTo(VersionNumber.version(7)) < 0) {
            return Collections.singleton("net.sourceforge.pmd:pmd-java:" + versionString);
        }

        // starting from version 7, PMD is split into multiple modules
        return ImmutableSet.of(
            "net.sourceforge.pmd:pmd-java:" + versionString,
            "net.sourceforge.pmd:pmd-ant:" + versionString
        );
    }

    @Override
    protected void configureForSourceSet(final SourceSet sourceSet, final Pmd task) {
        task.setDescription("Run PMD analysis for " + sourceSet.getName() + " classes");
        task.setSource(sourceSet.getAllJava());

        RoleBasedConfigurationContainerInternal configurations = (RoleBasedConfigurationContainerInternal) project.getConfigurations();
        Configuration compileClasspath = configurations.getByName(sourceSet.getCompileClasspathConfigurationName());

        // TODO: Consider checking if the resolution consistency is enabled for compile/runtime.
        @SuppressWarnings("deprecation") Configuration pmdAuxClasspath = configurations.createWithRole(sourceSet.getName() + "PmdAuxClasspath", ConfigurationRolesForMigration.RESOLVABLE_BUCKET_TO_RESOLVABLE);
        pmdAuxClasspath.extendsFrom(compileClasspath, additionalAuxClasspath);
        pmdAuxClasspath.setVisible(false);
        // This is important to get transitive implementation dependencies. PMD may load referenced classes for analysis so it expects the classpath to be "closed" world.
        getJvmPluginServices().configureAsRuntimeClasspath(pmdAuxClasspath);

        // We have to explicitly add compileClasspath here because it may contain classes that aren't part of the compileClasspathConfiguration. In particular, compile
        // classpath of the test sourceSet contains output of the main sourceSet.
        ConventionMapping taskMapping = task.getConventionMapping();
        taskMapping.map("classpath", () -> {
            // It is important to subtract compileClasspath and not pmdAuxClasspath here because these configurations are resolved differently (as a compile and as a
            // runtime classpath). Compile and runtime entries for the same dependency may resolve to different files (e.g. compiled classes directory vs. jar).
            FileCollection nonConfigurationClasspathEntries = sourceSet.getCompileClasspath().minus(compileClasspath);
            return sourceSet.getOutput().plus(nonConfigurationClasspathEntries).plus(pmdAuxClasspath);
        });
    }
}
