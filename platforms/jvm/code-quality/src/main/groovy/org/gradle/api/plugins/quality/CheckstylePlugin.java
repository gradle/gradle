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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.plugins.quality.internal.AbstractCodeQualityPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.resources.TextResource;
import org.gradle.api.tasks.SourceSet;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.internal.CurrentJvmToolchainSpec;

import javax.inject.Inject;
import java.io.File;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.gradle.api.internal.lambdas.SerializableLambdas.action;

/**
 * Checkstyle Plugin.
 *
 * @see <a href="https://docs.gradle.org/current/userguide/checkstyle_plugin.html">Checkstyle plugin reference</a>
 */
public abstract class CheckstylePlugin extends AbstractCodeQualityPlugin<Checkstyle> {

    public static final String DEFAULT_CHECKSTYLE_VERSION = "9.3";
    private static final String CONFIG_DIR_NAME = "config/checkstyle";
    private CheckstyleExtension extension;

    @Override
    protected String getToolName() {
        return "Checkstyle";
    }

    @Override
    protected Class<Checkstyle> getTaskType() {
        return Checkstyle.class;
    }

    @Inject
    protected JavaToolchainService getToolchainService() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected CodeQualityExtension createExtension() {
        extension = project.getExtensions().create("checkstyle", CheckstyleExtension.class, project);
        extension.setToolVersion(DEFAULT_CHECKSTYLE_VERSION);
        Directory directory = project.getRootProject().getLayout().getProjectDirectory().dir(CONFIG_DIR_NAME);
        extension.getConfigDirectory().convention(directory);
        extension.setConfig(project.getResources().getText().fromFile(extension.getConfigDirectory().file("checkstyle.xml")
            // If for whatever reason the provider above cannot be resolved, go back to default location, which we know how to ignore if missing
            .orElse(directory.file("checkstyle.xml"))));
        return extension;
    }

    @Override
    protected void configureConfiguration(Configuration configuration) {
        configureDefaultDependencies(configuration);
    }

    @Override
    protected void configureTaskDefaults(Checkstyle task, final String baseName) {
        Configuration configuration = project.getConfigurations().getAt(getConfigurationName());
        configureTaskConventionMapping(configuration, task);
        configureReportsConventionMapping(task, baseName);
        configureToolchains(task);
    }

    private void configureDefaultDependencies(Configuration configuration) {
        configuration.defaultDependencies(dependencies ->
            dependencies.add(project.getDependencies().create("com.puppycrawl.tools:checkstyle:" + extension.getToolVersion()))
        );
    }

    private void configureTaskConventionMapping(Configuration configuration, Checkstyle task) {
        ConventionMapping taskMapping = task.getConventionMapping();
        taskMapping.map("checkstyleClasspath", Callables.returning(configuration));
        taskMapping.map("config", (Callable<TextResource>) () -> extension.getConfig());
        taskMapping.map("configProperties", (Callable<Map<String, Object>>) () -> extension.getConfigProperties());
        taskMapping.map("ignoreFailures", (Callable<Boolean>) () -> extension.isIgnoreFailures());
        taskMapping.map("showViolations", (Callable<Boolean>) () -> extension.isShowViolations());
        taskMapping.map("maxErrors", (Callable<Integer>) () -> extension.getMaxErrors());
        taskMapping.map("maxWarnings", (Callable<Integer>) () -> extension.getMaxWarnings());
        task.getConfigDirectory().convention(extension.getConfigDirectory());
        task.getEnableExternalDtdLoad().convention(extension.getEnableExternalDtdLoad());
    }

    private void configureReportsConventionMapping(Checkstyle task, final String baseName) {
        ProjectLayout layout = project.getLayout();
        ProviderFactory providers = project.getProviders();
        Provider<RegularFile> reportsDir = layout.file(providers.provider(() -> extension.getReportsDir()));
        task.getReports().all(action(report -> {
            report.getRequired().convention(!report.getName().equals("sarif"));
            report.getOutputLocation().convention(
                layout.getProjectDirectory().file(providers.provider(() -> {
                    String reportFileName = baseName + "." + report.getName();
                    return new File(reportsDir.get().getAsFile(), reportFileName).getAbsolutePath();
                }))
            );
        }));
    }

    private void configureToolchains(Checkstyle task) {
        Provider<JavaLauncher> javaLauncherProvider = getToolchainService().launcherFor(new CurrentJvmToolchainSpec(project.getObjects()));
        task.getJavaLauncher().convention(javaLauncherProvider);
        project.getPluginManager().withPlugin("java-base", p -> {
            JavaToolchainSpec toolchain = getJavaPluginExtension().getToolchain();
            task.getJavaLauncher().convention(getToolchainService().launcherFor(toolchain).orElse(javaLauncherProvider));
        });
    }

    @Override
    protected void configureForSourceSet(final SourceSet sourceSet, Checkstyle task) {
        task.setDescription("Run Checkstyle analysis for " + sourceSet.getName() + " classes");
        task.setClasspath(sourceSet.getOutput().plus(sourceSet.getCompileClasspath()));
        task.setSource(sourceSet.getAllJava());
    }
}
