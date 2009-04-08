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
import org.gradle.api.UnknownProjectException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.ResolverContainer;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.artifacts.repositories.InternalRepository;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.internal.DynamicObjectHelper;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.artifacts.ConfigurationContainerFactory;
import org.gradle.api.internal.artifacts.DefaultModule;
import org.gradle.api.internal.artifacts.configurations.Configurations;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.configurations.ResolverProvider;
import org.gradle.api.internal.project.IProjectRegistry;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.util.ClasspathUtil;
import org.gradle.util.WrapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class BaseSettings implements SettingsInternal {
    private static Logger logger = LoggerFactory.getLogger(DefaultSettings.class);

    public static final String BUILD_CONFIGURATION = "build";
    public static final String DEFAULT_BUILD_SRC_DIR = "buildSrc";

    private Configuration buildConfiguration;

    private BuildSourceBuilder buildSourceBuilder;

    private ScriptSource settingsScript;

    private StartParameter startParameter;

    private File settingsDir;

    private StartParameter buildSrcStartParameter;

    private DefaultProjectDescriptor rootProjectDescriptor;

    private DynamicObjectHelper dynamicObjectHelper;

    private DependencyFactory dependencyFactory;

    private ResolverContainer resolverContainer;

    private InternalRepository internalRepository;

    IProjectDescriptorRegistry projectDescriptorRegistry;

    protected BaseSettings() {
    }

    public BaseSettings(DependencyFactory dependencyFactory,
                        ResolverContainer resolverContainer,
                        ConfigurationContainerFactory configurationContainerFactory,
                        InternalRepository internalRepository,
                        IProjectDescriptorRegistry projectDescriptorRegistry,
                        BuildSourceBuilder buildSourceBuilder, File settingsDir, ScriptSource settingsScript,
                        StartParameter startParameter) {
        this.dependencyFactory = dependencyFactory;
        this.resolverContainer = resolverContainer;
        this.internalRepository = internalRepository;
        this.projectDescriptorRegistry = projectDescriptorRegistry;
        this.settingsDir = settingsDir;
        this.settingsScript = settingsScript;
        this.startParameter = startParameter;
        this.buildSourceBuilder = buildSourceBuilder;
        this.buildConfiguration = createBuildConfiguration(configurationContainerFactory, startParameter);
        assignBuildSrcStartParameter(startParameter);
        rootProjectDescriptor = createProjectDescriptor(null, settingsDir.getName(), settingsDir);
        dynamicObjectHelper = new DynamicObjectHelper(this);
    }

    private Configuration createBuildConfiguration(ConfigurationContainerFactory configurationContainerFactory, final StartParameter startParameter) {
        DependencyMetaDataProvider metaDataProvider = new DependencyMetaDataProvider() {
            public Map getClientModuleRegistry() {
                return new HashMap();
            }

            public File getGradleUserHomeDir() {
                return startParameter.getGradleUserHomeDir();
            }

            public InternalRepository getInternalRepository() {
                return internalRepository;
            }

            public Module getModule() {
                return new DefaultModule(
                        BUILD_DEPENDENCIES_GROUP,
                        BUILD_DEPENDENCIES_NAME,
                        BUILD_DEPENDENCIES_VERSION
                );
            }
        };
        ResolverProvider resolverProvider = new ResolverProvider() {
            public List<DependencyResolver> getResolvers() {
                return resolverContainer.getResolverList();
            }
        };
        return configurationContainerFactory.createConfigurationContainer(resolverProvider, metaDataProvider).add(BUILD_CONFIGURATION);
    }

    private void assignBuildSrcStartParameter(StartParameter startParameter) {
        buildSrcStartParameter = startParameter.newBuild();
        buildSrcStartParameter.setTaskNames(WrapUtil.toList(JavaPlugin.CLEAN,
                Configurations.uploadInternalTaskName(Dependency.MASTER_CONFIGURATION)));
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
        for (Object dependency : dependencies) {
            buildConfiguration.addDependency(dependencyFactory.createDependency(dependency));
        }
    }

    public Dependency dependency(Object dependencyNotation, Closure configureClosure) {
        Dependency dependency = dependencyFactory.createDependency(dependencyNotation, configureClosure);
        buildConfiguration.addDependency(dependency);
        return dependency;
    }

    public void clientModule(String id, Closure configureClosure) {
        buildConfiguration.addDependency(dependencyFactory.createModule(id, configureClosure));
    }

    public FileSystemResolver flatDir(String name, Object[] dirs) {
        return resolverContainer.flatDir(name, dirs);
    }

    public FileSystemResolver flatDir(Object[] dirs) {
        return resolverContainer.flatDir(dirs);
    }

    public DependencyResolver mavenCentral(String[] jarRepoUrls) {
        return resolverContainer.mavenCentral(jarRepoUrls);
    }

    public DependencyResolver mavenRepo(String name, String root, String[] jarRepoUrls) {
        return resolverContainer.mavenRepo(name, root, jarRepoUrls);
    }

    public DependencyResolver mavenRepo(String root, String[] jarRepoUrls) {
        return resolverContainer.mavenRepo(root, jarRepoUrls);
    }

    public List<DependencyResolver> getResolvers() {
        return resolverContainer.getResolverList();
    }

    // todo We don't have command query separation here. This si a temporary thing. If our new classloader handling works out, which
    // adds simply the build script jars to the context classloader we can remove the return argument and simplify our design.
    public URLClassLoader createClassLoader() {
        URLClassLoader classLoader = (URLClassLoader) Thread.currentThread().getContextClassLoader();
        StartParameter startParameter = buildSrcStartParameter.newInstance();
        startParameter.setCurrentDir(new File(getRootDir(), DEFAULT_BUILD_SRC_DIR));
        Set<File> additionalClasspath = buildSourceBuilder.createBuildSourceClasspath(startParameter);
        additionalClasspath.addAll(buildConfiguration.getFiles());
        File toolsJar = ClasspathUtil.getToolsJar();
        if (toolsJar != null) {
            additionalClasspath.add(toolsJar);
        }
        logger.debug("Adding to classpath: {}", additionalClasspath);
        ClasspathUtil.addUrl(classLoader, additionalClasspath);
        return classLoader;
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

    public Configuration getBuildConfiguration() {
        return buildConfiguration;
    }

    public ResolverContainer getResolverContainer() {
        return resolverContainer;
    }

    public void setResolverContainer(ResolverContainer resolverContainer) {
        this.resolverContainer = resolverContainer;
    }

    public DependencyFactory getDependencyFactory() {
        return dependencyFactory;
    }

    public void setDependencyFactory(DependencyFactory dependencyFactory) {
        this.dependencyFactory = dependencyFactory;
    }

    public IProjectRegistry<DefaultProjectDescriptor> getProjectRegistry() {
        return projectDescriptorRegistry;
    }
}
