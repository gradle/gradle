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

import spock.lang.Specification

class DefaultDeploymentRegistryTest extends Specification {
    DeploymentRegistry registry = new DefaultDeploymentRegistry()

    def "can register and retrieve a deployment handle" () {
        DeploymentHandle handle = Mock(DeploymentHandle)

        when:
        registry.register("test", handle)

        then:
        registry.get(DeploymentHandle.class, "test") == handle
    }

    def "cannot register a duplicate deployment handle" () {
        DeploymentHandle handle = Mock(DeploymentHandle)

        when:
        registry.register("test", handle)

        then:
        noExceptionThrown()
        registry.get(DeploymentHandle.class, "test") == handle

        when:
        registry.register("test", handle)

        then:
        IllegalStateException e = thrown()
        e.message == "A deployment with id 'test' is already registered."
    }

    def "stopping registry stops deployment handles" () {
        DeploymentHandle handle1 = Mock(DeploymentHandle)
        DeploymentHandle handle2 = Mock(DeploymentHandle)
        DeploymentHandle handle3 = Mock(DeploymentHandle)
        registry.register("test1", handle1)
        registry.register("test2", handle2)
        registry.register("test3", handle3)

        when:
        registry.stop()

        then:
        1 * handle1.stop()
        1 * handle2.stop()
        1 * handle3.stop()
    }

    def "stopping registry removes deployment handles from registry" () {
        DeploymentHandle handle1 = Mock(DeploymentHandle)
        DeploymentHandle handle2 = Mock(DeploymentHandle)
        DeploymentHandle handle3 = Mock(DeploymentHandle)
        registry.register("test1", handle1)
        registry.register("test2", handle2)
        registry.register("test3", handle3)

        when:
        registry.stop()

        then:
        registry.get(DeploymentHandle.class, "test1") == null
        registry.get(DeploymentHandle.class, "test2") == null
        registry.get(DeploymentHandle.class, "test3") == null
    }
}
