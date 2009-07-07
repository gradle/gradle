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

import org.gradle.StartParameter;
import org.gradle.api.UnknownProjectException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.internal.DynamicObjectHelper;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.project.IProjectRegistry;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.util.ClasspathUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URLClassLoader;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

/**
 * @author Hans Dockter
 */
public class BaseSettings implements SettingsInternal {
    private static Logger logger = LoggerFactory.getLogger(DefaultSettings.class);

    public static final String DEFAULT_BUILD_SRC_DIR = "buildSrc";

    private BuildSourceBuilder buildSourceBuilder;

    private ScriptSource settingsScript;

    private StartParameter startParameter;

    private File settingsDir;

    private DefaultProjectDescriptor rootProjectDescriptor;

    private DynamicObjectHelper dynamicObjectHelper;

    private IProjectDescriptorRegistry projectDescriptorRegistry;

    protected BaseSettings() {
    }

    public BaseSettings(IProjectDescriptorRegistry projectDescriptorRegistry,
                        BuildSourceBuilder buildSourceBuilder, File settingsDir, ScriptSource settingsScript,
                        StartParameter startParameter) {
        this.projectDescriptorRegistry = projectDescriptorRegistry;
        this.settingsDir = settingsDir;
        this.settingsScript = settingsScript;
        this.startParameter = startParameter;
        this.buildSourceBuilder = buildSourceBuilder;
        rootProjectDescriptor = createProjectDescriptor(null, settingsDir.getName(), settingsDir);
        dynamicObjectHelper = new DynamicObjectHelper(this);
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

    // todo We don't have command query separation here. This is a temporary thing. If our new classloader handling works out, which
    // adds simply the build script jars to the context classloader we can remove the return argument and simplify our design.
    public URLClassLoader createClassLoader() {
        ClassLoader parentClassLoader = Thread.currentThread().getContextClassLoader();
        StartParameter buildSrcStartParameter = startParameter.newBuild();
        buildSrcStartParameter.setCurrentDir(new File(getRootDir(), DEFAULT_BUILD_SRC_DIR));
        Set<File> additionalClasspath = buildSourceBuilder.createBuildSourceClasspath(buildSrcStartParameter);
        File toolsJar = ClasspathUtil.getToolsJar();
        if (toolsJar != null) {
            additionalClasspath.add(toolsJar);
        }
        logger.debug("Adding to classpath: {}", additionalClasspath);
        List<URL> urls = new ArrayList<URL>();
        for (File file : additionalClasspath) {
            try {
                urls.add(file.toURI().toURL());
            } catch (MalformedURLException e) {
                throw new UncheckedIOException(e);
            }
        }
        return new URLClassLoader(urls.toArray(new URL[urls.size()]), parentClassLoader);
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
