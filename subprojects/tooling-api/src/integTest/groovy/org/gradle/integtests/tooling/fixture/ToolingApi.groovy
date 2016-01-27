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

import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.integtests.fixtures.daemon.DaemonsFixture
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.gradle.util.GradleVersion
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.concurrent.TimeUnit

class ToolingApi implements TestRule {
    private static final Logger LOGGER = LoggerFactory.getLogger(ToolingApi)

    private GradleDistribution dist
    private TestDirectoryProvider testWorkDirProvider
    private TestFile gradleUserHomeDir
    private TestFile daemonBaseDir
    private boolean useSeparateDaemonBaseDir
    private boolean requiresDaemon
    private boolean requireIsolatedDaemons

    private final List<Closure> connectorConfigurers = []
    boolean verboseLogging = LOGGER.debugEnabled

    ToolingApi(GradleDistribution dist, TestDirectoryProvider testWorkDirProvider) {
        this.dist = dist
        def context = new IntegrationTestBuildContext()
        this.useSeparateDaemonBaseDir = dist.toolingApiDaemonBaseDirSupported && DefaultGradleConnector.metaClass.respondsTo(null, "daemonBaseDir")
        this.gradleUserHomeDir = context.gradleUserHomeDir
        this.daemonBaseDir = context.daemonBaseDir
        this.requiresDaemon = !GradleContextualExecuter.embedded
        this.testWorkDirProvider = testWorkDirProvider
    }

    /**
     * Specifies that the test use its own Gradle user home dir and daemon registry.
     */
    void requireIsolatedUserHome() {
        withUserHome(testWorkDirProvider.testDirectory.file("user-home-dir"))
    }

    void withUserHome(TestFile userHomeDir) {
        gradleUserHomeDir = userHomeDir
        useSeparateDaemonBaseDir = false
    }

    TestFile getDaemonBaseDir() {
        return useSeparateDaemonBaseDir ? daemonBaseDir : gradleUserHomeDir.file("daemon")
    }

    /**
     * Specifies that the test use real daemon processes (not embedded) and a test-specific daemon registry. Uses a shared Gradle user home dir
     */
    void requireIsolatedDaemons() {
        if (useSeparateDaemonBaseDir) {
            daemonBaseDir = testWorkDirProvider.testDirectory.file("daemons")
        } else {
            gradleUserHomeDir = testWorkDirProvider.testDirectory.file("user-home-dir")
        }
        requireIsolatedDaemons = true
        requiresDaemon = true
    }

    /**
     * Specifies that the test use real daemon processes (not embedded).
     */
    void requireDaemons() {
        requiresDaemon = true
    }

    DaemonsFixture getDaemons() {
        return DaemonLogsAnalyzer.newAnalyzer(getDaemonBaseDir(), dist.version.version)
    }

    void withConnector(Closure cl) {
        connectorConfigurers << cl
    }

    public <T> T withConnection(Closure<T> cl) {
        GradleConnector connector = connector()
        withConnection(connector, cl)
    }

    public <T> T withConnection(GradleConnector connector, Closure<T> cl) {
        return withConnectionRaw(connector, cl)
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
            return connection.with(cl)
        } catch (Throwable t) {
            validate(t)
            throw t
        } finally {
            connection.close()
        }
    }

    GradleConnector connector() {
        DefaultGradleConnector connector = GradleConnector.newConnector()
        connector.useGradleUserHomeDir(new File(gradleUserHomeDir.path))
        if (useSeparateDaemonBaseDir) {
            connector.daemonBaseDir(new File(daemonBaseDir.path))
        }
        connector.forProjectDirectory(testWorkDirProvider.testDirectory)
        connector.searchUpwards(false)
        connector.daemonMaxIdleTime(120, TimeUnit.SECONDS)
        if (connector.metaClass.hasProperty(connector, 'verboseLogging')) {
            connector.verboseLogging = verboseLogging
        }
        if (useClasspathImplementation) {
            connector.useClasspathDistribution()
        } else {
            connector.useInstallation(dist.gradleHomeDir.absoluteFile)
        }
        connector.embedded(embedded)
        connectorConfigurers.each {
            connector.with(it)
        }
        return connector
    }

    boolean isUseClasspathImplementation() {
        // Use classpath implementation only when running tests in embedded mode and for the current Gradle version
        return GradleContextualExecuter.embedded && GradleVersion.current() == dist.version
    }

    boolean isEmbedded() {
        // Use in-process build when running tests in embedded mode and daemon is not required
        return GradleContextualExecuter.embedded && !requiresDaemon
    }

    @Override
    Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    base.evaluate();
                } finally {
                    if (requireIsolatedDaemons) {
                        try {
                            getDaemons().killAll()
                        } catch (RuntimeException ex) {
                            //TODO once we figured out why pid from logfile can be null we should remove this again
                            LOGGER.warn("Unable to kill daemon(s)", ex);
                        }
                    }
                }
            }
        };
    }
}
