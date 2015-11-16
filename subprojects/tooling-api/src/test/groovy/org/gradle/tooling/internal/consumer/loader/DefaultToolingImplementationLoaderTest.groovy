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
package org.gradle.tooling.internal.consumer.loader

import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.logging.ProgressLoggerFactory
import org.gradle.messaging.actor.ActorFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.tooling.internal.consumer.ConnectionParameters
import org.gradle.tooling.internal.consumer.Distribution
import org.gradle.tooling.internal.consumer.connection.*
import org.gradle.tooling.internal.protocol.*
import org.gradle.tooling.internal.protocol.exceptions.InternalUnsupportedBuildArgumentException
import org.gradle.util.GradleVersion
import org.junit.Rule
import org.slf4j.Logger
import spock.lang.Specification

class DefaultToolingImplementationLoaderTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    Distribution distribution = Mock()
    ProgressLoggerFactory loggerFactory = Mock()
    ConnectionParameters connectionParameters = Stub() {
        getVerboseLogging() >> true
    }
    File userHomeDir = Mock()
    final BuildCancellationToken cancellationToken = Mock()
    final loader = new DefaultToolingImplementationLoader()

    def "locates connection implementation using meta-inf service then instantiates and configures the connection"() {
        given:
        distribution.getToolingImplementationClasspath(loggerFactory, userHomeDir, cancellationToken) >> new DefaultClassPath(
                getToolingApiResourcesDir(connectionImplementation),
                ClasspathUtil.getClasspathForClass(TestConnection.class),
                ClasspathUtil.getClasspathForClass(ActorFactory.class),
                ClasspathUtil.getClasspathForClass(Logger.class),
                ClasspathUtil.getClasspathForClass(GroovyObject.class),
                ClasspathUtil.getClasspathForClass(GradleVersion.class))

        when:
        def adaptedConnection = loader.create(distribution, loggerFactory, connectionParameters, cancellationToken)

        then:
        def consumerConnection = wrappedToNonCancellableAdapter ? adaptedConnection.delegate : adaptedConnection
        consumerConnection.delegate.class != connectionImplementation //different classloaders
        consumerConnection.delegate.class.name == connectionImplementation.name
        consumerConnection.delegate.configured

        and:
        wrappedToNonCancellableAdapter  || adaptedConnection.class == adapter
        !wrappedToNonCancellableAdapter || adaptedConnection.class == NonCancellableConsumerConnectionAdapter
        !wrappedToNonCancellableAdapter || adaptedConnection.delegate.class == adapter

        where:
        connectionImplementation  | adapter                                          | wrappedToNonCancellableAdapter
        TestConnection.class      | ShutdownAwareConsumerConnection.class            | false
        TestR21Connection.class   | CancellableConsumerConnection.class              | false
        TestR18Connection.class   | ActionAwareConsumerConnection.class              | true
        TestR16Connection.class   | ModelBuilderBackedConsumerConnection.class       | true
        TestR12Connection.class   | BuildActionRunnerBackedConsumerConnection.class  | true
        TestR10M8Connection.class | InternalConnectionBackedConsumerConnection.class | true
    }

    def "locates connection implementation using meta-inf service for deprecated connection"() {
        given:
        distribution.getToolingImplementationClasspath(loggerFactory, userHomeDir, cancellationToken) >> new DefaultClassPath(
                getToolingApiResourcesDir(TestR10M3Connection.class),
                ClasspathUtil.getClasspathForClass(TestConnection.class),
                ClasspathUtil.getClasspathForClass(ActorFactory.class),
                ClasspathUtil.getClasspathForClass(Logger.class),
                ClasspathUtil.getClasspathForClass(GroovyObject.class),
                ClasspathUtil.getClasspathForClass(GradleVersion.class))

        when:
        def adaptedConnection = loader.create(distribution, loggerFactory, connectionParameters, cancellationToken)

        then:
        adaptedConnection.class == UnsupportedOlderVersionConnection.class
    }

    private getToolingApiResourcesDir(Class implementation) {
        tmpDir.file("META-INF/services/org.gradle.tooling.internal.protocol.ConnectionVersion4") << implementation.name
        return tmpDir.testDirectory;
    }

    def "creates broken connection when resource not found"() {
        def loader = new DefaultToolingImplementationLoader()

        given:
        distribution.getToolingImplementationClasspath(loggerFactory, userHomeDir, cancellationToken) >> new DefaultClassPath()

        expect:
        loader.create(distribution, loggerFactory, connectionParameters, cancellationToken) instanceof NoToolingApiConnection
    }
}

