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
package org.gradle.tooling.internal.consumer

import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor
import org.gradle.tooling.model.GradleProject
import spock.lang.Specification

class DefaultProjectConnectionTest extends Specification {
    final AsyncConsumerActionExecutor protocolConnection = Mock()
    final ConnectionParameters parameters = Stub() {
        getProjectDir() >> new File("foo")
    }
    final ProjectConnectionCloseListener listener = Mock()

    final DefaultProjectConnection connection = new DefaultProjectConnection(protocolConnection, parameters, listener)

    def canCreateAModelBuilder() {
        expect:
        connection.model(GradleProject.class) instanceof DefaultModelBuilder
    }

    def modelTypeMustBeAnInterface() {
        when:
        connection.model(String.class)

        then:
        IllegalArgumentException e = thrown()
        e.message == "Cannot fetch a model of type 'java.lang.String' as this type is not an interface."
    }

    def canCreateABuildLauncher() {
        expect:
        connection.newBuild() instanceof DefaultBuildLauncher
    }

    def closeStopsBackingConnection() {
        when:
        connection.close()

        then:
        1 * protocolConnection.stop()
        1 * listener.connectionClosed(connection)
    }

    def "can create phased build action builder"() {
        expect:
        connection.action() instanceof DefaultBuildActionExecuter.Builder
    }
}
