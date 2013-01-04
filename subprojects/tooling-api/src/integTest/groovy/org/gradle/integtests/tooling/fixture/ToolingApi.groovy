/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.tooling.fixture

import org.gradle.integtests.fixtures.IntegrationTestHint
import org.gradle.integtests.fixtures.executer.BasicGradleDistribution
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.GradleDistributionExecuter
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.util.TestWorkDirProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.concurrent.TimeUnit

class ToolingApi {
    private static final Logger LOGGER = LoggerFactory.getLogger(ToolingApi)

    private BasicGradleDistribution dist
    private TestWorkDirProvider testWorkDirProvider
    private File userHomeDir

    private final List<Closure> connectorConfigurers = []
    boolean isEmbedded
    boolean verboseLogging = LOGGER.debugEnabled

    ToolingApi(GradleDistribution dist, TestWorkDirProvider testWorkDirProvider) {
        this(dist, dist.userHomeDir, testWorkDirProvider, GradleDistributionExecuter.systemPropertyExecuter == GradleDistributionExecuter.Executer.embedded)
    }

    ToolingApi(BasicGradleDistribution dist, File userHomeDir, TestWorkDirProvider testWorkDirProvider, boolean isEmbedded) {
        this.dist = dist
        this.userHomeDir = userHomeDir
        this.testWorkDirProvider = testWorkDirProvider
        this.isEmbedded = isEmbedded
    }

    void withConnector(Closure cl) {
        connectorConfigurers << cl
    }

    public <T> T withConnection(Closure<T> cl) {
        GradleConnector connector = connector()
        withConnection(connector, cl)
    }

    public <T> T withConnection(GradleConnector connector, Closure<T> cl) {
        try {
            return withConnectionRaw(connector, cl)
        } catch (UnsupportedVersionException e) {
            throw new IntegrationTestHint(e);
        }
    }

    public void maybeFailWithConnection(Closure cl) {
        GradleConnector connector = connector()
        try {
            withConnectionRaw(connector, cl)
        } catch (Throwable e) {
            throw e
        }
    }

    private <T> T withConnectionRaw(GradleConnector connector, Closure<T> cl) {
        ProjectConnection connection = connector.connect()
        try {
            return cl.call(connection)
        } finally {
            connection.close()
        }
    }

    GradleConnector connector() {
        GradleConnector connector = GradleConnector.newConnector()
        connector.useGradleUserHomeDir(userHomeDir)
        connector.forProjectDirectory(testWorkDirProvider.testWorkDir)
        connector.searchUpwards(false)
        connector.daemonMaxIdleTime(60, TimeUnit.SECONDS)
        if (connector.metaClass.hasProperty(connector, 'verboseLogging')) {
            connector.verboseLogging = verboseLogging
        }
        if (isEmbedded) {
            LOGGER.info("Using embedded tooling API provider");
            connector.useClasspathDistribution()
            connector.embedded(true)
        } else {
            LOGGER.info("Using daemon tooling API provider");
            connector.useInstallation(dist.gradleHomeDir.absoluteFile)
            connector.embedded(false)
        }
        connectorConfigurers.each {
            it.call(connector)
        }
        return connector
    }
}
