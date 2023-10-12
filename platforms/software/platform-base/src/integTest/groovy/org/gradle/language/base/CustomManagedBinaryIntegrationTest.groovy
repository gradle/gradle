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
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

@UnsupportedWithConfigurationCache(because = "software model")
class CustomManagedBinaryIntegrationTest extends AbstractIntegrationSpec {
    def "setup"() {
        buildFile << """
@Managed
interface SampleBinary extends BinarySpec {
    String getVersion()
    void setVersion(String version)
}
"""
    }

    def "custom managed binary type can be registered and created"() {
        when:
        buildWithCustomBinaryPlugin()

        and:
        buildFile << '''
model {
    tasks {
        checkModel(Task) {
            doLast {
                def binaries = $.binaries
                assert binaries.size() == 1
                def sampleBinary = binaries.sampleBinary
                assert sampleBinary instanceof SampleBinary
                assert sampleBinary.displayName == "SampleBinary 'sampleBinary'"
                assert sampleBinary.buildable
            }
        }
    }
}
'''
        then:
        succeeds "checkModel"
    }

    def "can configure managed binary defined by rule method using rule DSL"() {
        when:
        buildWithCustomBinaryPlugin()

        and:
        buildFile << '''
model {
    tasks {
        checkModel(Task) {
            doLast {
                def binaries = $.binaries
                assert binaries.size() == 1
                def sampleBinary = binaries.sampleBinary
                assert sampleBinary instanceof SampleBinary
                assert sampleBinary.version == '1.2'
                assert sampleBinary.displayName == "SampleBinary 'sampleBinary'"
            }
        }
    }
}

model {
    binaries {
        sampleBinary {
            version = '1.2'
        }
    }
}
'''
        then:
        succeeds "checkModel"
    }

    def "creates lifecycle task per binary"() {
        when:
        buildWithCustomBinaryPlugin()
        then:
        succeeds "sampleBinary"
    }

    def "additional managed binaries listed in components report"() {
        given:
        buildWithCustomBinaryPlugin()
        when:
        succeeds "components"
        then:
        output.contains """> Task :components

------------------------------------------------------------
Root project 'custom-binary'
------------------------------------------------------------

No components defined for this project.

Additional binaries
-------------------
SampleBinary 'sampleBinary'
    build using task: :sampleBinary

Note: currently not all plugins register their components, so some components may not be visible here.

BUILD SUCCESSFUL"""
    }

    def buildWithCustomBinaryPlugin() {
        settingsFile << """rootProject.name = 'custom-binary'"""
        buildFile << """
        class MySamplePlugin implements Plugin<Project> {
            void apply(final Project project) {}

            static class Rules extends RuleSource {
                @ComponentType
                void register(TypeBuilder<SampleBinary> builder) {
                }

                @Mutate
                void createSampleBinary(BinaryContainer binaries) {
                    binaries.create("sampleBinary", SampleBinary)
                }
            }
        }

        apply plugin:MySamplePlugin
        """
    }

}
