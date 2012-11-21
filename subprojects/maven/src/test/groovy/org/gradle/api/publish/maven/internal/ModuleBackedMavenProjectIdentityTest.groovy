/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.maven.internal

import org.gradle.api.artifacts.Module
import spock.lang.Specification

class ModuleBackedMavenProjectIdentityTest extends Specification {

    def "attributes"() {
        when:
        def module = new MutableModule(group: "group", name: "name", version: "version", status: "status")
        def identity = identity(module)

        then:
        identity.artifactId == "name"
        identity.groupId == "group"
        identity.version == "version"
        identity.packaging == null

        when:
        module.name = "changed-name"
        module.group = "changed-group"
        module.version = "changed-version"

        then:
        identity.artifactId == "changed-name"
        identity.groupId == "changed-group"
        identity.version == "changed-version"
        identity.packaging == null
    }

    MavenProjectIdentity identity(Module module) {
        new ModuleBackedMavenProjectIdentity(module)
    }

    private static class MutableModule implements Module {
        String name
        String group
        String version
        String status
    }
}
