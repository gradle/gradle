/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.initialization;

import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.UnknownProjectException;
import org.gradle.api.cache.CacheConfigurations;
import org.gradle.api.initialization.ConfigurableIncludedBuild;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.initialization.Settings;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.initialization.resolve.DependencyResolutionManagement;
import org.gradle.api.internal.FeaturePreviews.Feature;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.cache.CacheConfigurationsInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.plugins.DefaultObjectConfigurationAction;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.project.AbstractPluginAware;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.toolchain.management.ToolchainManagement;
import org.gradle.caching.configuration.BuildCacheConfiguration;
import org.gradle.caching.configuration.internal.BuildCacheConfigurationInternal;
import org.gradle.configuration.ScriptPluginFactory;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.Actions;
import org.gradle.internal.buildoption.FeatureFlags;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.management.DependencyResolutionManagementInternal;
import org.gradle.internal.management.ToolchainManagementInternal;
import org.gradle.internal.resource.TextUriResourceLoader;
import org.gradle.internal.scripts.ScriptFileResolver;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;
import org.gradle.plugin.management.PluginManagementSpec;
import org.gradle.plugin.management.internal.PluginManagementSpecInternal;
import org.gradle.vcs.SourceControl;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static java.time.Instant.now;
import static org.apache.commons.lang.ArrayUtils.contains;
import static org.gradle.internal.hash.Hashing.sha512;

public abstract class DefaultSettings extends AbstractPluginAware implements SettingsInternal {
    private ScriptSource settingsScript;

    private StartParameter startParameter;

    private File settingsDir;

    private DefaultProjectDescriptor rootProjectDescriptor;

    private DefaultProjectDescriptor defaultProjectDescriptor;

    private final GradleInternal gradle;

    private final ClassLoaderScope classLoaderScope;
    private final ClassLoaderScope baseClassLoaderScope;
    private final ScriptHandler scriptHandler;
    private final ServiceRegistry services;

    private final List<IncludedBuildSpec> includedBuildSpecs = new ArrayList<>();
    private final DependencyResolutionManagementInternal dependencyResolutionManagement;

    private final ToolchainManagementInternal toolchainManagement;

    public DefaultSettings(
        ServiceRegistryFactory serviceRegistryFactory,
        GradleInternal gradle,
        ClassLoaderScope classLoaderScope,
        ClassLoaderScope baseClassLoaderScope,
        ScriptHandler settingsScriptHandler,
        File settingsDir,
        ScriptSource settingsScript,
        StartParameter startParameter
    ) {
        this.gradle = gradle;
        this.classLoaderScope = classLoaderScope;
        this.baseClassLoaderScope = baseClassLoaderScope;
        this.scriptHandler = settingsScriptHandler;
        this.settingsDir = settingsDir;
        this.settingsScript = settingsScript;
        this.startParameter = startParameter;
        this.services = serviceRegistryFactory.createFor(this);
        this.rootProjectDescriptor = createProjectDescriptor(null, getProjectName(settingsDir), settingsDir);
        this.dependencyResolutionManagement = services.get(DependencyResolutionManagementInternal.class);
        this.toolchainManagement = services.get(ToolchainManagementInternal.class);
    }

    private static String getProjectName(File settingsDir) {
        if (contains(File.listRoots(), settingsDir)) {
            String rootIndicator = settingsDir.toPath().getRoot().toString().replaceAll("[\\\\:\\/]*", "");
            // using "-" to separate the parts of the root project name to allow easier usage in the CLI, just in case.
            return "generated-" + rootIndicator  + (rootIndicator.isEmpty() ? "" : "-") +
                sha512().hashString(now().toString()).toString().substring(0, 6);
        }
        return settingsDir.getName();
    }

    @Override
    public String toString() {
        return "settings '" + rootProjectDescriptor.getName() + "'";
    }

    @Override
    public GradleInternal getGradle() {
        return gradle;
    }

    @Override
    public List<IncludedBuildSpec> getIncludedBuilds() {
        return includedBuildSpecs;
    }

