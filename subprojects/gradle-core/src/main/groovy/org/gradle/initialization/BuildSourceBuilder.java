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

import org.apache.commons.io.IOUtils;
import org.gradle.*;
import org.gradle.api.Project;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.plugins.EmbedableJavaProject;
import org.gradle.api.invocation.Gradle;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.StringScriptSource;
import org.gradle.util.GFileUtils;
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
    private static Logger logger = LoggerFactory.getLogger(BuildSourceBuilder.class);

    private GradleLauncherFactory gradleLauncherFactory;
    private CacheInvalidationStrategy cacheInvalidationStrategy;
    private final ClassLoaderFactory classLoaderFactory;

    private static final String DEFAULT_BUILD_SOURCE_SCRIPT_RESOURCE = "defaultBuildSourceScript.txt";

    public BuildSourceBuilder(GradleLauncherFactory gradleLauncherFactory, CacheInvalidationStrategy cacheInvalidationStrategy, ClassLoaderFactory classLoaderFactory) {
        this.gradleLauncherFactory = gradleLauncherFactory;
        this.cacheInvalidationStrategy = cacheInvalidationStrategy;
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

        logger.debug("Starting to build the build sources.");
        if (!startParameter.getCurrentDir().isDirectory()) {
            logger.debug("Gradle source dir does not exist. We leave.");
            return new HashSet<File>();
        }
        logger.info("================================================" + " Start building buildSrc");
        StartParameter startParameterArg = startParameter.newInstance();
        startParameterArg.setProjectProperties(startParameter.getProjectProperties());
        startParameterArg.setSearchUpwards(false);
        boolean executeBuild = true;

        File markerFile = markerFile(startParameter.getCurrentDir());
        if (startParameter.getCacheUsage() == CacheUsage.ON && cacheInvalidationStrategy.isValid(markerFile, startParameter.getCurrentDir())) {
            executeBuild = false;
        }

        if (!new File(startParameter.getCurrentDir(), Project.DEFAULT_BUILD_FILE).isFile()) {
            logger.debug("Gradle script file does not exists. Using default one.");
            ScriptSource source = new StringScriptSource("default buildSrc build script", getDefaultScript());
            startParameterArg.setBuildScriptSource(source);
        }

        GradleLauncher gradleLauncher = gradleLauncherFactory.newInstance(startParameterArg);
        BuildSrcBuildListener listener = new BuildSrcBuildListener();
        gradleLauncher.addListener(listener);
        BuildResult buildResult;
        if (executeBuild) {
            buildResult = gradleLauncher.run();
        } else {
            buildResult = gradleLauncher.getBuildAnalysis();
        }
        buildResult.rethrowFailure();
        GFileUtils.touch(markerFile);

        Set<File> buildSourceClasspath = new LinkedHashSet<File>();
        buildSourceClasspath.addAll(listener.getRuntimeClasspath());
        logger.debug("Gradle source classpath is: {}", buildSourceClasspath);
        logger.info("================================================" + " Finished building buildSrc");
        return buildSourceClasspath;
    }

    static String getDefaultScript() {
        try {
            return IOUtils.toString(BuildSourceBuilder.class.getResourceAsStream(DEFAULT_BUILD_SOURCE_SCRIPT_RESOURCE));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public File markerFile(File buildSrcDir) {
        return new File(buildSrcDir, "build/COMPLETED");
    }

    public GradleLauncherFactory getGradleLauncherFactory() {
        return gradleLauncherFactory;
    }

    public void setGradleLauncherFactory(GradleLauncherFactory gradleLauncherFactory) {
        this.gradleLauncherFactory = gradleLauncherFactory;
    }

    private static class BuildSrcBuildListener extends BuildAdapter {
        private EmbedableJavaProject projectInfo;
        private Set<File> classpath;

        @Override
        public void projectsEvaluated(Gradle gradle) {
            projectInfo = gradle.getRootProject().getConvention().getPlugin(
                    EmbedableJavaProject.class);
            gradle.getStartParameter().setTaskNames(projectInfo.getRebuildTasks());
            classpath = projectInfo.getRuntimeClasspath().getFiles();
        }

        public Collection<File> getRuntimeClasspath() {
            return classpath;
        }
    }
}
