/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.deployment.internal

import org.gradle.api.model.ObjectFactory
import org.gradle.internal.filewatch.PendingChangesManager
import org.gradle.internal.operations.TestBuildOperationExecutor
import spock.lang.Specification

class DefaultDeploymentRegistryTest extends Specification {
    static class TestDeploymentHandle implements DeploymentHandle {
        boolean running

        @Override
        void start(Deployment deploymentActivity) {
            running = true
        }

        @Override
        boolean isRunning() {
            return running
        }

        @Override
        void stop() {
            running = false
        }
    }

    static class ParametersDeploymentHandle extends TestDeploymentHandle {
        ParametersDeploymentHandle(String parameter) {
            assert parameter == "parameter"
        }
    }

    def objectFactory = Mock(ObjectFactory)
    def pendingChangesManager = Mock(PendingChangesManager)
    def buildOperationExecutor = new TestBuildOperationExecutor()
    def registry = new DefaultDeploymentRegistry(pendingChangesManager, buildOperationExecutor, objectFactory)

    def "creating registry registers with pending changes listener"() {
        when:
        def registry = new DefaultDeploymentRegistry(pendingChangesManager, buildOperationExecutor, objectFactory)
        then:
        1 * pendingChangesManager.addListener(_)
        when:
        registry.stop()
        then:
        1 * pendingChangesManager.removeListener(_)
    }

    def "can start a deployment with a given type and parameters and continuous build waits"() {
        def testHandle = new ParametersDeploymentHandle("parameter")
        objectFactory.newInstance(ParametersDeploymentHandle, "parameter") >> testHandle
        when:
        def handle = registry.start("id", ParametersDeploymentHandle, "parameter")
        then:
        assert handle == testHandle
        and:
        registry.get("id", ParametersDeploymentHandle) == testHandle
    }

    def "cannot register a duplicate deployment handle" () {
        def testHandle = new TestDeploymentHandle()
        objectFactory.newInstance(TestDeploymentHandle) >> testHandle
        when:
        registry.start("id", TestDeploymentHandle)
        then:
        noExceptionThrown()
        registry.get("id", TestDeploymentHandle) == testHandle

        when:
        registry.start("id", TestDeploymentHandle)
        then:
        IllegalStateException e = thrown()
        e.message == "A deployment with id 'id' is already registered."
    }

    def "stopping registry stops deployment handles" () {
        def testHandle = Mock(TestDeploymentHandle)
        objectFactory.newInstance(TestDeploymentHandle) >> testHandle
        testHandle.running >> true

        registry.start("id1", TestDeploymentHandle)
        registry.start("id2", TestDeploymentHandle)
        registry.start("id3", TestDeploymentHandle)

        when:
        registry.stop()
        then:
        3 * testHandle.stop()
    }

    def "cannot get a handle once the registry is stopped" () {
        objectFactory.newInstance(TestDeploymentHandle) >> new TestDeploymentHandle()
        given:
        registry.start("id", TestDeploymentHandle)
        registry.stop()

        when:
        registry.get("id", TestDeploymentHandle)
        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot modify deployment handles once the registry has been stopped."
    }

    def "cannot register a handle once the registry is stopped" () {
        given:
        registry.stop()

        when:
        registry.start("id", TestDeploymentHandle)
        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot modify deployment handles once the registry has been stopped."
    }
}