    @Override
    public Settings getSettings() {
        return this;
    }

    @Override
    public ScriptHandler getBuildscript() {
        return scriptHandler;
    }

    public DefaultProjectDescriptor createProjectDescriptor(@Nullable DefaultProjectDescriptor parent, String name, File dir) {
        return new DefaultProjectDescriptor(parent, name, dir, getProjectDescriptorRegistry(), getFileResolver(), getScriptFileResolver());
    }

    @Override
    public DefaultProjectDescriptor findProject(String path) {
        return getProjectDescriptorRegistry().getProject(path);
    }

    @Override
    public DefaultProjectDescriptor findProject(File projectDir) {
        return getProjectDescriptorRegistry().getProject(projectDir);
    }

    @Override
    public DefaultProjectDescriptor project(String path) {
        DefaultProjectDescriptor projectDescriptor = getProjectDescriptorRegistry().getProject(path);
        if (projectDescriptor == null) {
            throw new UnknownProjectException(String.format("Project with path '%s' could not be found.", path));
        }
        return projectDescriptor;
    }

    @Override
    public DefaultProjectDescriptor project(File projectDir) {
        DefaultProjectDescriptor projectDescriptor = getProjectDescriptorRegistry().getProject(projectDir);
        if (projectDescriptor == null) {
            throw new UnknownProjectException(String.format("Project with path '%s' could not be found.", projectDir));
        }
        return projectDescriptor;
    }

    @Override
    public void include(Iterable<String> projectPaths) {
        for (String projectPath : projectPaths) {
            String subPath = "";
            String[] pathElements = removeTrailingColon(projectPath).split(":");
            DefaultProjectDescriptor parentProjectDescriptor = rootProjectDescriptor;
            for (String pathElement : pathElements) {
                subPath = subPath + ":" + pathElement;
                DefaultProjectDescriptor projectDescriptor = getProjectDescriptorRegistry().getProject(subPath);
                if (projectDescriptor == null) {
                    parentProjectDescriptor = createProjectDescriptor(parentProjectDescriptor, pathElement, new File(parentProjectDescriptor.getProjectDir(), pathElement));
                } else {
                    parentProjectDescriptor = projectDescriptor;
                }
            }
        }
    }

    @Override
    public void includeFlat(Iterable<String> projectNames) {
        for (String projectName : projectNames) {
            createProjectDescriptor(rootProjectDescriptor, projectName,
                new File(rootProjectDescriptor.getProjectDir().getParentFile(), projectName));
        }
    }

    private String removeTrailingColon(String projectPath) {
        if (projectPath.startsWith(":")) {
            return projectPath.substring(1);
        }
        return projectPath;
    }

    @Override
    public ProjectDescriptor getRootProject() {
        return rootProjectDescriptor;
    }

    public void setRootProjectDescriptor(DefaultProjectDescriptor rootProjectDescriptor) {
        this.rootProjectDescriptor = rootProjectDescriptor;
    }

    @Override
    public DefaultProjectDescriptor getDefaultProject() {
        return defaultProjectDescriptor;
    }

    @Override
    public void setDefaultProject(DefaultProjectDescriptor defaultProjectDescriptor) {
        this.defaultProjectDescriptor = defaultProjectDescriptor;
    }

    @Override
    public File getRootDir() {
        return rootProjectDescriptor.getProjectDir();
    }

    @Override
    public StartParameter getStartParameter() {
        return startParameter;
    }

    public void setStartParameter(StartParameter startParameter) {
        this.startParameter = startParameter;
    }

    @Override
    public File getSettingsDir() {
        return settingsDir;
    }

    public void setSettingsDir(File settingsDir) {
        this.settingsDir = settingsDir;
    }

    @Override
    public ScriptSource getSettingsScript() {
        return settingsScript;
    }

    public void setSettingsScript(ScriptSource settingsScript) {
        this.settingsScript = settingsScript;
    }

    @Override
    @Inject
    public abstract ProviderFactory getProviders();

    @Inject
    public abstract ProjectDescriptorRegistry getProjectDescriptorRegistry();

