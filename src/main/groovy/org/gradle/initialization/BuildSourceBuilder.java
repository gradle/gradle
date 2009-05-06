/*
 * Copyright 2007 the original author or authors.
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
import org.apache.ivy.core.IvyPatternHelper;
import org.gradle.*;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ResolverContainer;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Build;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class BuildSourceBuilder {
    private static Logger logger = LoggerFactory.getLogger(BuildSourceBuilder.class);

    public static final String BUILD_SRC_ORG = "org.gradle";
    public static final String BUILD_SRC_MODULE = "buildSrc";
    public static final String BUILD_SRC_REVISION = "SNAPSHOT";
    public static final String BUILD_SRC_TYPE = "jar";
    public static final String BUILD_SRC_ID = String.format("%s:%s:%s", BUILD_SRC_ORG, BUILD_SRC_MODULE, BUILD_SRC_REVISION);

    private GradleFactory gradleFactory;
    private CacheInvalidationStrategy cacheInvalidationStrategy;

    private static final String DEFAULT_BUILD_SOURCE_SCRIPT_RESOURCE = "defaultBuildSourceScript.txt";

    public BuildSourceBuilder(GradleFactory gradleFactory, CacheInvalidationStrategy cacheInvalidationStrategy) {
        this.gradleFactory = gradleFactory;
        this.cacheInvalidationStrategy = cacheInvalidationStrategy;
    }

    public Set<File> createBuildSourceClasspath(StartParameter startParameter) {
        assert startParameter.getCurrentDir() != null && startParameter.getBuildFile() == null;

        logger.debug("Starting to build the build sources.");
        if (!startParameter.getCurrentDir().isDirectory()) {
            logger.debug("Build source dir does not exists!. We leave.");
            return new HashSet<File>();
        }
        if (!GUtil.isTrue(startParameter.getTaskNames())) {
            logger.debug("No task names specified. We leave..");
            return new HashSet<File>();
        }
        logger.info("================================================" + " Start building buildSrc");
        try {
            Logging.LIFECYCLE.add(Logging.DISABLED);

            // todo Remove this redundancy. We have defined the buildResolverDir already somewhere else.
            // We should get the build resolver dir from the root project. But as we need to get the dir before
            // the build is executed, the Gradle class should offer distinct methods for gettting an evaluated
            // project tree and running a build against such a tree. In the case of a valid cache, this would also
            // save the time to build the dag.
            File buildResolverDir = new File(startParameter.getCurrentDir(), Project.TMP_DIR_NAME + "/" + ResolverContainer.INTERNAL_REPOSITORY_NAME);

            StartParameter startParameterArg = startParameter.newInstance();
            startParameterArg.setProjectProperties(GUtil.addMaps(startParameter.getProjectProperties(), getDependencyProjectProps()));
            startParameterArg.setSearchUpwards(false);

            if (startParameter.getCacheUsage() == CacheUsage.ON && cacheInvalidationStrategy.isValid(buildArtifactFile(buildResolverDir), startParameter.getCurrentDir())) {
                startParameterArg.setTaskNames(WrapUtil.toList(JavaPlugin.INIT_TASK_NAME));
            }

            if (!new File(startParameter.getCurrentDir(), Project.DEFAULT_BUILD_FILE).isFile()) {
                logger.debug("Build script file does not exists. Using default one.");
                startParameterArg.useEmbeddedBuildFile(getDefaultScript());
            }
            Gradle gradle = gradleFactory.newInstance(startParameterArg);
            BuildSourceBuildListener buildListener = new BuildSourceBuildListener();
            gradle.addBuildListener(buildListener);
            gradle.run().rethrowFailure();
            File artifactFile = buildArtifactFile(buildResolverDir);
            if (!artifactFile.exists()) {
                logger.info("Building buildSrc has not produced any artifact!");
                return new HashSet<File>();
            }
            Set<File> buildSourceClasspath = buildListener.getRootProject().getConfigurations().getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME).resolve();
            buildSourceClasspath.add(artifactFile);
            logger.debug("Build source classpath is: {}", buildSourceClasspath);
            logger.info("================================================" + " Finished building buildSrc");
            return buildSourceClasspath;
        } finally {
            Logging.LIFECYCLE.remove(Logging.DISABLED);
        }

    }

    static String getDefaultScript() {
        try {
            return IOUtils.toString(BuildSourceBuilder.class.getResourceAsStream(DEFAULT_BUILD_SOURCE_SCRIPT_RESOURCE));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public File buildArtifactFile(File buildResolverDir) {
        String path = IvyPatternHelper.substitute(buildResolverDir.getAbsolutePath() + "/" + ResolverContainer.BUILD_RESOLVER_PATTERN,
                BUILD_SRC_ORG, BUILD_SRC_MODULE, BUILD_SRC_REVISION, BUILD_SRC_MODULE, BUILD_SRC_TYPE, BUILD_SRC_TYPE);
        return new File(path);
    }

    private Map getDependencyProjectProps() {
        return GUtil.map(
                "group", BuildSourceBuilder.BUILD_SRC_ORG,
                "version", BuildSourceBuilder.BUILD_SRC_REVISION,
                "type", "jar");
    }

    public GradleFactory getGradleFactory() {
        return gradleFactory;
    }

    public void setGradleFactory(GradleFactory gradleFactory) {
        this.gradleFactory = gradleFactory;
    }

    public static class BuildSourceBuildListener implements BuildListener {
        private Project rootProject;

        public void buildStarted(StartParameter startParameter) {}

        public void settingsEvaluated(Settings settings) {}

        public void projectsLoaded(Build build) {}

        public void projectsEvaluated(Build build) {
            rootProject = build.getRootProject();
        }

        public void taskGraphPopulated(TaskExecutionGraph graph) {}

        public void buildFinished(BuildResult result) {}

        public Project getRootProject() {
            return rootProject;
        }

        public void setRootProject(Project rootProject) {
            this.rootProject = rootProject;
        }
    }
}
