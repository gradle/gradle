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
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.logging.services.LoggingServiceRegistry
import org.gradle.internal.scan.config.BuildScanConfig
import org.gradle.internal.scan.config.BuildScanConfigProvider
import org.gradle.internal.scan.config.BuildScanPluginMetadata
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.scopes.BuildSessionScopeServices
import org.gradle.internal.service.scopes.ExecutionScopeServices
import org.gradle.internal.service.scopes.GlobalScopeServices
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry
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
    final def sessionServices = new BuildSessionScopeServices(userHomeServices, startParameter, ClassPath.EMPTY)
    final def executionServices = new ExecutionScopeServices(sessionServices)
    final def listenerManager = globalServices.get(ListenerManager)
    final def progressLoggerFactory = globalServices.get(ProgressLoggerFactory)
    final def userHomeScopeServiceRegistry = globalServices.get(GradleUserHomeScopeServiceRegistry)
    final def factory = new DefaultGradleLauncherFactory(listenerManager, progressLoggerFactory, userHomeScopeServiceRegistry)

    def cleanup() {
        executionServices.close()
        sessionServices.close()
        userHomeScopeServiceRegistry.release(userHomeServices)
        globalServices.close()
    }

    def "makes services from build context available as build scoped services"() {
        def cancellationToken = Stub(BuildCancellationToken)
        def eventConsumer = Stub(BuildEventConsumer)
        def requestContext = Stub(BuildRequestContext) {
            getCancellationToken() >> cancellationToken
            getEventConsumer() >> eventConsumer
        }

        expect:
        def launcher = factory.newInstance(startParameter, requestContext, executionServices)
        launcher.gradle.parent == null
        launcher.gradle.startParameter == startParameter
        launcher.gradle.services.get(BuildRequestMetaData) == requestContext
        launcher.gradle.services.get(BuildCancellationToken) == cancellationToken
        launcher.gradle.services.get(BuildEventConsumer) == eventConsumer
    }

    def "reuses build context services for nested build"() {
        def cancellationToken = Stub(BuildCancellationToken)
        def clientMetaData = Stub(BuildClientMetaData)
        def eventConsumer = Stub(BuildEventConsumer)
        def requestContext = Stub(BuildRequestContext) {
            getCancellationToken() >> cancellationToken
            getClient() >> clientMetaData
            getEventConsumer() >> eventConsumer
        }

        def parent = factory.newInstance(startParameter, requestContext, executionServices)
        parent.buildListener.buildStarted(parent.gradle)

        expect:
        def launcher = parent.gradle.services.get(NestedBuildFactory).nestedInstance(startParameter)
        launcher.gradle.parent == parent.gradle

        def request = launcher.gradle.services.get(BuildRequestMetaData)
        request instanceof DefaultBuildRequestMetaData
        request != requestContext
        request.client == clientMetaData
        launcher.gradle.services.get(BuildCancellationToken) == cancellationToken
        launcher.gradle.services.get(BuildEventConsumer) == eventConsumer
    }

    def "initializes build scan config"() {
        given:
        startParameter.setBuildScan(true)

        when:
        def launcher = factory.newInstance(startParameter, Stub(BuildRequestContext), executionServices)
        def c = buildScanConfig(launcher)

        then:
        c.enabled
        !c.disabled
    }

    def "marks BuildScanRequest as disabled when no build scan startparameter is set"() {
        given:
        startParameter.setNoBuildScan(true)

        when:
        def launcher = factory.newInstance(startParameter, Stub(BuildRequestContext), executionServices)
        def c = buildScanConfig(launcher)

        then:
        !c.enabled
        c.disabled
    }

    def "marks BuildScanRequest as neither enabled or disabled when no parameter is set"() {
        when:
        def launcher = factory.newInstance(startParameter, Stub(BuildRequestContext), executionServices)
        def c = buildScanConfig(launcher)

        then:
        !c.enabled
        !c.disabled
    }


    BuildScanConfig buildScanConfig(GradleLauncher launcher) {
        launcher.gradle.services.get(BuildScanConfigProvider).collect([getVersion: { "2.0" }] as BuildScanPluginMetadata)
    }
}
