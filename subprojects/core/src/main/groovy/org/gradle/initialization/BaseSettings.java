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
import org.gradle.api.UnknownProjectException;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.AbstractPluginAware;
import org.gradle.api.internal.project.IProjectRegistry;
import org.gradle.api.internal.project.ServiceRegistryFactory;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.configuration.ScriptPluginFactory;
import org.gradle.groovy.scripts.ScriptSource;

import java.io.File;
import java.net.URLClassLoader;

/**
 * @author Hans Dockter
 */
public class BaseSettings extends AbstractPluginAware implements SettingsInternal {
    public static final String DEFAULT_BUILD_SRC_DIR = "buildSrc";

    private ScriptSource settingsScript;

    private StartParameter startParameter;

    private URLClassLoader classloader;

    private File settingsDir;

    private DefaultProjectDescriptor rootProjectDescriptor;

    private GradleInternal gradle;

    private IProjectDescriptorRegistry projectDescriptorRegistry;

    private  ServiceRegistryFactory services;

    private PluginContainer plugins;

    private FileResolver fileResolver;

    private ScriptPluginFactory scriptPluginFactory;

    public BaseSettings(ServiceRegistryFactory serviceRegistryFactory, GradleInternal gradle,
                        URLClassLoader classloader, File settingsDir, ScriptSource settingsScript,
                        StartParameter startParameter) {
        this.gradle = gradle;
        this.settingsDir = settingsDir;
        this.settingsScript = settingsScript;
        this.startParameter = startParameter;
        this.classloader = classloader;
        this.services = serviceRegistryFactory.createFor(this);
        this.plugins = services.get(PluginContainer.class);
        this.fileResolver = services.get(FileResolver.class);
        this.scriptPluginFactory = services.get(ScriptPluginFactory.class);
        this.projectDescriptorRegistry = services.get(IProjectDescriptorRegistry.class);
        rootProjectDescriptor = createProjectDescriptor(null, settingsDir.getName(), settingsDir);
    }

    @Override
    public String toString() {
        return String.format("settings '%s'", rootProjectDescriptor.getName());
    }

    public GradleInternal getGradle() {
        return gradle;
    }

    public Settings getSettings() {
        return this;
    }

    public DefaultProjectDescriptor createProjectDescriptor(DefaultProjectDescriptor parent, String name, File dir) {
        return new DefaultProjectDescriptor(parent, name, dir, projectDescriptorRegistry);
    }

    public DefaultProjectDescriptor findProject(String path) {
        return projectDescriptorRegistry.getProject(path);
    }

    public DefaultProjectDescriptor findProject(File projectDir) {
        return projectDescriptorRegistry.getProject(projectDir);
    }

    public DefaultProjectDescriptor project(String path) {
        DefaultProjectDescriptor projectDescriptor = projectDescriptorRegistry.getProject(path);
        if (projectDescriptor == null) {
            throw new UnknownProjectException(String.format("Project with path '%s' could not be found.", path));
        }
        return projectDescriptor;
    }

    public DefaultProjectDescriptor project(File projectDir) {
        DefaultProjectDescriptor projectDescriptor = projectDescriptorRegistry.getProject(projectDir);
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
                DefaultProjectDescriptor projectDescriptor = projectDescriptorRegistry.getProject(subPath);
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

    public URLClassLoader getClassLoader() {
        return classloader;
    }

    public ProjectDescriptor getRootProject() {
        return rootProjectDescriptor;
    }

    public void setRootProjectDescriptor(DefaultProjectDescriptor rootProjectDescriptor) {
        this.rootProjectDescriptor = rootProjectDescriptor;
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

    public IProjectDescriptorRegistry getProjectDescriptorRegistry() {
        return projectDescriptorRegistry;
    }

    public void setProjectDescriptorRegistry(IProjectDescriptorRegistry projectDescriptorRegistry) {
        this.projectDescriptorRegistry = projectDescriptorRegistry;
    }

    public IProjectRegistry<DefaultProjectDescriptor> getProjectRegistry() {
        return projectDescriptorRegistry;
    }

    public PluginContainer getPlugins() {
        return plugins;
    }


    @Override
    protected FileResolver getFileResolver() {
        return fileResolver;
    }

    @Override
    protected ScriptPluginFactory getScriptPluginFactory() {
        return scriptPluginFactory;
    }
}
