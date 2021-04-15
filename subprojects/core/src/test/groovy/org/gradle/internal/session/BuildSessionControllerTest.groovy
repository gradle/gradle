/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.session

import org.gradle.api.internal.StartParameterInternal
import org.gradle.initialization.BuildCancellationToken
import org.gradle.initialization.BuildClientMetaData
import org.gradle.initialization.BuildEventConsumer
import org.gradle.initialization.BuildRequestMetaData
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry
import spock.lang.Specification

class BuildSessionControllerTest extends Specification {
    def userHomeServiceRegistry = Mock(GradleUserHomeScopeServiceRegistry)
    def crossBuildState = Mock(CrossBuildSessionState)
    def startParameter = new StartParameterInternal()
    def buildRequestMetadata = Mock(BuildRequestMetaData)
    def clientMetadata = Mock(BuildClientMetaData)
    def cancellationToken = Mock(BuildCancellationToken)
    def eventConsumer = Mock(BuildEventConsumer)
    def classPath = ClassPath.EMPTY
    BuildSessionController state

    def setup() {
        _ * userHomeServiceRegistry.getServicesFor(_) >> new DefaultServiceRegistry()
        def services = new DefaultServiceRegistry()
        services.add(BuildSessionActionExecutor, Stub(BuildSessionActionExecutor))
        _ * crossBuildState.services >> services
        state = new BuildSessionController(userHomeServiceRegistry, crossBuildState, startParameter, buildRequestMetadata, classPath, cancellationToken, clientMetadata, eventConsumer)
    }

    def "cannot run multiple actions against a session"() {
        given:
        state.run {
            it.execute(Stub(BuildAction))
        }

        when:
        state.run {
            it.execute(Stub(BuildAction))
        }

        then:
        thrown(IllegalStateException)
    }

}
