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
import org.gradle.StartParameter;
import org.gradle.api.DependencyManager;
import org.gradle.util.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;

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

    private EmbeddedBuildExecuter embeddedBuildExecuter;
    private static final String DEFAULT_BUILD_SOURCE_SCRIPT_RESOURCE = "defaultBuildSourceScript.txt";

    public BuildSourceBuilder() {
    }

    public BuildSourceBuilder(EmbeddedBuildExecuter embeddedBuildExecuter) {
        this.embeddedBuildExecuter = embeddedBuildExecuter;
    }

    public Object createDependency(File buildResolverDir, StartParameter startParameter) {
        assert startParameter.getCurrentDir() != null && GUtil.isTrue(startParameter.getBuildFileName()) && buildResolverDir != null;

        logger.debug("Starting to build the build sources.");
        if (!startParameter.getCurrentDir().isDirectory()) {
            logger.debug("Build source dir does not exists!. We leave.");
            return null;
        }
        if (!GUtil.isTrue(startParameter.getTaskNames())) {
            logger.debug("No task names specified. We leave..");
            return null;
        }
        logger.info("================================================" + " Start building buildSrc");
        StartParameter startParameterArg = StartParameter.newInstance(startParameter);
        startParameterArg.setProjectProperties(GUtil.addMaps(startParameter.getProjectProperties(), getDependencyProjectProps()));
        startParameterArg.setSearchUpwards(false);
        startParameterArg.setBuildResolverDirectory(buildResolverDir);

        if (!new File(startParameter.getCurrentDir(), startParameter.getBuildFileName()).isFile()) {
            logger.debug("Build script file does not exists. Using default one.");
            startParameterArg.useEmbeddedBuildFile(getDefaultScript());
        }
        embeddedBuildExecuter.execute(startParameterArg);
        logger.info("Check if build artifact exists: ${buildArtifactFile(buildResolverDir)}");
        if (!buildArtifactFile(buildResolverDir).exists()) {
            logger.info("Building buildSrc has not produced any artifact!");
            return null;
        }
        logger.info("================================================" + " Finished building buildSrc");
        return BUILD_SRC_ID;
    }

    static String getDefaultScript() {
        try {
            return IOUtils.toString(BuildSourceBuilder.class.getResourceAsStream(DEFAULT_BUILD_SOURCE_SCRIPT_RESOURCE));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public File buildArtifactFile(File buildResolverDir) {
        String path = IvyPatternHelper.substitute(buildResolverDir.getAbsolutePath() + "/" + DependencyManager.BUILD_RESOLVER_PATTERN,
                BUILD_SRC_ORG, BUILD_SRC_MODULE, BUILD_SRC_REVISION, BUILD_SRC_MODULE, BUILD_SRC_TYPE, BUILD_SRC_TYPE);
        return new File(path);
    }

    private Map getDependencyProjectProps() {
        return GUtil.map(
                "group", BuildSourceBuilder.BUILD_SRC_ORG,
                "version", BuildSourceBuilder.BUILD_SRC_REVISION,
                "type", "jar");
    }

    public EmbeddedBuildExecuter getEmbeddedBuildExecuter() {
        return embeddedBuildExecuter;
    }

    public void setEmbeddedBuildExecuter(EmbeddedBuildExecuter embeddedBuildExecuter) {
        this.embeddedBuildExecuter = embeddedBuildExecuter;
    }
}
