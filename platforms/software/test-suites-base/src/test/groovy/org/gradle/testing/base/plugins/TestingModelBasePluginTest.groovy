/*
 * Copyright 2016 the original author or authors.
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

/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.testing.base.plugins

import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.PlatformBaseSpecification
import org.gradle.testing.base.TestSuiteContainer
import org.gradle.testing.base.TestSuiteSpec

class TestingModelBasePluginTest extends PlatformBaseSpecification {
    TestSuiteContainer realizeTestSuites() {
        project.modelRegistry.find("testSuites", TestSuiteContainer)
    }

    def "registers TestSuiteSpec"() {
        when:
        dsl {
            apply plugin: TestingModelBasePlugin
            model {
                baseComponent(TestSuiteSpec) {
                }
            }
        }

        then:
        realize("baseComponent") instanceof TestSuiteSpec
    }

    def "adds a 'testSuites' container to the project model"() {
        when:
        dsl {
            apply plugin: TestingModelBasePlugin
        }

        then:
        realizeTestSuites() != null
    }

    def "links the binaries of each component in 'testSuites' container into the 'binaries' container"() {
        when:
        dsl {
            apply plugin: TestingModelBasePlugin
            model {
                testSuites {
                    comp1(TestSuiteSpec) {
                        binaries {
                            bin1(BinarySpec)
                            bin2(BinarySpec)
                        }
                    }
                    comp2(TestSuiteSpec)
                }
            }
        }

        then:
        def binaries = realizeBinaries()
        def components = realizeTestSuites()
        binaries.size() == 2
        binaries.comp1Bin1 == components.comp1.binaries.bin1
        binaries.comp1Bin2 == components.comp1.binaries.bin2
    }
}