    @Inject
    public abstract TextUriResourceLoader.Factory getTextUriResourceLoaderFactory();

    @Inject
    public abstract ScriptFileResolver getScriptFileResolver();

    @Override
    public ProjectRegistry<DefaultProjectDescriptor> getProjectRegistry() {
        return getProjectDescriptorRegistry();
    }

    @Override
    protected DefaultObjectConfigurationAction createObjectConfigurationAction() {
        return new DefaultObjectConfigurationAction(
            getFileResolver(),
            getScriptPluginFactory(),
            getScriptHandlerFactory(),
            baseClassLoaderScope,
            getTextUriResourceLoaderFactory(),
            this);
    }

    @Override
    public ClassLoaderScope getBaseClassLoaderScope() {
        return baseClassLoaderScope;
    }

    @Override
    public ClassLoaderScope getClassLoaderScope() {
        return classLoaderScope;
    }

    @Override
    public ServiceRegistry getServices() {
        return services;
    }

    @Inject
    protected abstract ScriptHandlerFactory getScriptHandlerFactory();

    @Inject
    protected abstract ScriptPluginFactory getScriptPluginFactory();

    @Inject
    protected abstract FileResolver getFileResolver();

    @Override
    @Inject
    public abstract PluginManagerInternal getPluginManager();

    @Override
    public void includeBuild(Object rootProject) {
        includeBuild(rootProject, Actions.doNothing());
    }

    @Override
    public void includeBuild(Object rootProject, Action<ConfigurableIncludedBuild> configuration) {
        File projectDir = getFileResolver().resolve(rootProject);
        includedBuildSpecs.add(IncludedBuildSpec.includedBuild(projectDir, configuration));
    }

    @Override
    public void buildCache(Action<? super BuildCacheConfiguration> action) {
        action.execute(getBuildCache());
    }

    @Override
    @Inject
    public abstract BuildCacheConfigurationInternal getBuildCache();

    @Override
    public void pluginManagement(Action<? super PluginManagementSpec> rule) {
        rule.execute(getPluginManagement());
        includedBuildSpecs.addAll(((PluginManagementSpecInternal) getPluginManagement()).getIncludedBuilds());
    }

    @Override
    @Inject
    public abstract PluginManagementSpec getPluginManagement();

    @Override
    public void sourceControl(Action<? super SourceControl> configuration) {
        configuration.execute(getSourceControl());
    }

    @Override
    @Inject
    public abstract SourceControl getSourceControl();

    @Override
    public void enableFeaturePreview(String name) {
        Feature feature = Feature.withName(name);
        if (feature.isActive()) {
            services.get(FeatureFlags.class).enable(feature);
        } else {
            DeprecationLogger
                .deprecate("enableFeaturePreview('" + feature.name() + "')")
                .withAdvice("The feature flag is no longer relevant, please remove it from your settings file.")
                .willBeRemovedInGradle9()
                .withUserManual("feature_lifecycle", "feature_preview")
                .nagUser();
        }
    }

    @Override
    public void preventFromFurtherMutation() {
        dependencyResolutionManagement.preventFromFurtherMutation();
        toolchainManagement.preventFromFurtherMutation();
    }

    @Override
    public void dependencyResolutionManagement(Action<? super DependencyResolutionManagement> dependencyResolutionConfiguration) {
        dependencyResolutionConfiguration.execute(dependencyResolutionManagement);
    }

    @Override
    public DependencyResolutionManagementInternal getDependencyResolutionManagement() {
        return dependencyResolutionManagement;
    }

    @Override
    public ToolchainManagement getToolchainManagement() {
        return toolchainManagement;
    }

    @Override
    public void toolchainManagement(Action<? super ToolchainManagement> toolchainManagementConfiguration) {
        toolchainManagementConfiguration.execute(toolchainManagement);
    }

    @Override
    @Inject
    public abstract CacheConfigurationsInternal getCaches();

    @Override
    public void caches(Action<? super CacheConfigurations> cachesConfiguration) {
        cachesConfiguration.execute(getCaches());
    }
}