class TestMetaData implements ConnectionMetaDataVersion1 {
    private final String version;

    TestMetaData(String version) {
        this.version = version
    }

    String getVersion() {
        return version
    }

    String getDisplayName() {
        throw new UnsupportedOperationException()
    }
}

class TestConnection extends TestR21Connection implements StoppableConnection {
    @Override
    void shutdown(ShutdownParameters parameters) {
        throw new UnsupportedOperationException()
    }

    ConnectionMetaDataVersion1 getMetaData() {
        return new TestMetaData('2.2')
    }
}

class TestR21Connection extends TestR18Connection implements InternalCancellableConnection {
    @Override
    BuildResult<?> getModel(ModelIdentifier modelIdentifier, InternalCancellationToken cancellationToken, BuildParameters operationParameters)
            throws BuildExceptionVersion1, InternalUnsupportedModelException, InternalUnsupportedBuildArgumentException, IllegalStateException {
        throw new UnsupportedOperationException();
    }

    @Override
    def <T> BuildResult<T> run(InternalBuildAction<T> action, InternalCancellationToken cancellationToken, BuildParameters operationParameters)
            throws BuildExceptionVersion1, InternalUnsupportedBuildArgumentException, InternalBuildActionFailureException, IllegalStateException {
        throw new UnsupportedOperationException();
    }

    ConnectionMetaDataVersion1 getMetaData() {
        return new TestMetaData('2.1')
    }
}

class TestR18Connection extends TestR16Connection implements InternalBuildActionExecutor {
    def <T> BuildResult<T> run(InternalBuildAction<T> action, BuildParameters operationParameters) throws BuildExceptionVersion1, InternalUnsupportedBuildArgumentException, IllegalStateException {
        throw new UnsupportedOperationException()
    }

    ConnectionMetaDataVersion1 getMetaData() {
        return new TestMetaData('1.8')
    }
}

class TestR16Connection extends TestR12Connection implements ModelBuilder {
    BuildResult<Object> getModel(ModelIdentifier modelIdentifier, BuildParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        throw new UnsupportedOperationException()
    }

    ConnectionMetaDataVersion1 getMetaData() {
        return new TestMetaData('1.6')
    }
}

class TestR12Connection extends TestR10M8Connection implements BuildActionRunner, ConfigurableConnection {
    void configure(org.gradle.tooling.internal.protocol.ConnectionParameters parameters) {
        configured = parameters.verboseLogging
    }

    @Override
    void configureLogging(boolean verboseLogging) {
        throw new UnsupportedOperationException()
    }

    ConnectionMetaDataVersion1 getMetaData() {
        return new TestMetaData('1.2')
    }

    def <T> BuildResult<T> run(Class<T> type, BuildParameters parameters) {
        throw new UnsupportedOperationException()
    }
}

class TestR10M8Connection extends TestR10M3Connection implements InternalConnection {
    def <T> T getTheModel(Class<T> type, BuildOperationParametersVersion1 operationParameters) {
        throw new UnsupportedOperationException()
    }

    void configureLogging(boolean verboseLogging) {
        configured = verboseLogging
    }

    ConnectionMetaDataVersion1 getMetaData() {
        return new TestMetaData('1.0-milestone-8')
    }
}

class TestR10M3Connection implements ConnectionVersion4 {
    boolean configured

    void configureLogging(boolean verboseLogging) {
        configured = verboseLogging
    }

    void stop() {
        throw new UnsupportedOperationException()
    }

    ConnectionMetaDataVersion1 getMetaData() {
        return new TestMetaData('1.0-milestone-3')
    }

    ProjectVersion3 getModel(Class<? extends ProjectVersion3> type, BuildOperationParametersVersion1 operationParameters) {
        throw new UnsupportedOperationException()
    }

    void executeBuild(BuildParametersVersion1 buildParameters, BuildOperationParametersVersion1 operationParameters) {
        throw new UnsupportedOperationException()
    }
}
