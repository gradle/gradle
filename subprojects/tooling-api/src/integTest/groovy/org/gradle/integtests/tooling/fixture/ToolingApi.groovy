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
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.util.GradleVersion
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.concurrent.TimeUnit

class ToolingApi {
    private static final Logger LOGGER = LoggerFactory.getLogger(ToolingApi)

    private GradleDistribution dist
    private TestDirectoryProvider testWorkDirProvider
    private File userHomeDir

    private final List<Closure> connectorConfigurers = []
    boolean isEmbedded
    boolean verboseLogging = LOGGER.debugEnabled

    ToolingApi(GradleDistribution dist, TestDirectoryProvider testWorkDirProvider) {
        this(dist, new IntegrationTestBuildContext().gradleUserHomeDir, testWorkDirProvider, GradleContextualExecuter.embedded)
    }

    ToolingApi(GradleDistribution dist, File userHomeDir, TestDirectoryProvider testWorkDirProvider, boolean isEmbedded) {
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

    private validate(Throwable throwable) {
        if (dist.version != GradleVersion.current()) {
            return
        }

        // Verify that the exception carries the calling thread's stack information
        def currentThreadStack = Thread.currentThread().stackTrace as List
        while (!currentThreadStack.empty && (currentThreadStack[0].className != ToolingApi.name || currentThreadStack[0].methodName != 'withConnectionRaw')) {
            currentThreadStack.remove(0)
        }
        assert currentThreadStack.size() > 1
        currentThreadStack.remove(0)
        String currentThreadStackStr = currentThreadStack.join("\n")

        def throwableStack = throwable.stackTrace.join("\n")

        assert throwableStack.endsWith(currentThreadStackStr)
    }

    private <T> T withConnectionRaw(GradleConnector connector, Closure<T> cl) {
        ProjectConnection connection = connector.connect()
        try {
            return cl.call(connection)
        } catch (Throwable t) {
            validate(t)
            throw t
        } finally {
            connection.close()
        }
    }

    GradleConnector connector() {
        GradleConnector connector = GradleConnector.newConnector()
        connector.useGradleUserHomeDir(userHomeDir)
        connector.forProjectDirectory(testWorkDirProvider.testDirectory)
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
