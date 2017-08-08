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

import org.gradle.BuildResult
import org.gradle.StartParameter
import org.gradle.api.model.ObjectFactory
import org.gradle.internal.filewatch.PendingChangesManager
import org.gradle.internal.operations.TestBuildOperationExecutor
import spock.lang.Specification

class DefaultDeploymentRegistryTest extends Specification {
    static class TestDeploymentHandle implements DeploymentHandle {
        TestDeploymentHandle(String parameter) {
            assert parameter == "parameter"
        }

        @Override
        void start(DeploymentActivity deploymentActivity) {
        }

        @Override
        boolean isRunning() {
            return false
        }

        @Override
        void outOfDate() {
        }

        @Override
        void upToDate(Throwable failure) {
        }

        @Override
        void stop() {
        }
    }

    def testHandle = Mock(DeploymentHandle)
    def objectFactory = Mock(ObjectFactory)
    def startParameter = Mock(StartParameter)
    def pendingChangesManager = Mock(PendingChangesManager)
    def buildOperationExecutor = new TestBuildOperationExecutor()
    def registry = new DefaultDeploymentRegistry(startParameter, pendingChangesManager, buildOperationExecutor, objectFactory)

    def setup() {
        objectFactory.newInstance(DeploymentHandle) >> testHandle
    }

    def "creating registry registers with pending changes listener"() {
        startParameter.continuous >> true
        when:
        def registry = new DefaultDeploymentRegistry(startParameter, pendingChangesManager, buildOperationExecutor, objectFactory)
        then:
        1 * pendingChangesManager.addListener(_)
        when:
        registry.stop()
        then:
        1 * pendingChangesManager.removeListener(_)
    }

    def "can start a deployment with a given type and parameters and continuous build waits"() {
        def testHandle = new TestDeploymentHandle("parameter")
        when:
        def handle = registry.start("id", TestDeploymentHandle, "parameter")
        then:
        objectFactory.newInstance(TestDeploymentHandle, "parameter") >> testHandle
        assert handle == testHandle
        and:
        registry.get("id", TestDeploymentHandle) == testHandle
    }

    def "notifies all handles when new build starts"() {
        (1..10).each {
            registry.start("id${it}", DeploymentHandle)
        }
        testHandle.running >> true
        def failure = new Throwable()

        when:
        registry.onPendingChanges()
        then:
        10 * testHandle.outOfDate()

        when:
        registry.buildFinished(new BuildResult(null, null))
        then:
        10 * testHandle.upToDate(null)


        when:
        registry.onPendingChanges()
        then:
        10 * testHandle.outOfDate()

        when:
        registry.buildFinished(new BuildResult(null, failure))
        then:
        10 * testHandle.upToDate(failure)
    }

    def "cannot register a duplicate deployment handle" () {
        when:
        registry.start("id", DeploymentHandle)
        then:
        noExceptionThrown()
        registry.get("id", DeploymentHandle) == testHandle

        when:
        registry.start("id", DeploymentHandle)
        then:
        IllegalStateException e = thrown()
        e.message == "A deployment with id 'id' is already registered."
    }

    def "stopping registry stops deployment handles" () {
        registry.start("id1", DeploymentHandle)
        registry.start("id2", DeploymentHandle)
        registry.start("id3", DeploymentHandle)
        testHandle.running >> true

        when:
        registry.stop()
        then:
        3 * testHandle.stop()
    }

    def "cannot get a handle once the registry is stopped" () {
        given:
        registry.start("id", DeploymentHandle)
        registry.stop()

        when:
        registry.get("id", DeploymentHandle)
        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot modify deployment handles once the registry has been stopped."
    }

    def "cannot register a handle once the registry is stopped" () {
        given:
        registry.stop()

        when:
        registry.start("id", DeploymentHandle)
        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot modify deployment handles once the registry has been stopped."
    }
}
