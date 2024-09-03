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

import com.google.common.collect.ImmutableSet;
import groovy.lang.GroovySystem;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.plugins.GroovyBasePlugin;
import org.gradle.api.plugins.JvmEcosystemPlugin;
import org.gradle.api.plugins.quality.internal.AbstractCodeQualityPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.GroovySourceDirectorySet;
import org.gradle.api.tasks.SourceSet;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.internal.CurrentJvmToolchainSpec;
import org.gradle.util.internal.VersionNumber;

import javax.inject.Inject;
import java.util.Set;

import static org.gradle.api.internal.lambdas.SerializableLambdas.action;

/**
 * CodeNarc Plugin.
 *
 * @see <a href="https://docs.gradle.org/current/userguide/codenarc_plugin.html">CodeNarc plugin reference</a>
 */
public abstract class CodeNarcPlugin extends AbstractCodeQualityPlugin<CodeNarc> {

    public static final String DEFAULT_CODENARC_VERSION = appropriateCodeNarcVersion();
    private static final String DEFAULT_CONFIG_FILE_PATH = "config/codenarc/codenarc.xml";
    private static final Set<String> REPORT_FORMATS = ImmutableSet.of("xml", "html", "console", "text");
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

    @Inject
    abstract protected JavaToolchainService getToolchainService();

    @Override
    protected Class<? extends Plugin<?>> getBasePlugin() {
        return GroovyBasePlugin.class;
    }

    @Override
    protected CodeQualityExtension createExtension() {
        extension = project.getExtensions().create("codenarc", CodeNarcExtension.class, project);
        extension.getToolVersion().convention(DEFAULT_CODENARC_VERSION);
        extension.setConfig(project.getResources().getText().fromFile(getRootProjectDirectory().file(DEFAULT_CONFIG_FILE_PATH)));
        extension.getMaxPriority1Violations().convention(0);
        extension.getMaxPriority2Violations().convention(0);
        extension.getMaxPriority3Violations().convention(0);
        extension.getReportFormat().convention("html");
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
        configureToolchains(task);
    }

    @Override
    protected void beforeApply() {
        // Necessary to disambiguate the published variants of newer codenarc versions (including the default version)
        project.getPluginManager().apply(JvmEcosystemPlugin.class);
    }

    private void configureDefaultDependencies(Configuration configuration) {
        configuration.defaultDependencies(dependencies ->
            dependencies.addLater(extension.getToolVersion().map(version -> project.getDependencies().create("org.codenarc:CodeNarc:" + version)))
        );
    }

    private void configureTaskConventionMapping(Configuration configuration, CodeNarc task) {
        ConventionMapping taskMapping = task.getConventionMapping();
        taskMapping.map("config", () -> extension.getConfig());
        task.getCodenarcClasspath().convention(configuration);
        task.getMaxPriority1Violations().convention(extension.getMaxPriority1Violations());
        task.getMaxPriority2Violations().convention(extension.getMaxPriority2Violations());
        task.getMaxPriority3Violations().convention(extension.getMaxPriority3Violations());
        task.getIgnoreFailuresProperty().convention(extension.getIgnoreFailures());
    }

    private void configureReportsConventionMapping(CodeNarc task, final String baseName) {
        Provider<String> reportFormat = extension.getReportFormat().map(format -> {
            if (REPORT_FORMATS.contains(format)) {
                return format;
            } else {
                throw new InvalidUserDataException("'" + format + "' is not a valid codenarc report format");
            }
        });
        Provider<Directory> reportsDir = extension.getReportsDir();
        task.getReports().all(action(report -> {
            report.getRequired().convention(reportFormat.map(format -> report.getName().equals(format)));
            report.getOutputLocation().convention(reportsDir.map(directory -> {
                String fileSuffix = report.getName().equals("text") ? "txt" : report.getName();
                return directory.file(baseName + "." + fileSuffix);
            }));
        }));
    }

    private void configureToolchains(CodeNarc task) {
        Provider<JavaLauncher> javaLauncherProvider = getToolchainService().launcherFor(project.getObjects().newInstance(CurrentJvmToolchainSpec.class));
        task.getJavaLauncher().convention(javaLauncherProvider);
        project.getPluginManager().withPlugin("java-base", p -> {
            JavaToolchainSpec toolchain = getJavaPluginExtension().getToolchain();
            task.getJavaLauncher().convention(getToolchainService().launcherFor(toolchain).orElse(javaLauncherProvider));
        });
    }

    @Override
    protected void configureForSourceSet(final SourceSet sourceSet, CodeNarc task) {
        task.setDescription("Run CodeNarc analysis for " + sourceSet.getName() + " classes");
        SourceDirectorySet groovySourceSet =  sourceSet.getExtensions().getByType(GroovySourceDirectorySet.class);
        task.setSource(groovySourceSet.matching(filter -> filter.include("**/*.groovy")));
    }

    private static String appropriateCodeNarcVersion() {
        int groovyMajorVersion = VersionNumber.parse(GroovySystem.getVersion()).getMajor();
        return groovyMajorVersion < 4 ? STABLE_VERSION : STABLE_VERSION_WITH_GROOVY4_SUPPORT;
    }
}
