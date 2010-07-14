/*
 * Copyright 2010 the original author or authors.
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

import org.apache.commons.io.IOUtils;
import org.gradle.*;
import org.gradle.api.Project;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.plugins.EmbeddableJavaProject;
import org.gradle.api.invocation.Gradle;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.StringScriptSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * @author Hans Dockter
 */
public class BuildSourceBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildSourceBuilder.class);

    private final GradleLauncherFactory gradleLauncherFactory;
    private final ClassLoaderFactory classLoaderFactory;

    private static final String DEFAULT_BUILD_SOURCE_SCRIPT_RESOURCE = "defaultBuildSourceScript.txt";

    public BuildSourceBuilder(GradleLauncherFactory gradleLauncherFactory, ClassLoaderFactory classLoaderFactory) {
        this.gradleLauncherFactory = gradleLauncherFactory;
        this.classLoaderFactory = classLoaderFactory;
    }

    public URLClassLoader buildAndCreateClassLoader(StartParameter startParameter)
    {
        Set<File> classpath = createBuildSourceClasspath(startParameter);
        Iterator<File> classpathIterator = classpath.iterator();
        URL[] urls = new URL[classpath.size()];
        for (int i = 0; i < urls.length; i++)
        {
            try
            {
                urls[i] = classpathIterator.next().toURI().toURL();
            }
            catch (MalformedURLException e)
            {
                throw new UncheckedIOException(e);
            }
        }
        return new URLClassLoader(urls, classLoaderFactory.getRootClassLoader());
    }

    public Set<File> createBuildSourceClasspath(StartParameter startParameter) {
        assert startParameter.getCurrentDir() != null && startParameter.getBuildFile() == null;

        LOGGER.debug("Starting to build the build sources.");
        if (!startParameter.getCurrentDir().isDirectory()) {
            LOGGER.debug("Gradle source dir does not exist. We leave.");
            return new HashSet<File>();
        }
        LOGGER.info("================================================" + " Start building buildSrc");
        StartParameter startParameterArg = startParameter.newInstance();
        startParameterArg.setProjectProperties(startParameter.getProjectProperties());
        startParameterArg.setSearchUpwards(false);

        if (!new File(startParameter.getCurrentDir(), Project.DEFAULT_BUILD_FILE).isFile()) {
            LOGGER.debug("Gradle script file does not exist. Using default one.");
            ScriptSource source = new StringScriptSource("default buildSrc build script", getDefaultScript());
            startParameterArg.setBuildScriptSource(source);
        }

        GradleLauncher gradleLauncher = gradleLauncherFactory.newInstance(startParameterArg);
        BuildSrcBuildListener listener = new BuildSrcBuildListener();
        gradleLauncher.addListener(listener);
        gradleLauncher.run().rethrowFailure();

        Set<File> buildSourceClasspath = new LinkedHashSet<File>();
        buildSourceClasspath.addAll(listener.getRuntimeClasspath());
        LOGGER.debug("Gradle source classpath is: {}", buildSourceClasspath);
        LOGGER.info("================================================" + " Finished building buildSrc");

        return buildSourceClasspath;
    }

    static String getDefaultScript() {
        try {
            return IOUtils.toString(BuildSourceBuilder.class.getResourceAsStream(DEFAULT_BUILD_SOURCE_SCRIPT_RESOURCE));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class BuildSrcBuildListener extends BuildAdapter {
        private EmbeddableJavaProject projectInfo;
        private Set<File> classpath;

        @Override
        public void projectsEvaluated(Gradle gradle) {
            projectInfo = gradle.getRootProject().getConvention().getPlugin(
                    EmbeddableJavaProject.class);
            gradle.getStartParameter().setTaskNames(projectInfo.getRebuildTasks());
            classpath = projectInfo.getRuntimeClasspath().getFiles();
        }

        public Collection<File> getRuntimeClasspath() {
            return classpath;
        }
    }
}
