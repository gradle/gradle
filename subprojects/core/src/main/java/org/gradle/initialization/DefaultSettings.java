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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.UnknownProjectException;
import org.gradle.api.initialization.ConfigurableIncludedBuild;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.initialization.Settings;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.plugins.DefaultObjectConfigurationAction;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.project.AbstractPluginAware;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.caching.configuration.BuildCacheConfiguration;
import org.gradle.composite.internal.IncludedBuildRegistry;
import org.gradle.configuration.ScriptPluginFactory;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.Actions;
import org.gradle.internal.resource.TextResourceLoader;
import org.gradle.internal.scripts.ScriptFileResolver;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;
import org.gradle.plugin.management.PluginManagementSpec;
import org.gradle.vcs.SourceControl;

import javax.inject.Inject;
import java.io.File;

import static org.gradle.util.NameValidator.asValidName;

public class DefaultSettings extends AbstractPluginAware implements SettingsInternal {
    public static final String DEFAULT_BUILD_SRC_DIR = "buildSrc";
    private ScriptSource settingsScript;

    private StartParameter startParameter;

    private File settingsDir;

    private DefaultProjectDescriptor rootProjectDescriptor;

    private ProjectDescriptor defaultProjectDescriptor;

    private GradleInternal gradle;

    private final ClassLoaderScope settingsClassLoaderScope;
    private final ClassLoaderScope buildRootClassLoaderScope;
    private final ScriptHandler scriptHandler;
    private final ServiceRegistry services;

    public DefaultSettings(ServiceRegistryFactory serviceRegistryFactory, GradleInternal gradle,
                           ClassLoaderScope settingsClassLoaderScope, ClassLoaderScope buildRootClassLoaderScope, ScriptHandler settingsScriptHandler,
                           File settingsDir, ScriptSource settingsScript, StartParameter startParameter) {
        this.gradle = gradle;
        this.buildRootClassLoaderScope = buildRootClassLoaderScope;
        this.scriptHandler = settingsScriptHandler;
        this.settingsDir = settingsDir;
        this.settingsScript = settingsScript;
        this.startParameter = startParameter;
        this.settingsClassLoaderScope = settingsClassLoaderScope;
        services = serviceRegistryFactory.createFor(this);
        rootProjectDescriptor = createProjectDescriptor(null, asValidName(settingsDir.getName()), settingsDir);
    }

    @Override
    public String toString() {
        return "settings '" + rootProjectDescriptor.getName() + "'";
    }

    public GradleInternal getGradle() {
        return gradle;
    }

    public Settings getSettings() {
        return this;
    }

    @Override
    public ScriptHandler getBuildscript() {
        return scriptHandler;
    }

    public DefaultProjectDescriptor createProjectDescriptor(DefaultProjectDescriptor parent, String name, File dir) {
        return new DefaultProjectDescriptor(parent, name, dir, getProjectDescriptorRegistry(), getFileResolver(), getScriptFileResolver());
    }

    public DefaultProjectDescriptor findProject(String path) {
        return getProjectDescriptorRegistry().getProject(path);
    }

    public DefaultProjectDescriptor findProject(File projectDir) {
        return getProjectDescriptorRegistry().getProject(projectDir);
    }

    public DefaultProjectDescriptor project(String path) {
        DefaultProjectDescriptor projectDescriptor = getProjectDescriptorRegistry().getProject(path);
        if (projectDescriptor == null) {
            throw new UnknownProjectException(String.format("Project with path '%s' could not be found.", path));
        }
        return projectDescriptor;
    }

    public DefaultProjectDescriptor project(File projectDir) {
        DefaultProjectDescriptor projectDescriptor = getProjectDescriptorRegistry().getProject(projectDir);
        if (projectDescriptor == null) {
            throw new UnknownProjectException(String.format("Project with path '%s' could not be found.", projectDir));
        }
        return projectDescriptor;
    }

    public void include(String... projectPaths) {
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

    public void includeFlat(String... projectNames) {
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

    public ProjectDescriptor getRootProject() {
        return rootProjectDescriptor;
    }

    public void setRootProjectDescriptor(DefaultProjectDescriptor rootProjectDescriptor) {
        this.rootProjectDescriptor = rootProjectDescriptor;
    }

    public ProjectDescriptor getDefaultProject() {
        return defaultProjectDescriptor;
    }

    public void setDefaultProject(ProjectDescriptor defaultProjectDescriptor) {
        this.defaultProjectDescriptor = defaultProjectDescriptor;
    }

    public File getRootDir() {
        return rootProjectDescriptor.getProjectDir();
    }

    public StartParameter getStartParameter() {
        return startParameter;
    }

    public void setStartParameter(StartParameter startParameter) {
        this.startParameter = startParameter;
    }

    public File getSettingsDir() {
        return settingsDir;
    }

    public void setSettingsDir(File settingsDir) {
        this.settingsDir = settingsDir;
    }

    public ScriptSource getSettingsScript() {
        return settingsScript;
    }

    public void setSettingsScript(ScriptSource settingsScript) {
        this.settingsScript = settingsScript;
    }

    @Inject
    public ProjectDescriptorRegistry getProjectDescriptorRegistry() {
        throw new UnsupportedOperationException();
    }

    public ProjectRegistry<DefaultProjectDescriptor> getProjectRegistry() {
        return getProjectDescriptorRegistry();
    }

    @Override
    protected DefaultObjectConfigurationAction createObjectConfigurationAction() {
        return new DefaultObjectConfigurationAction(getFileResolver(), getScriptPluginFactory(), getScriptHandlerFactory(), getRootClassLoaderScope(), getResourceLoader(), this);
    }

    public ClassLoaderScope getRootClassLoaderScope() {
        return buildRootClassLoaderScope;
    }

    public ClassLoaderScope getClassLoaderScope() {
        return settingsClassLoaderScope;
    }

    protected ServiceRegistry getServices() {
        return services;
    }

    @Inject
    protected ScriptFileResolver getScriptFileResolver() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected TextResourceLoader getResourceLoader() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ScriptHandlerFactory getScriptHandlerFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ScriptPluginFactory getScriptPluginFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected FileResolver getFileResolver() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected IncludedBuildRegistry getIncludedBuildRegistry() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected NestedBuildFactory getNestedBuildFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    public PluginManagerInternal getPluginManager() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void includeBuild(Object rootProject) {
        includeBuild(rootProject, Actions.<ConfigurableIncludedBuild>doNothing());
    }

    @Override
    public void includeBuild(Object rootProject, Action<ConfigurableIncludedBuild> configuration) {
        if (gradle.getParent() == null) {
            File projectDir = getFileResolver().resolve(rootProject);
            ConfigurableIncludedBuild includedBuild = getIncludedBuildRegistry().addExplicitBuild(projectDir, getNestedBuildFactory());
            configuration.execute(includedBuild);
        } else {
            throw new InvalidUserDataException(String.format("Included build '%s' cannot have included builds.", getRootProject().getName()));
        }
    }

    @Override
    public void buildCache(Action<? super BuildCacheConfiguration> action) {
        action.execute(getBuildCache());
    }

    @Override
    @Inject
    public BuildCacheConfiguration getBuildCache() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void pluginManagement(Action<? super PluginManagementSpec> rule) {
        rule.execute(getPluginManagement());
    }

    @Override
    @Inject
    public PluginManagementSpec getPluginManagement() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void sourceControl(Action<? super SourceControl> configuration) {
        configuration.execute(getSourceControl());
    }

    @Override
    @Inject
    public SourceControl getSourceControl() {
        throw new UnsupportedOperationException();
    }
}
