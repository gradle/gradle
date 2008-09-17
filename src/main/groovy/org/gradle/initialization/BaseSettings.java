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
import org.apache.ivy.plugins.resolver.FileSystemResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.StartParameter;
import org.gradle.api.DependencyManager;
import org.gradle.api.Project;
import org.gradle.api.UnknownProjectException;
import org.gradle.api.dependencies.ResolverContainer;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.internal.dependencies.DependencyManagerFactory;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.util.ClasspathUtil;
import org.gradle.util.GradleUtil;
import org.gradle.util.WrapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.util.HashMap;
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
    private Map<String, String> additionalProperties = new HashMap();

    private Map<String, String> gradleProperties;
    private StartParameter startParameter;

    private File settingsDir;

    private StartParameter buildSrcStartParameter;

    private DefaultProjectDescriptor rootProjectDescriptor;

    IProjectDescriptorRegistry projectDescriptorRegistry;

    public BaseSettings() {
    }

    public BaseSettings(DependencyManagerFactory dependencyManagerFactory, IProjectDescriptorRegistry projectDescriptorRegistry,
                        BuildSourceBuilder buildSourceBuilder, File settingsDir, Map<String, String> gradleProperties, StartParameter startParameter) {
        this.projectDescriptorRegistry = projectDescriptorRegistry;
        this.settingsDir = settingsDir;
        this.gradleProperties = gradleProperties;
        this.startParameter = startParameter;
        this.dependencyManager = dependencyManagerFactory.createDependencyManager(createBuildDependenciesProject());
        this.buildSourceBuilder = buildSourceBuilder;
        dependencyManager.addConfiguration(BUILD_CONFIGURATION);
        assignBuildSrcStartParameter(startParameter);
        rootProjectDescriptor = createProjectDescriptor(null, settingsDir.getName(), settingsDir);
    }

    private void assignBuildSrcStartParameter(StartParameter startParameter) {
        buildSrcStartParameter = startParameter.newBuild();
        buildSrcStartParameter.setTaskNames(WrapUtil.toList(JavaPlugin.CLEAN, JavaPlugin.UPLOAD_INTERNAL_LIBS));
        buildSrcStartParameter.setSearchUpwards(true);
    }

    public DefaultProjectDescriptor createProjectDescriptor(DefaultProjectDescriptor parent, String name, File dir) {
        return new DefaultProjectDescriptor(parent, name, dir, projectDescriptorRegistry);
    }

    public DefaultProjectDescriptor findDescriptor(String path) {
        return projectDescriptorRegistry.getProjectDescriptor(path);
    }

    public DefaultProjectDescriptor findDescriptor(File projectDir) {
        return projectDescriptorRegistry.getProjectDescriptor(projectDir);
    }

    public DefaultProjectDescriptor descriptor(String path) {
        DefaultProjectDescriptor projectDescriptor = projectDescriptorRegistry.getProjectDescriptor(path);
        if (projectDescriptor == null) {
            throw new UnknownProjectException(String.format("Project with path '%s' could not be found.", path));
        }
        return projectDescriptor;
    }

    public DefaultProjectDescriptor descriptor(File projectDir) {
        DefaultProjectDescriptor projectDescriptor = projectDescriptorRegistry.getProjectDescriptor(projectDir);
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
                DefaultProjectDescriptor projectDescriptor = projectDescriptorRegistry.getProjectDescriptor(subPath);
                if (projectDescriptor == null) {
                    parentProjectDescriptor = createProjectDescriptor(parentProjectDescriptor, pathElement, new File(parentProjectDescriptor.getDir(), pathElement));
                } else {
                    parentProjectDescriptor = projectDescriptor;
                }
            }
        }
    }

    public void includeFlat(String[] projectNames) {
        for (String projectName : projectNames) {
            createProjectDescriptor(rootProjectDescriptor, projectName,
                    new File(rootProjectDescriptor.getDir().getParentFile(), projectName));
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

    public void dependency(String id, Closure configureClosure) {
        dependencyManager.dependency(WrapUtil.toList(BUILD_CONFIGURATION), id, configureClosure);
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
        Object dependency = null;
        StartParameter startParameter = buildSrcStartParameter.newInstance();
        startParameter.setCurrentDir(new File(getRootDir(), DEFAULT_BUILD_SRC_DIR));
        if (buildSourceBuilder != null) {
            dependency = buildSourceBuilder.createDependency(dependencyManager.getBuildResolverDir(),
                    startParameter);
        }
        logger.debug("Build src dependency: {}", dependency);
        if (dependency != null) {
            dependencyManager.dependencies(WrapUtil.toList(BUILD_CONFIGURATION), dependency);
        } else {
            logger.info("No build sources found.");
        }
        List additionalClasspath = dependencyManager.resolve(BUILD_CONFIGURATION);
        File toolsJar = GradleUtil.getToolsJar();
        if (toolsJar != null) {
            additionalClasspath.add(toolsJar);
        }
        logger.debug("Adding to classpath: {}", additionalClasspath);
        ClasspathUtil.addUrl(classLoader, additionalClasspath);
        return classLoader;
    }

    private Project createBuildDependenciesProject() {
        DefaultProject dummyProjectForDepencencyManager = new DefaultProject();
        dummyProjectForDepencencyManager.setProperty(DependencyManager.GROUP, BUILD_DEPENDENCIES_PROJECT_GROUP);
        dummyProjectForDepencencyManager.setName(BUILD_DEPENDENCIES_PROJECT_NAME);
        dummyProjectForDepencencyManager.setProperty(DependencyManager.VERSION, BUILD_DEPENDENCIES_PROJECT_VERSION);
        try {
            dummyProjectForDepencencyManager.setGradleUserHome(startParameter.getGradleUserHomeDir().getCanonicalPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return dummyProjectForDepencencyManager;
    }

    public ProjectDescriptor getRootProjectDescriptor() {
        return rootProjectDescriptor;
    }

    public void setRootProjectDescriptor(DefaultProjectDescriptor rootProjectDescriptor) {
        this.rootProjectDescriptor = rootProjectDescriptor;
    }

    public File getRootDir() {
        return rootProjectDescriptor.getDir();
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

    public Map<String, String> getAdditionalProperties() {
        return additionalProperties;
    }

    public void setAdditionalProperties(Map<String, String> additionalProperties) {
        this.additionalProperties = additionalProperties;
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

    public Map<String, String> getGradleProperties() {
        return gradleProperties;
    }
}
