/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.initialization

import org.gradle.StartParameter
import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.internal.BuildDefinition
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.logging.services.LoggingServiceRegistry
import org.gradle.internal.progress.BuildProgressLogger
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.scopes.BuildSessionScopeServices
import org.gradle.internal.service.scopes.BuildTreeScopeServices
import org.gradle.internal.service.scopes.CrossBuildSessionScopeServices
import org.gradle.internal.service.scopes.GlobalScopeServices
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry
import org.gradle.internal.time.Time
import org.gradle.plugin.management.internal.PluginRequests
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.junit.Rule
import spock.lang.Specification

class DefaultGradleLauncherFactoryTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def startParameter = new StartParameter()
    final def globalServices = new DefaultServiceRegistry(LoggingServiceRegistry.newEmbeddableLogging(), NativeServicesTestFixture.getInstance()).addProvider(new GlobalScopeServices(false))
    final def userHomeServices = globalServices.get(GradleUserHomeScopeServiceRegistry).getServicesFor(tmpDir.createDir("user-home"))
    final def crossBuildSessionScopeServices = new CrossBuildSessionScopeServices(globalServices, startParameter)
    final def sessionServices = new BuildSessionScopeServices(userHomeServices, crossBuildSessionScopeServices, startParameter, new DefaultBuildRequestMetaData(Time.currentTimeMillis()), ClassPath.EMPTY)
    final def buildTreeServices = new BuildTreeScopeServices(sessionServices)
    final def listenerManager = globalServices.get(ListenerManager)
    final def progressLoggerFactory = globalServices.get(ProgressLoggerFactory)
    final def userHomeScopeServiceRegistry = globalServices.get(GradleUserHomeScopeServiceRegistry)
    final def buildProgressLogger = globalServices.get(BuildProgressLogger)
    final def factory = new DefaultGradleLauncherFactory(userHomeScopeServiceRegistry, buildProgressLogger, crossBuildSessionScopeServices)
    def requestContext = Stub(BuildRequestContext)

    def cleanup() {
        buildTreeServices.close()
        sessionServices.close()
        userHomeScopeServiceRegistry.release(userHomeServices)
        globalServices.close()
    }

    def "makes services from build context available as build scoped services"() {
        def cancellationToken = Stub(BuildCancellationToken)
        def eventConsumer = Stub(BuildEventConsumer)
        requestContext.cancellationToken >> cancellationToken
        requestContext.eventConsumer >> eventConsumer

        expect:
        def launcher = factory.newInstance(BuildDefinition.fromStartParameter(startParameter), Stub(BuildIdentifier), requestContext, buildTreeServices)
        launcher.gradle.parent == null
        launcher.gradle.startParameter == startParameter
        launcher.gradle.services.get(BuildRequestMetaData) == requestContext
        launcher.gradle.services.get(BuildCancellationToken) == cancellationToken
        launcher.gradle.services.get(BuildEventConsumer) == eventConsumer
    }

    def "makes build definition services available as build scoped services"() {

        def identifier = Stub(BuildIdentifier)
        def buildDefinition = BuildDefinition.fromStartParameter(startParameter)

        expect:
        def launcher = factory.newInstance(buildDefinition, identifier, requestContext, buildTreeServices)
        launcher.gradle.parent == null
        launcher.gradle.startParameter == startParameter
        launcher.gradle.services.get(BuildDefinition) == buildDefinition
        launcher.gradle.services.get(BuildIdentity).currentBuild == identifier
    }

    def "reuses build context services for nested build"() {
        def cancellationToken = Stub(BuildCancellationToken)
        def clientMetaData = Stub(BuildClientMetaData)
        def eventConsumer = Stub(BuildEventConsumer)
        requestContext.eventConsumer >> eventConsumer
        requestContext.client >> clientMetaData
        requestContext.cancellationToken >> cancellationToken

        def parent = factory.newInstance(BuildDefinition.fromStartParameter(startParameter), Stub(BuildIdentifier), requestContext, buildTreeServices)
        parent.buildListener.buildStarted(parent.gradle)

        expect:
        def launcher = parent.gradle.services.get(NestedBuildFactory).nestedInstance(BuildDefinition.fromStartParameterForBuild(startParameter, tmpDir.file("nested"), Stub(PluginRequests)), Stub(BuildIdentifier))
        launcher.gradle.parent == parent.gradle

        def request = launcher.gradle.services.get(BuildRequestMetaData)
        request instanceof DefaultBuildRequestMetaData
        request != requestContext
        request.client == clientMetaData
        launcher.gradle.services.get(BuildCancellationToken) == cancellationToken
        launcher.gradle.services.get(BuildEventConsumer) == eventConsumer
    }

}
