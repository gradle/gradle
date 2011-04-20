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

import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.internal.protocol.ConnectionVersion4
import org.gradle.tooling.model.Project
import org.gradle.tooling.model.Task
import org.gradle.util.ConcurrentSpecification

class DefaultProjectConnectionTest extends ConcurrentSpecification {
    final ConnectionVersion4 protocolConnection = Mock()
    final ProtocolToModelAdapter adapter = Mock()
    final DefaultProjectConnection connection = new DefaultProjectConnection(protocolConnection, adapter)

    def canCreateAModelBuilder() {
        expect:
        connection.model(Project.class) instanceof DefaultModelBuilder
    }

    def canCreateABuildLauncher() {
        expect:
        connection.newBuild() instanceof DefaultBuildLauncher
    }
    
    def modelFailsForUnknownModelType() {
        when:
        connection.model(TestBuild.class)

        then:
        UnsupportedVersionException e = thrown()
        e.message == 'Model of type \'TestBuild\' is not supported.'
    }

    def closeStopsBackingConnection() {
        when:
        connection.close()

        then:
        1 * protocolConnection.stop()
    }

    def getModelFailsWhenConnectionHasBeenStopped() {
        when:
        def builder = connection.model(Project.class)
        connection.close()
        builder.get()

        then:
        IllegalStateException e = thrown()
        e.message == 'This connection has been closed.'
        1 * protocolConnection.stop()
        0 * _._
    }

    def buildFailsWhenConnectionHasBeenStopped() {
        when:
        def build = connection.newBuild()
        connection.close()
        build.run()

        then:
        IllegalStateException e = thrown()
        e.message == 'This connection has been closed.'
        1 * protocolConnection.stop()
        0 * _._
    }

    def task(String path) {
        Task task = Mock()
        _ * task.path >> path
        return task
    }
}

interface TestBuild extends Project {
    
}
