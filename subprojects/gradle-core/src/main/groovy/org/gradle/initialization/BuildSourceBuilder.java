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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;
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

    public static final String BUILD_SRC_ORG = "org.gradle";
    public static final String BUILD_SRC_MODULE = "buildSrc";
    public static final String BUILD_SRC_REVISION = "SNAPSHOT";
    public static final String BUILD_SRC_TYPE = "jar";

    private GradleLauncherFactory gradleLauncherFactory;
    private CacheInvalidationStrategy cacheInvalidationStrategy;

    private static final String DEFAULT_BUILD_SOURCE_SCRIPT_RESOURCE = "defaultBuildSourceScript.txt";

    public BuildSourceBuilder(GradleLauncherFactory gradleLauncherFactory, CacheInvalidationStrategy cacheInvalidationStrategy) {
        this.gradleLauncherFactory = gradleLauncherFactory;
        this.cacheInvalidationStrategy = cacheInvalidationStrategy;
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
        return new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
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
        startParameterArg.setProjectProperties(GUtil.addMaps(startParameter.getProjectProperties(), getDependencyProjectProps()));
        startParameterArg.setSearchUpwards(false);
        startParameterArg.setTaskNames(WrapUtil.toList(BasePlugin.CLEAN_TASK_NAME, JavaPlugin.BUILD_TASK_NAME));
        boolean executeBuild = true;

        File artifactFile = buildArtifactFile(startParameter.getCurrentDir());
        if (startParameter.getCacheUsage() == CacheUsage.ON && cacheInvalidationStrategy.isValid(artifactFile, startParameter.getCurrentDir())) {
            executeBuild = false;
        }

        if (!new File(startParameter.getCurrentDir(), Project.DEFAULT_BUILD_FILE).isFile()) {
            logger.debug("Gradle script file does not exists. Using default one.");
            startParameterArg.useEmbeddedBuildFile(getDefaultScript());
        }

        GradleLauncher gradleLauncher = gradleLauncherFactory.newInstance(startParameterArg);
        BuildResult buildResult;
        if (executeBuild) {
            buildResult = gradleLauncher.run();
        } else {
            buildResult = gradleLauncher.getBuildAnalysis();
        }
        buildResult.rethrowFailure();

        Set<File> buildSourceClasspath = new LinkedHashSet<File>();
        if (artifactFile.exists()) {
            buildSourceClasspath.add(artifactFile);
        } else {
            logger.info("Building buildSrc has not produced any artifacts.");
        }
        Configuration runtimeConfiguration = buildResult.getGradle().getRootProject().getConfigurations().getByName(
                JavaPlugin.RUNTIME_CONFIGURATION_NAME);
        buildSourceClasspath.addAll(runtimeConfiguration.getFiles());
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

    public File buildArtifactFile(File buildSrcDir) {
        return new File(buildSrcDir, String.format("build/libs/buildSrc-%s.jar", BuildSourceBuilder.BUILD_SRC_REVISION));
    }

    private Map getDependencyProjectProps() {
        return GUtil.map(
                "group", BUILD_SRC_ORG,
                "version", BUILD_SRC_REVISION,
                "type", BUILD_SRC_TYPE);
    }

    public GradleLauncherFactory getGradleLauncherFactory() {
        return gradleLauncherFactory;
    }

    public void setGradleLauncherFactory(GradleLauncherFactory gradleLauncherFactory) {
        this.gradleLauncherFactory = gradleLauncherFactory;
    }
}
