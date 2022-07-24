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

package org.gradle.api.internal.artifacts

import org.gradle.test.fixtures.AbstractProjectBuilderSpec

class ProjectBackedModuleTest extends AbstractProjectBuilderSpec {

    def "module exposes project properties"() {
        given:
        def module = new ProjectBackedModule(project)

        expect:
        module.name == project.name
        module.group == project.group.toString()
        module.version == project.version.toString()
        module.status == project.status.toString()
        module.projectId == project.owner.componentIdentifier

        when:
        project.group = "fo${1}o"
        project.version = "fo${2}o"
        project.status = "fo${3}o"

        then:
        module.group == "fo1o"
        module.version == "fo2o"
        module.status == "fo3o"
    }
}
