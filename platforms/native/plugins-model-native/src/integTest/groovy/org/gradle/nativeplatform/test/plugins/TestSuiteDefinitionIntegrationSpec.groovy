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

package org.gradle.nativeplatform.test.plugins

import org.gradle.api.reporting.model.ModelReportOutput
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.modes.UnsupportedWithConfigurationCache

@UnsupportedWithConfigurationCache(because = "software model")
class TestSuiteDefinitionIntegrationSpec extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
interface CustomTestSuite extends TestSuiteSpec {
}

class DefaultCustomTestSuite extends BaseComponentSpec implements CustomTestSuite {
    ComponentSpec testedComponent

    void testing(ComponentSpec component) { this.testedComponent = component }
}

interface CustomTestBinary extends TestSuiteBinarySpec {
    String getData()
}

class DefaultCustomTestBinary extends BaseBinarySpec implements CustomTestBinary {
    TestSuiteSpec testSuite
    BinarySpec testedBinary
    String data
}

@Managed interface CustomTestSourceSet extends LanguageSourceSet {}

class TestSuitePlugin extends RuleSource {
    @ComponentType
    void registerTestSuiteType(TypeBuilder<CustomTestSuite> builder) {
        builder.defaultImplementation(DefaultCustomTestSuite)
    }

    @ComponentType
    void registerBinaryType(TypeBuilder<CustomTestBinary> builder) {
        builder.defaultImplementation(DefaultCustomTestBinary)
    }

    @ComponentType
    void registerLanguageType(TypeBuilder<CustomTestSourceSet> builder) {
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
        expectSoftwareModelDeprecations("TestSuitePlugin", "org.gradle.language.base.plugins.ComponentModelBasePlugin", "org.gradle.language.base.plugins.LanguageBasePlugin", "org.gradle.nativeplatform.plugins.NativeComponentModelPlugin", "org.gradle.nativeplatform.test.plugins.NativeBinariesTestPlugin", "org.gradle.platform.base.plugins.BinaryBasePlugin", "org.gradle.platform.base.plugins.ComponentBasePlugin", "org.gradle.testing.base.plugins.TestingModelBasePlugin")
        run "model"

        then:
        def reportOutput = ModelReportOutput.from(output)
        reportOutput.hasNodeStructure {
            testSuites {
                unitTests {
                    binaries {
                        tests {
                            sources()
                            tasks()
                        }
                    }
                    sources {
                        tests()
                    }
                }
            }
        }
        reportOutput.hasNodeStructure {
            binaries {
                unitTestTests()
            }
        }

        when:
        expectSoftwareModelDeprecations("TestSuitePlugin", "org.gradle.language.base.plugins.ComponentModelBasePlugin", "org.gradle.language.base.plugins.LanguageBasePlugin", "org.gradle.nativeplatform.plugins.NativeComponentModelPlugin", "org.gradle.nativeplatform.test.plugins.NativeBinariesTestPlugin", "org.gradle.platform.base.plugins.BinaryBasePlugin", "org.gradle.platform.base.plugins.ComponentBasePlugin", "org.gradle.testing.base.plugins.TestingModelBasePlugin")
        run "check"

        then:
        noExceptionThrown()
    }

    def "plugin can define multiple test suites"() {
        buildFile << """

apply plugin: NativeBinariesTestPlugin
apply plugin: TestSuitePlugin

model {
    testSuites {
        unit(CustomTestSuite)
        functional(CustomTestSuite)
    }
}
"""

        when:
        expectSoftwareModelDeprecations("TestSuitePlugin", "org.gradle.language.base.plugins.ComponentModelBasePlugin", "org.gradle.language.base.plugins.LanguageBasePlugin", "org.gradle.nativeplatform.plugins.NativeComponentModelPlugin", "org.gradle.nativeplatform.test.plugins.NativeBinariesTestPlugin", "org.gradle.platform.base.plugins.BinaryBasePlugin", "org.gradle.platform.base.plugins.ComponentBasePlugin", "org.gradle.testing.base.plugins.TestingModelBasePlugin")
        run "model"

        then:
        def reportOutput = ModelReportOutput.from(output)
        reportOutput.hasNodeStructure {
            testSuites {
                functional {
                    binaries {
                        tests {
                            sources()
                            tasks()
                        }
                    }
                    sources {
                        tests()
                    }
                }
                unit {
                    binaries {
                        tests {
                            sources()
                            tasks()
                        }
                    }
                    sources {
                        tests()
                    }
                }
            }
        }
        reportOutput.hasNodeStructure {
            binaries {
                functionalTests()
                unitTests()
            }
        }

        when:
        expectSoftwareModelDeprecations("TestSuitePlugin", "org.gradle.language.base.plugins.ComponentModelBasePlugin", "org.gradle.language.base.plugins.LanguageBasePlugin", "org.gradle.nativeplatform.plugins.NativeComponentModelPlugin", "org.gradle.nativeplatform.test.plugins.NativeBinariesTestPlugin", "org.gradle.platform.base.plugins.BinaryBasePlugin", "org.gradle.platform.base.plugins.ComponentBasePlugin", "org.gradle.testing.base.plugins.TestingModelBasePlugin")
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
        expectSoftwareModelDeprecations("TestSuitePlugin", "org.gradle.language.base.plugins.ComponentModelBasePlugin", "org.gradle.language.base.plugins.LanguageBasePlugin", "org.gradle.nativeplatform.plugins.NativeComponentModelPlugin", "org.gradle.nativeplatform.test.plugins.NativeBinariesTestPlugin", "org.gradle.platform.base.plugins.BinaryBasePlugin", "org.gradle.platform.base.plugins.ComponentBasePlugin", "org.gradle.testing.base.plugins.TestingModelBasePlugin")
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
        expectSoftwareModelDeprecations("TestSuitePlugin", "org.gradle.language.base.plugins.ComponentModelBasePlugin", "org.gradle.language.base.plugins.LanguageBasePlugin", "org.gradle.nativeplatform.plugins.NativeComponentModelPlugin", "org.gradle.nativeplatform.test.plugins.NativeBinariesTestPlugin", "org.gradle.platform.base.plugins.BinaryBasePlugin", "org.gradle.platform.base.plugins.ComponentBasePlugin", "org.gradle.testing.base.plugins.TestingModelBasePlugin")
        run "assemble"

        then:
        noExceptionThrown()
    }

    private void expectSoftwareModelDeprecations(String... pluginNames) {
        for (String name : pluginNames) {
            executer.expectDocumentedDeprecationWarning("The ${name} plugin has been deprecated. This is scheduled to be removed in Gradle 10. Rule-based/software model plugins are no longer supported. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_software_model")
        }
    }
}
