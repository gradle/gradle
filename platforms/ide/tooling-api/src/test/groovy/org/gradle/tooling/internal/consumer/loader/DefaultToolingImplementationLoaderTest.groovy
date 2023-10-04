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
import org.gradle.internal.actor.ActorFactory
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.tooling.internal.consumer.ConnectionParameters
import org.gradle.tooling.internal.consumer.DefaultConnectionParameters
import org.gradle.tooling.internal.consumer.Distribution
import org.gradle.tooling.internal.consumer.connection.NoToolingApiConnection
import org.gradle.tooling.internal.consumer.connection.ParameterAcceptingConsumerConnection
import org.gradle.tooling.internal.consumer.connection.PhasedActionAwareConsumerConnection
import org.gradle.tooling.internal.consumer.connection.TestExecutionConsumerConnection
import org.gradle.tooling.internal.consumer.connection.UnsupportedOlderVersionConnection
import org.gradle.tooling.internal.protocol.BuildExceptionVersion1
import org.gradle.tooling.internal.protocol.BuildParameters
import org.gradle.tooling.internal.protocol.BuildResult
import org.gradle.tooling.internal.protocol.ConfigurableConnection
import org.gradle.tooling.internal.protocol.ConnectionMetaDataVersion1
import org.gradle.tooling.internal.protocol.ConnectionVersion4
import org.gradle.tooling.internal.protocol.InternalBuildAction
import org.gradle.tooling.internal.protocol.InternalBuildActionFailureException
import org.gradle.tooling.internal.protocol.InternalBuildActionVersion2
import org.gradle.tooling.internal.protocol.InternalBuildCancelledException
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener
import org.gradle.tooling.internal.protocol.InternalCancellableConnection
import org.gradle.tooling.internal.protocol.InternalCancellationToken
import org.gradle.tooling.internal.protocol.InternalParameterAcceptingConnection
import org.gradle.tooling.internal.protocol.InternalPhasedAction
import org.gradle.tooling.internal.protocol.InternalPhasedActionConnection
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException
import org.gradle.tooling.internal.protocol.ModelIdentifier
import org.gradle.tooling.internal.protocol.PhasedActionResultListener
import org.gradle.tooling.internal.protocol.ShutdownParameters
import org.gradle.tooling.internal.protocol.StoppableConnection
import org.gradle.tooling.internal.protocol.exceptions.InternalUnsupportedBuildArgumentException
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionConnection
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionRequest
import org.gradle.util.GradleVersion
import org.junit.Rule
import org.slf4j.Logger
import spock.lang.Specification

class DefaultToolingImplementationLoaderTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    Distribution distribution = Mock()
    ProgressLoggerFactory loggerFactory = Mock()
    InternalBuildProgressListener progressListener = Mock()
    ConnectionParameters connectionParameters = DefaultConnectionParameters.builder()
        .setProjectDir(tmpDir.testDirectory)
        .setVerboseLogging(true)
        .build()
    final BuildCancellationToken cancellationToken = Mock()
    final loader = new DefaultToolingImplementationLoader()

    def "locates connection implementation using meta-inf service then instantiates and configures the connection"() {
        given:
        distribution.getToolingImplementationClasspath(loggerFactory, progressListener, connectionParameters, cancellationToken) >> DefaultClassPath.of(
            getToolingApiResourcesDir(connectionImplementation),
            ClasspathUtil.getClasspathForClass(TestConnection.class),
            ClasspathUtil.getClasspathForClass(ActorFactory.class),
            ClasspathUtil.getClasspathForClass(Logger.class),
            ClasspathUtil.getClasspathForClass(GroovyObject.class),
            ClasspathUtil.getClasspathForClass(GradleVersion.class))

        when:
        def consumerConnection = loader.create(distribution, loggerFactory, progressListener, connectionParameters, cancellationToken).delegate

        then:
        consumerConnection.delegate.class != connectionImplementation //different classloaders
        consumerConnection.delegate.class.name == connectionImplementation.name
        consumerConnection.delegate.configured

        where:
        connectionImplementation | adapter
        TestConnection.class     | PhasedActionAwareConsumerConnection.class
        TestR44Connection.class  | ParameterAcceptingConsumerConnection.class
        TestR26Connection.class  | TestExecutionConsumerConnection.class
    }

    def "locates connection implementation using meta-inf service for deprecated connection"() {
        given:
        distribution.getToolingImplementationClasspath(loggerFactory, progressListener, connectionParameters, cancellationToken) >> DefaultClassPath.of(
            getToolingApiResourcesDir(connectionImplementation),
            ClasspathUtil.getClasspathForClass(TestConnection.class),
            ClasspathUtil.getClasspathForClass(ActorFactory.class),
            ClasspathUtil.getClasspathForClass(Logger.class),
            ClasspathUtil.getClasspathForClass(GroovyObject.class),
            ClasspathUtil.getClasspathForClass(GradleVersion.class))

        when:
        def adaptedConnection = loader.create(distribution, loggerFactory, progressListener, connectionParameters, cancellationToken)

        then:
        adaptedConnection.class == UnsupportedOlderVersionConnection.class

        where:
        connectionImplementation  | _
        TestR10M3Connection.class | _
        TestR10M8Connection.class | _
        TestR12Connection.class   | _
        TestR21Connection.class   | _
        TestR22Connection.class   | _
    }

    private getToolingApiResourcesDir(Class implementation) {
        tmpDir.file("META-INF/services/org.gradle.tooling.internal.protocol.ConnectionVersion4") << implementation.name
        return tmpDir.testDirectory;
    }

    def "creates broken connection when resource not found"() {
        def loader = new DefaultToolingImplementationLoader()

        given:
        distribution.getToolingImplementationClasspath(loggerFactory, progressListener, connectionParameters, cancellationToken) >> ClassPath.EMPTY

        expect:
        loader.create(distribution, loggerFactory, progressListener, connectionParameters, cancellationToken) instanceof NoToolingApiConnection
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

class TestConnection extends TestR44Connection implements InternalPhasedActionConnection {
    @Override
    BuildResult<?> run(InternalPhasedAction internalPhasedAction, PhasedActionResultListener listener, InternalCancellationToken cancellationToken, BuildParameters operationParameters)
        throws BuildExceptionVersion1, InternalUnsupportedBuildArgumentException, InternalBuildActionFailureException, InternalBuildCancelledException, IllegalStateException {
        throw new UnsupportedOperationException()
    }

    ConnectionMetaDataVersion1 getMetaData() {
        return new TestMetaData('4.8')
    }
}

class TestR44Connection extends TestR26Connection implements InternalParameterAcceptingConnection {
    @Override
    <T> BuildResult<T> run(InternalBuildActionVersion2<T> action, InternalCancellationToken cancellationToken, BuildParameters operationParameters)
        throws BuildExceptionVersion1, InternalUnsupportedBuildArgumentException, InternalBuildActionFailureException, IllegalStateException {
        throw new UnsupportedOperationException();
    }

    ConnectionMetaDataVersion1 getMetaData() {
        return new TestMetaData('4.4')
    }
}

class TestR26Connection extends TestR22Connection implements InternalTestExecutionConnection {
    @Override
    BuildResult<?> runTests(InternalTestExecutionRequest testExecutionRequest, InternalCancellationToken cancellationToken, BuildParameters operationParameters) {
        throw new UnsupportedOperationException()
    }

    ConnectionMetaDataVersion1 getMetaData() {
        return new TestMetaData('2.6')
    }
}

class TestR22Connection extends TestR21Connection implements StoppableConnection {
    @Override
    void shutdown(ShutdownParameters parameters) {
        throw new UnsupportedOperationException()
    }

    ConnectionMetaDataVersion1 getMetaData() {
        return new TestMetaData('2.2')
    }
}

class TestR21Connection extends TestR12Connection implements InternalCancellableConnection {
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
}

class TestR12Connection extends TestR10M8Connection implements ConfigurableConnection {
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
}

class TestR10M8Connection extends TestR10M3Connection {

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
}
