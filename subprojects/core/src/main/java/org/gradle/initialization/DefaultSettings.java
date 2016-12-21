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

import com.google.common.collect.Maps;
import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.UnknownProjectException;
import org.gradle.api.initialization.ConfigurableIncludedBuild;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.plugins.DefaultObjectConfigurationAction;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.project.AbstractPluginAware;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.configuration.ScriptPluginFactory;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;

import javax.inject.Inject;
import java.io.File;
import java.util.Map;

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
    private final ServiceRegistry services;
    private final Map<File, ConfigurableIncludedBuild> includedBuilds = Maps.newLinkedHashMap();

    public DefaultSettings(ServiceRegistryFactory serviceRegistryFactory, GradleInternal gradle,
                           ClassLoaderScope settingsClassLoaderScope, ClassLoaderScope buildRootClassLoaderScope, File settingsDir,
                           ScriptSource settingsScript, StartParameter startParameter) {
        this.gradle = gradle;
        this.buildRootClassLoaderScope = buildRootClassLoaderScope;
        this.settingsDir = settingsDir;
        this.settingsScript = settingsScript;
        this.startParameter = startParameter;
        this.settingsClassLoaderScope = settingsClassLoaderScope;
        services = serviceRegistryFactory.createFor(this);
        rootProjectDescriptor = createProjectDescriptor(null, settingsDir.getName(), settingsDir);
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

    public DefaultProjectDescriptor createProjectDescriptor(DefaultProjectDescriptor parent, String name, File dir) {
        return new DefaultProjectDescriptor(parent, name, dir, getProjectDescriptorRegistry(), getFileResolver());
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

    public void include(String[] projectPaths) {
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

    public void includeFlat(String[] projectNames) {
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
        return new DefaultObjectConfigurationAction(getFileResolver(), getScriptPluginFactory(), getScriptHandlerFactory(), getRootClassLoaderScope(), this);
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
    protected IncludedBuildFactory getIncludedBuildFactory() {
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
        File projectDir = getFileResolver().resolve(rootProject);
        ConfigurableIncludedBuild build = includedBuilds.get(projectDir);
        if (build == null) {
            build = getIncludedBuildFactory().createBuild(projectDir);
            includedBuilds.put(projectDir, build);
        }
        configuration.execute(build);
    }

    @Override
    public Map<File, IncludedBuild> getIncludedBuilds() {
        return Cast.uncheckedCast(includedBuilds);
    }
}
