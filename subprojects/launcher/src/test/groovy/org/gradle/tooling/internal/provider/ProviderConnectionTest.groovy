/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.tooling.internal.provider

import org.gradle.tooling.events.OperationType
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener
import org.gradle.tooling.internal.provider.connection.ProviderOperationParameters
import org.gradle.util.GradleVersion
import spock.lang.Specification

class ProviderConnectionTest extends Specification {

    def "ignores unknown operation types"() {
        given:
        def parameters = Stub(ProviderOperationParameters) {
            getBuildProgressListener() >> Stub(InternalBuildProgressListener) {
                getSubscribedOperations() >> ["UNKNOWN_OPERATION"]
            }
        }

        when:
        def configuration = ProviderConnection.ProgressListenerConfiguration.from(parameters, GradleVersion.version("12.7"))

        then:
        !configuration.clientSubscriptions.anyOperationTypeRequested
    }

    def "adds specific types when generic type requested by old consumer versions"() {
        given:
        def parameters = Stub(ProviderOperationParameters) {
            getBuildProgressListener() >> Stub(InternalBuildProgressListener) {
                getSubscribedOperations() >> ["BUILD_EXECUTION"]
            }
        }

        when:
        def configuration = ProviderConnection.ProgressListenerConfiguration.from(parameters, GradleVersion.version("5.0"))

        then:
        configuration.clientSubscriptions.anyOperationTypeRequested
        configuration.clientSubscriptions.isRequested(OperationType.GENERIC)
        configuration.clientSubscriptions.isRequested(OperationType.PROJECT_CONFIGURATION)
        configuration.clientSubscriptions.isRequested(OperationType.TRANSFORM)
        configuration.clientSubscriptions.isRequested(OperationType.WORK_ITEM)
        !configuration.clientSubscriptions.isRequested(OperationType.TEST)
        !configuration.clientSubscriptions.isRequested(OperationType.TASK)
    }

}
