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

import org.gradle.internal.session.BuildSession
import spock.lang.Specification


class DefaultDeploymentRegistryTest extends Specification {
    BuildSession buildSession = Mock(BuildSession)
    DeploymentRegistry registry = new DefaultDeploymentRegistry(buildSession)

    def "can register and retrieve a deployment handle" () {
        DeploymentHandle handle = mockDeployment("test")

        when:
        registry.register(handle)

        then:
        registry.get(DeploymentHandle.class, "test") == handle
    }

    def "a new deployment registry is added to the build session" () {
        def newRegistry

        when:
        newRegistry = new DefaultDeploymentRegistry(buildSession)

        then:
        1 * buildSession.add(_)
    }

    def "can register a duplicate deployment handle" () {
        DeploymentHandle handle = mockDeployment("test")
        boolean newRegistration

        when:
        newRegistration = registry.register(handle)

        then:
        assert newRegistration

        when:
        newRegistration = registry.register(handle)

        then:
        noExceptionThrown()
        assert !newRegistration

        and:
        registry.get(DeploymentHandle.class, "test") == handle
    }

    def "stopping registry stops deployment handles" () {
        DeploymentHandle handle1 = mockDeployment("test1")
        DeploymentHandle handle2 = mockDeployment("test2")
        DeploymentHandle handle3 = mockDeployment("test3")
        registry.register(handle1)
        registry.register(handle2)
        registry.register(handle3)

        when:
        registry.stop()

        then:
        1 * handle1.stop()
        1 * handle2.stop()
        1 * handle3.stop()
    }

    def "stopping registry removes deployment handles from registry" () {
        DeploymentHandle handle1 = mockDeployment("test1")
        DeploymentHandle handle2 = mockDeployment("test2")
        DeploymentHandle handle3 = mockDeployment("test3")
        registry.register(handle1)
        registry.register(handle2)
        registry.register(handle3)

        when:
        registry.stop()

        then:
        registry.get(DeploymentHandle.class, "test1") == null
        registry.get(DeploymentHandle.class, "test2") == null
        registry.get(DeploymentHandle.class, "test3") == null
    }

    def mockDeployment(String id) {
        return Mock(DeploymentHandle) {
            _ * getId() >> id
        }
    }
}
