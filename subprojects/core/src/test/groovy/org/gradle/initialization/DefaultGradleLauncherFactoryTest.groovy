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
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.BuildSessionScopeServices
import org.gradle.internal.service.scopes.GlobalScopeServices
import org.gradle.logging.LoggingServiceRegistry
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import spock.lang.Specification

class DefaultGradleLauncherFactoryTest extends Specification {
    def startParameter = new StartParameter()
    final ServiceRegistry globalServices = new DefaultServiceRegistry(LoggingServiceRegistry.newEmbeddableLogging(), NativeServicesTestFixture.getInstance()).addProvider(new GlobalScopeServices(false))
    final ServiceRegistry sessionServices = new BuildSessionScopeServices(globalServices, startParameter)
    final DefaultGradleLauncherFactory factory = new DefaultGradleLauncherFactory(globalServices)

    def "makes services from build context available as build scoped services"() {
        def cancellationToken = Stub(BuildCancellationToken)
        def eventConsumer = Stub(BuildEventConsumer)
        def requestContext = Stub(BuildRequestContext) {
            getCancellationToken() >> cancellationToken
            getEventConsumer() >> eventConsumer
        }

        expect:
        def launcher = factory.newInstance(startParameter, requestContext, sessionServices)
        launcher.gradle.parent == null
        launcher.gradle.startParameter == startParameter
        launcher.gradle.services.get(BuildRequestMetaData) == requestContext
        launcher.gradle.services.get(BuildCancellationToken) == cancellationToken
        launcher.gradle.services.get(BuildEventConsumer) == eventConsumer
    }

    def "provides default build context when no outer build is running"() {
        expect:
        def launcher = factory.newInstance(startParameter)
        launcher.gradle.parent == null
        launcher.gradle.services.get(BuildRequestMetaData) instanceof DefaultBuildRequestMetaData
        launcher.gradle.services.get(BuildCancellationToken) instanceof DefaultBuildCancellationToken
        launcher.gradle.services.get(BuildEventConsumer) instanceof NoOpBuildEventConsumer
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

        def parent = factory.newInstance(startParameter, requestContext, sessionServices);
        parent.buildListener.buildStarted(parent.gradle)

        expect:
        def launcher = factory.newInstance(startParameter)
        launcher.gradle.parent == parent.gradle
        def request = launcher.gradle.services.get(BuildRequestMetaData)
        request instanceof DefaultBuildRequestMetaData
        request != requestContext
        request.client == clientMetaData
        launcher.gradle.services.get(BuildCancellationToken) == cancellationToken
        launcher.gradle.services.get(BuildEventConsumer) == eventConsumer
    }
}
