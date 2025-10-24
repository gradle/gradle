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

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.apache.commons.io.output.TeeOutputStream
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.integtests.fixtures.daemon.DaemonsFixture
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.internal.service.ServiceRegistry
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.internal.consumer.ConnectorServices
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.gradle.tooling.internal.consumer.GradleConnectorFactory
import org.gradle.tooling.model.build.BuildEnvironment
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
    private ConnectorFactory connectorFactory = new SharedConnectorFactory()
    private context = IntegrationTestBuildContext.INSTANCE

    private final List<Closure> connectorConfigurers = []
    boolean verboseLogging = LOGGER.debugEnabled
    private final OutputStream stdout
    private final OutputStream stderr

    ToolingApi(GradleDistribution dist, TestDirectoryProvider testWorkDirProvider, OutputStream stdout = System.out, OutputStream stderr = System.err) {
        this.stderr = stderr
        this.stdout = stdout
        this.dist = dist
        this.useSeparateDaemonBaseDir = DefaultGradleConnector.metaClass.respondsTo(null, "daemonBaseDir")
        this.gradleUserHomeDir = context.gradleUserHomeDir
        this.daemonBaseDir = context.daemonBaseDir
        this.requiresDaemon = !IntegrationTestBuildContext.embedded
        this.testWorkDirProvider = testWorkDirProvider
    }

    void setDist(GradleDistribution dist) {
        this.dist = dist
    }

    GradleDistribution getDistribution() {
        return dist
    }

    /**
     * Specifies that the test use its own Gradle user home dir and daemon registry.
     *
     * @return the user home directory that is used by the test
     */
    TestFile requireIsolatedUserHome() {
        TestFile dir = testWorkDirProvider.testDirectory.file("user-home-dir")
        withUserHome(dir)
        return dir
    }

    void withUserHome(TestFile userHomeDir) {
        gradleUserHomeDir = userHomeDir
        useSeparateDaemonBaseDir = false
    }

    TestFile getDaemonBaseDir() {
        return useSeparateDaemonBaseDir ? daemonBaseDir : gradleUserHomeDir.file("daemon")
    }

    /**
     * Makes the fixture use a new connector factory that is independent from the shared one
     * that is normally used by users via {@link GradleConnector#newConnector()}.
     * <p>
     * IMPORTANT: Clean up the resources either by explicitly calling {@link #close()} when done
     * or by using the fixture according to the {@link TestRule} contract.
     * <p>
     * Some tests require that the tooling client be closed or reset,
     * either to verify the behaviour on close or to ensure that certain sticky state is reset.
     * The shared connector factory is backed by static state and can cause interference between the tests.
     */
    void requireIsolatedToolingApi() {
        requireIsolatedDaemons()
        connectorFactory = createManagedConnectorFactory()
    }

    /**
     * Creates a connector factory in a cross-version compatible way.
     */
    private static ConnectorFactory createManagedConnectorFactory() {
        def currentVersion = GradleVersion.current().baseVersion
        if (GradleVersion.version("9.0") <= currentVersion) {
            return new GradleConnectorFactoryWrapper(ConnectorServices.createConnectorFactory())
        }
        if (GradleVersion.version("8.9") < currentVersion) {
            //noinspection GroovyAccessibility
            ServiceRegistry connectorServices = ConnectorServices.ConnectorServiceRegistry.create()
            return new ServiceRegistryBackedConnectorFactory(connectorServices)
        }
        // In Gradle <=8.9, ConnectorServiceRegistry directly implemented a ServiceRegistry
        //noinspection GroovyAccessibility
        ServiceRegistry connectorServices = new ConnectorServices.ConnectorServiceRegistry() as ServiceRegistry
        return new ServiceRegistryBackedConnectorFactory(connectorServices)
    }

    void close() {
        if (connectorFactory instanceof ManagedConnectorFactory) {
            connectorFactory.close()
        }
    }

    /**
     * Specifies that the test use real daemon processes (not embedded) and a test-specific daemon registry.
     * <p>
     * Uses a shared Gradle user home dir
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

    void withConnector(@DelegatesTo(GradleConnector) Closure cl) {
        connectorConfigurers << cl
    }

    <T> T withConnection(@DelegatesTo(value = ProjectConnection, strategy = Closure.DELEGATE_FIRST) @ClosureParams(value = SimpleType, options = ["org.gradle.tooling.ProjectConnection"]) Closure<T> cl) {
        def connector = connector()
        withConnection(connector, cl)
    }

    <T> T withConnection(ToolingApiConnector connector, @DelegatesTo(value = ProjectConnection, strategy = Closure.DELEGATE_FIRST) @ClosureParams(value = SimpleType, options = ["org.gradle.tooling.ProjectConnection"]) Closure<T> cl) {
        try (def connection = connector.connect()) {
            return connection.with(cl)
        } catch (Throwable t) {
            validate(t)
            throw t
        }
    }

    private validate(Throwable throwable) {
        if (dist.version != GradleVersion.current()) {
            return
        }

        // Verify that the exception carries the calling thread's stack information
        def currentThreadStack = Thread.currentThread().stackTrace as List
        while (!currentThreadStack.empty && (currentThreadStack[0].className != ToolingApi.name || currentThreadStack[0].methodName != 'withConnection')) {
            currentThreadStack.remove(0)
        }
        assert currentThreadStack.size() > 1
        currentThreadStack.remove(0)
        String currentThreadStackStr = currentThreadStack.join("\n")

        def throwableStack = throwable.stackTrace.join("\n")

        assert throwableStack.endsWith(currentThreadStackStr)
    }

    ToolingApiConnector connector() {
        connector(testWorkDirProvider.testDirectory, true)
    }

    ToolingApiConnector connectorWithoutOutputRedirection() {
        connector(testWorkDirProvider.testDirectory, false)
    }

    ToolingApiConnector connector(TestFile projectDir) {
        connector(projectDir, true)
    }

    /**
     * Return a wrapper around a {@link GradleConnector} that delegates to the connector
     * and captures stdout and stderr.
     * <p>
     * Optionally, stdout and stderr can be redirected to the system streams so they are visible
     * in the console.
     */
    ToolingApiConnector connector(TestFile projectDir, boolean redirectOutput) {
        GradleConnector connector = rawConnector(projectDir)

        OutputStream output = stdout
        OutputStream error = stderr

        if (redirectOutput) {
            output = new TeeOutputStream(stdout, System.out)
            error = new TeeOutputStream(stderr, System.err)
        }

        return new ToolingApiConnector(connector, output, error)
    }

    /**
     * Get a {@link GradleConnector} that is not wrapped to forward stdout and stderr.
     * <p>
     * In general, prefer {@link #connector(TestFile)}. This method should be used when
     * interfacing with production code that is not {@link ToolingApiConnector}-aware.
     *
     * TODO: Can we get rid of this and have ToolingApiConnector implement GradleConnector?
     */
    GradleConnector rawConnector(TestFile projectDir = testWorkDirProvider.testDirectory) {
        DefaultGradleConnector connector = createConnector()

        connector.forProjectDirectory(projectDir)

        connector.useInstallation(dist.gradleHomeDir.absoluteFile)
        connector.embedded(embedded)

        if (GradleVersion.version(dist.getVersion().version) < GradleVersion.version("6.0")) {
            connector.searchUpwards(false)
        } else {
            // buildSrc builds are allowed to be missing their settings file
            if (projectDir.name != "buildSrc") {
                def settingsFile = projectDir.file('settings.gradle')
                def settingsFileKts = projectDir.file('settings.gradle.kts')
                def settingsFileDcl = projectDir.file('settings.gradle.dcl')
                assert (settingsFile.exists() || settingsFileKts.exists() || settingsFileDcl.exists()): "the build must have a settings file"
            }
        }
        if (useSeparateDaemonBaseDir) {
            connector.daemonBaseDir(new File(daemonBaseDir.path))
        }
        connector.daemonMaxIdleTime(120, TimeUnit.SECONDS)
        if (connector.metaClass.hasProperty(connector, 'verboseLogging')) {
            connector.verboseLogging = verboseLogging
        }

        if (gradleUserHomeDir != context.gradleUserHomeDir) {
            // When using an isolated user home, first initialise the Gradle instance using the default user home dir
            // This sets some static state that uses files from the user home dir, such as DLLs
            connector.useGradleUserHomeDir(new File(context.gradleUserHomeDir.path))

            try (def connection = connector.connect()) {
                connection.getModel(BuildEnvironment.class)
            }
        }

        isolateFromGradleOwnBuild(connector)

        connector.useGradleUserHomeDir(new File(gradleUserHomeDir.path))
        connectorConfigurers.each {
            connector.with(it)
        }

        return connector
    }

    private DefaultGradleConnector createConnector() {
        return connectorFactory.createConnector()
    }

    private void isolateFromGradleOwnBuild(DefaultGradleConnector connector) {
        // override the `user.dir` property in order to isolate tests from the Gradle directory
        try (def connection = connector.connect()) {
            connection.action(new SetWorkingDirectoryAction(testWorkDirProvider.testDirectory.absolutePath))
        }
    }

    /**
     * Only 'current->[some-version]' can run embedded.
     * If running '[other-version]->current' the other Gradle version does not know how to start Gradle from the embedded classpath.
     */
    boolean isEmbedded() {
        // Use in-process build when running tests in embedded mode and daemon is not required
        return IntegrationTestBuildContext.embedded && !requiresDaemon && GradleVersion.current() == dist.version
    }

    @Override
    Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            void evaluate() throws Throwable {
                try {
                    base.evaluate()
                } finally {
                    cleanUpIsolatedDaemonsAndServices()
                }
            }
        }
    }

    def cleanUpIsolatedDaemonsAndServices() {
        close()

        if (requireIsolatedDaemons) {
            try {
                getDaemons().killAll()
            } catch (RuntimeException ex) {
                //TODO once we figured out why pid from logfile can be null we should remove this again
                LOGGER.warn("Unable to kill daemon(s)", ex)
            }
        }
        if (gradleUserHomeDir != context.gradleUserHomeDir) {
            // When the user home directory is not the default for int tests, then the Gradle instance that was used during the test will still be holding some services open in the user home dir (this is by design), so kill off the Gradle instance that was used.
            // If we ran in embedded mode, shutdown the embedded services
            // If we used the daemons, kill the daemons
            // Either way, this is expensive
            if (embedded) {
                ConnectorServices.reset()
            } else {
                getDaemons().killAll()
            }
        }
    }

    /**
     * Abstracts connector creation across different Gradle versions
     */
    interface ConnectorFactory {

        // We expect the implementation rather than an interface as a return type,
        // because DefaultGradleConnector acts as a de-facto internal API for testing
        DefaultGradleConnector createConnector()
    }

    /**
     * Instance of a factory that is not shared and is created/destroyed in the scope of a test.
     */
    interface ManagedConnectorFactory extends ConnectorFactory {
        void close()
    }

    static class SharedConnectorFactory implements ConnectorFactory {
        @Override
        DefaultGradleConnector createConnector() {
            return GradleConnector.newConnector() as DefaultGradleConnector
        }
    }

    static class ServiceRegistryBackedConnectorFactory implements ManagedConnectorFactory {

        private ServiceRegistry serviceRegistry

        ServiceRegistryBackedConnectorFactory(ServiceRegistry serviceRegistry) {
            this.serviceRegistry = serviceRegistry
        }

        @Override
        DefaultGradleConnector createConnector() {
            // Before Gradle 9.0, there was ServiceRegistry.getFactory(Class<T> type): org.gradle.internal.Factory<T>
            return serviceRegistry.getFactory(DefaultGradleConnector).create()
        }

        @Override
        void close() {
            serviceRegistry.close()
        }
    }

    static class GradleConnectorFactoryWrapper implements ManagedConnectorFactory {

        private GradleConnectorFactory connectorFactory

        GradleConnectorFactoryWrapper(GradleConnectorFactory connectorFactory) {
            this.connectorFactory = connectorFactory
        }

        @Override
        DefaultGradleConnector createConnector() {
            return connectorFactory.createConnector() as DefaultGradleConnector
        }

        @Override
        void close() {
            connectorFactory.close()
        }
    }
}
