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

package org.gradle.nativeplatform

import org.gradle.api.reporting.model.ModelReportOutput
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TestSuiteDefinitionIntegrationSpec extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
interface CustomTestSuite extends TestSuiteSpec {
}

class DefaultCustomTestSuite extends BaseComponentSpec implements CustomTestSuite {
    ComponentSpec testedComponent
}

interface CustomTestBinary extends TestSuiteBinarySpec {
    String getData()
}

class DefaultCustomTestBinary extends BaseBinarySpec implements CustomTestBinary {
    TestSuiteSpec testSuite
    String data
}

interface CustomTestSourceSet extends LanguageSourceSet {
}

class DefaultCustomTestSourceSet extends BaseLanguageSourceSet implements CustomTestSourceSet {
}

class TestSuitePlugin extends RuleSource {
    @ComponentType
    void registerTestSuiteType(ComponentTypeBuilder<CustomTestSuite> builder) {
        builder.defaultImplementation(DefaultCustomTestSuite)
    }

    @BinaryType
    void registerBinaryType(BinaryTypeBuilder<CustomTestBinary> builder) {
        builder.defaultImplementation(DefaultCustomTestBinary)
    }

    @LanguageType
    void registerLanguageType(LanguageTypeBuilder<CustomTestSourceSet> builder) {
        builder.defaultImplementation(DefaultCustomTestSourceSet)
    }

    @Mutate
    void testSuiteDefaults(TestSuiteContainer testSuites) {
//        testSuites.withType(CustomTestSuite).beforeEach { suite ->
        testSuites.withType(CustomTestSuite) { suite ->
            suite.sources.create('tests', CustomTestSourceSet)
            suite.binaries.create('tests', CustomTestBinary)
        }
    }
}
"""
    }

    def "plugin can define custom test suite and attach source sets and binaries"() {
        buildFile << """

apply plugin: NativeBinariesTestPlugin
apply plugin: TestSuitePlugin

model {
    testSuites {
        unitTests(CustomTestSuite)
    }
}
"""

        when:
        run "model"

        then:
        ModelReportOutput.from(output).hasNodeStructure {
            testSuites {
                unitTests {
                    binaries {
                        tests {
                            tasks()
                        }
                    }
                    sources {
                        tests()
                    }
                }
            }
        }



        when:
        run "check"

        then:
        noExceptionThrown()
    }

    def "plugin can define a test suite as an output of the project"() {
        buildFile << """

apply plugin: NativeBinariesTestPlugin
apply plugin: TestSuitePlugin

model {
    components {
        unitTests(CustomTestSuite)
    }
}
"""

        when:
        run "model"

        then:
        ModelReportOutput.from(output).hasNodeStructure {
            components {
                unitTests {
                    binaries()
                    sources()

                }
            }
        }

        when:
        run "assemble"

        then:
        noExceptionThrown()
    }
}
