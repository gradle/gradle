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

package org.gradle.language.base

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class AndroidProblem28rc1IntegrationTest extends AbstractIntegrationSpec {
    def "Android problem with 2.8-rc1"() {
        buildFile << """

@Managed
interface MyModel {
    String value
}

// Define some binary and component types.
interface SampleBinarySpec extends BinarySpec {}
class DefaultSampleBinary extends BaseBinarySpec implements SampleBinarySpec {}

interface SampleComponent extends ComponentSpec{}
class DefaultSampleComponent extends BaseComponentSpec implements SampleComponent {}

class MyPlugin implements Plugin<Project> {
    public void apply(Project project) {
        project.plugins.apply(ComponentModelBasePlugin)
    }

    static class Rules extends RuleSource {
        @Model
        void createManagedModel(MyModel value) {
            println "createManagedModel"
        }

        @ComponentType
        void registerComponent(ComponentTypeBuilder<SampleComponent> builder) {
            println "registerComponent"
            builder.defaultImplementation(DefaultSampleComponent)
        }
    }

}

apply plugin: MyPlugin
"""
        expect:
        succeeds "components"
    }
}
