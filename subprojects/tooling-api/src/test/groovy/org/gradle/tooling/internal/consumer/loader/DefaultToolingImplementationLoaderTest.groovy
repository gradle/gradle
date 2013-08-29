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

import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.logging.ProgressLoggerFactory
import org.gradle.messaging.actor.ActorFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.tooling.internal.consumer.Distribution
import org.gradle.tooling.internal.consumer.connection.*
import org.gradle.tooling.internal.consumer.parameters.ConsumerConnectionParameters
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
    final loader = new DefaultToolingImplementationLoader()

    def "locates connection implementation using meta-inf service then instantiates and configures the connection"() {
        given:
        distribution.getToolingImplementationClasspath(loggerFactory) >> new DefaultClassPath(
                getToolingApiResourcesDir(connectionImplementation),
                ClasspathUtil.getClasspathForClass(TestConnection.class),
                ClasspathUtil.getClasspathForClass(ActorFactory.class),
                ClasspathUtil.getClasspathForClass(Logger.class),
                ClasspathUtil.getClasspathForClass(GroovyObject.class),
                getVersionResourcesDir(),
                ClasspathUtil.getClasspathForClass(GradleVersion.class))

        when:
        def adaptedConnection = loader.create(distribution, loggerFactory, new ConsumerConnectionParameters(true))

        then:
        adaptedConnection.delegate.class != connectionImplementation //different classloaders
        adaptedConnection.delegate.class.name == connectionImplementation.name
        adaptedConnection.delegate.configured

        and:
        adaptedConnection.class == adapter

        where:
        connectionImplementation  | adapter
        TestConnection.class      | ActionAwareConsumerConnection.class
        TestR16Connection.class   | ModelBuilderBackedConsumerConnection.class
        TestR12Connection.class   | BuildActionRunnerBackedConsumerConnection.class
        TestR10M8Connection.class | InternalConnectionBackedConsumerConnection.class
        TestR10M3Connection.class | ConnectionVersion4BackedConsumerConnection.class
    }

    private getToolingApiResourcesDir(Class implementation) {
        tmpDir.file("META-INF/services/org.gradle.tooling.internal.protocol.ConnectionVersion4") << implementation.name
        return tmpDir.testDirectory;
    }

    private getVersionResourcesDir() {
        return ClasspathUtil.getClasspathForResource(getClass().classLoader, "org/gradle/build-receipt.properties")
    }

    def "creates broken connection when resource not found"() {
        def loader = new DefaultToolingImplementationLoader()

        given:
        distribution.getToolingImplementationClasspath(loggerFactory) >> new DefaultClassPath()

        expect:
        loader.create(distribution, loggerFactory, new ConsumerConnectionParameters(true)) instanceof NoToolingApiConnection
    }
}

class TestMetaData implements ConnectionMetaDataVersion1 {
    String getVersion() {
        return "1.1"
    }

    String getDisplayName() {
        throw new UnsupportedOperationException()
    }
}

class TestConnection extends TestR16Connection implements InternalBuildActionExecutor {
    def <T> BuildResult<T> run(InternalBuildAction<T> action, BuildParameters operationParameters) throws BuildExceptionVersion1, InternalUnsupportedBuildArgumentException, IllegalStateException {
        throw new UnsupportedOperationException()
    }
}

class TestR16Connection extends TestR12Connection implements ModelBuilder {
    BuildResult<Object> getModel(ModelIdentifier modelIdentifier, BuildParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        throw new UnsupportedOperationException()
    }
}

class TestR12Connection extends TestR10M8Connection implements BuildActionRunner, ConfigurableConnection {
    void configure(ConnectionParameters parameters) {
        configured = parameters.verboseLogging
    }

    @Override
    void configureLogging(boolean verboseLogging) {
        throw new UnsupportedOperationException()
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
        return new TestMetaData()
    }

    ProjectVersion3 getModel(Class<? extends ProjectVersion3> type, BuildOperationParametersVersion1 operationParameters) {
        throw new UnsupportedOperationException()
    }

    void executeBuild(BuildParametersVersion1 buildParameters, BuildOperationParametersVersion1 operationParameters) {
        throw new UnsupportedOperationException()
    }
}
