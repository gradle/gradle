/*
 * Copyright 2007-2008 the original author or authors.
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

import groovy.lang.Closure;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.FileSystemResolver;
import org.gradle.StartParameter;
import org.gradle.api.DependencyManager;
import org.gradle.api.Project;
import org.gradle.api.UnknownProjectException;
import org.gradle.api.artifacts.ConfigurationResolvers;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolverContainer;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.internal.DynamicObjectHelper;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.artifacts.DependencyManagerFactory;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.api.internal.project.IProjectRegistry;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.invocation.DefaultBuild;
import org.gradle.util.ClasspathUtil;
import org.gradle.util.WrapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class BaseSettings implements SettingsInternal {
    private static Logger logger = LoggerFactory.getLogger(DefaultSettings.class);

    public static final String BUILD_CONFIGURATION = "build";
    public static final String DEFAULT_BUILD_SRC_DIR = "buildSrc";

    private DependencyManager dependencyManager;

    private BuildSourceBuilder buildSourceBuilder;

    private ScriptSource settingsScript;

    private StartParameter startParameter;

    private File settingsDir;

    private StartParameter buildSrcStartParameter;

    private DefaultProjectDescriptor rootProjectDescriptor;

    private DynamicObjectHelper dynamicObjectHelper;

    IProjectDescriptorRegistry projectDescriptorRegistry;

    protected BaseSettings() {
    }
    
    public BaseSettings(DependencyManagerFactory dependencyManagerFactory,
                        IProjectDescriptorRegistry projectDescriptorRegistry,
                        BuildSourceBuilder buildSourceBuilder, File settingsDir, ScriptSource settingsScript,
                        StartParameter startParameter) {
        this.projectDescriptorRegistry = projectDescriptorRegistry;
        this.settingsDir = settingsDir;
        this.settingsScript = settingsScript;
        this.startParameter = startParameter;
        this.dependencyManager = dependencyManagerFactory.createDependencyManager(createBuildDependenciesProject(),
                startParameter.getGradleUserHomeDir());
        this.buildSourceBuilder = buildSourceBuilder;
        dependencyManager.addConfiguration(BUILD_CONFIGURATION);
        assignBuildSrcStartParameter(startParameter);
        rootProjectDescriptor = createProjectDescriptor(null, settingsDir.getName(), settingsDir);
        rootProjectDescriptor.setBuildFileName(startParameter.getBuildFileName());
        dynamicObjectHelper = new DynamicObjectHelper(this);
    }

    private void assignBuildSrcStartParameter(StartParameter startParameter) {
        buildSrcStartParameter = startParameter.newBuild();
        buildSrcStartParameter.setTaskNames(WrapUtil.toList(JavaPlugin.CLEAN,
                ConfigurationResolvers.uploadInternalTaskName(Dependency.MASTER_CONFIGURATION)));
        buildSrcStartParameter.setSearchUpwards(true);
    }

    @Override
    public String toString() {
        return String.format("settings '%s'", rootProjectDescriptor.getName());
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

    public void dependencies(Object[] dependencies) {
        dependencyManager.dependencies(WrapUtil.toList(BUILD_CONFIGURATION), dependencies);
    }
    
    public Dependency dependency(Object dependency, Closure configureClosure) {
        return dependencyManager.dependency(WrapUtil.toList(BUILD_CONFIGURATION), dependency, configureClosure);
    }

    public void clientModule(String id, Closure configureClosure) {
        dependencyManager.clientModule(WrapUtil.toList(BUILD_CONFIGURATION), id, configureClosure);
    }

    public ResolverContainer getResolvers() {
        return dependencyManager.getClasspathResolvers();
    }

    public FileSystemResolver addFlatDirResolver(String name, Object[] dirs) {
        return dependencyManager.addFlatDirResolver(name, dirs);
    }

    public DependencyResolver addMavenRepo(String[] jarRepoUrls) {
        return dependencyManager.addMavenRepo(jarRepoUrls);
    }

    public DependencyResolver addMavenStyleRepo(String name, String root, String[] jarRepoUrls) {
        return dependencyManager.addMavenStyleRepo(name, root, jarRepoUrls);
    }

    // todo We don't have command query separation here. This si a temporary thing. If our new classloader handling works out, which
    // adds simply the build script jars to the context classloader we can remove the return argument and simplify our design.
    public URLClassLoader createClassLoader() {
        URLClassLoader classLoader = (URLClassLoader) Thread.currentThread().getContextClassLoader();
        StartParameter startParameter = buildSrcStartParameter.newInstance();
        startParameter.setCurrentDir(new File(getRootDir(), DEFAULT_BUILD_SRC_DIR));
        List<File> additionalClasspath = buildSourceBuilder.createBuildSourceClasspath(startParameter);
        additionalClasspath.addAll(dependencyManager.configuration(BUILD_CONFIGURATION).getFiles());
        File toolsJar = ClasspathUtil.getToolsJar();
        if (toolsJar != null) {
            additionalClasspath.add(toolsJar);
        }
        logger.debug("Adding to classpath: {}", additionalClasspath);
        ClasspathUtil.addUrl(classLoader, additionalClasspath);
        return classLoader;
    }

    private Project createBuildDependenciesProject() {
        DefaultProject dummyProjectForDepencencyManager = new DefaultProject(BUILD_DEPENDENCIES_PROJECT_NAME);
        dummyProjectForDepencencyManager.setProperty(DependencyManager.GROUP, BUILD_DEPENDENCIES_PROJECT_GROUP);
        dummyProjectForDepencencyManager.setProperty(DependencyManager.VERSION, BUILD_DEPENDENCIES_PROJECT_VERSION);
        dummyProjectForDepencencyManager.setBuild(new DefaultBuild(startParameter, null));
        return dummyProjectForDepencencyManager;
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

    public DependencyManager getDependencyManager() {
        return dependencyManager;
    }

    public void setDependencyManager(DependencyManager dependencyManager) {
        this.dependencyManager = dependencyManager;
    }

    public BuildSourceBuilder getBuildSourceBuilder() {
        return buildSourceBuilder;
    }

    public void setBuildSourceBuilder(BuildSourceBuilder buildSourceBuilder) {
        this.buildSourceBuilder = buildSourceBuilder;
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

    public StartParameter getBuildSrcStartParameter() {
        return buildSrcStartParameter;
    }

    public void setBuildSrcStartParameter(StartParameter buildSrcStartParameter) {
        this.buildSrcStartParameter = buildSrcStartParameter;
    }

    public IProjectDescriptorRegistry getProjectDescriptorRegistry() {
        return projectDescriptorRegistry;
    }

    public void setProjectDescriptorRegistry(IProjectDescriptorRegistry projectDescriptorRegistry) {
        this.projectDescriptorRegistry = projectDescriptorRegistry;
    }

    public Map<String, Object> getAdditionalProperties() {
        return dynamicObjectHelper.getAdditionalProperties();
    }

    protected DynamicObjectHelper getDynamicObjectHelper() {
        return dynamicObjectHelper;
    }

    public IProjectRegistry<DefaultProjectDescriptor> getProjectRegistry() {
        return projectDescriptorRegistry;
    }
}
