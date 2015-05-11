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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.EnableModelDsl
import org.gradle.util.TextUtil

class TestSuiteModelIntegrationSpec extends AbstractIntegrationSpec {

    def "setup"() {
        EnableModelDsl.enable(executer)

        buildScript """
            import org.gradle.api.internal.rules.*
            import org.gradle.internal.service.*
            import org.gradle.api.internal.project.*
            import org.gradle.internal.reflect.*
            import org.gradle.model.internal.core.rule.describe.*
            import org.gradle.platform.base.internal.*
            import org.gradle.language.base.internal.*
            import org.gradle.api.internal.file.*

            apply type: NativeBinariesTestPlugin

            interface CustomTestSuite extends TestSuiteSpec {}
            class DefaultCustomTestSuite extends BaseComponentSpec implements CustomTestSuite {
                ComponentSpec testedComponent
            }

            interface CustomLanguageSourceSet extends LanguageSourceSet {
                String getData();
            }
            class DefaultCustomLanguageSourceSet extends BaseLanguageSourceSet implements CustomLanguageSourceSet {
                final String data = "foo"
            }

            class TestSuiteTypeRules extends RuleSource {
                @Mutate
                public void registerCustomTestSuiteFactory(RuleAwareNamedDomainObjectFactoryRegistry<TestSuiteSpec> factoryRegistry, ServiceRegistry serviceRegistry,
                                                            final ProjectSourceSet projectSourceSet, final ProjectIdentifier projectIdentifier) {
                    final Instantiator instantiator = serviceRegistry.get(Instantiator.class)
                    final FileResolver fileResolver = serviceRegistry.get(FileResolver.class)
                    factoryRegistry.registerFactory(CustomTestSuite, new NamedDomainObjectFactory<CustomTestSuite>() {
                        public CustomTestSuite create(String suiteName) {
                            FunctionalSourceSet functionalSourceSet = instantiator.newInstance(DefaultFunctionalSourceSet, suiteName, instantiator, projectSourceSet)
                            functionalSourceSet.registerFactory(CustomLanguageSourceSet.class, new NamedDomainObjectFactory<CustomLanguageSourceSet>() {
                                public CustomLanguageSourceSet create(String name) {
                                    return BaseLanguageSourceSet.create(DefaultCustomLanguageSourceSet.class, name, suiteName, fileResolver, instantiator);
                                }
                            });
                            ComponentSpecIdentifier id = new DefaultComponentSpecIdentifier(projectIdentifier.getPath(), suiteName)
                            return BaseComponentSpec.create(DefaultCustomTestSuite, id, functionalSourceSet, instantiator)
                        }
                    }, new SimpleModelRuleDescriptor("TestSuiteTypeRules.registerCustomTestSuiteFactory()"))
                }
            }

            apply type: TestSuiteTypeRules

            model {
                testSuites {
                    main(CustomTestSuite)
                }
            }
        """
    }

    void withMainSourceSet() {
        buildFile << """
            model {
                testSuites {
                    main {
                        sources {
                            main(CustomLanguageSourceSet)
                        }
                    }
                }
            }
        """
    }

    def "test suite sources and binaries containers are visible in model report"() {
        when:
        succeeds "model"

        then:
        output.contains(TextUtil.toPlatformLineSeparators("""
    testSuites
        main
            binaries
            sources"""))
    }

    def "can reference sources container for a test suite in a rule"() {
        given:
        withMainSourceSet()
        buildFile << '''
            model {
                tasks {
                    create("printSourceNames") {
                        def sources = $("testSuites.main.sources")
                        doLast {
                            println "names: ${sources*.name}"
                        }
                    }
                }
            }
        '''

        when:
        succeeds "printSourceNames"

        then:
        output.contains "names: [main]"
    }

    def "test suite sources container elements are visible in model report"() {
        given:
        withMainSourceSet()
        buildFile << """
            model {
                testSuites {
                    main {
                        sources {
                            test(CustomLanguageSourceSet)
                        }
                    }
                    secondary(CustomTestSuite) {
                        sources {
                            test(CustomLanguageSourceSet)
                        }
                    }
                    foo(CustomTestSuite) {
                        sources {
                            bar(CustomLanguageSourceSet)
                        }
                    }
                }
            }
        """

        when:
        succeeds "model"

        then:
        output.contains(TextUtil.toPlatformLineSeparators("""
    testSuites
        foo
            binaries
            sources
                bar
        main
            binaries
            sources
                main
                test
        secondary
            binaries
            sources
                test"""))
    }

    def "can reference sources container elements in a rule"() {
        given:
        withMainSourceSet()
        buildFile << '''
            model {
                tasks {
                    create("printSourceDisplayName") {
                        def sources = $("testSuites.main.sources.main")
                        doLast {
                            println "sources display name: ${sources.displayName}"
                        }
                    }
                }
            }
        '''

        when:
        succeeds "printSourceDisplayName"

        then:
        output.contains "sources display name: DefaultCustomLanguageSourceSet 'main:main'"
    }

    def "can reference sources container elements using specialized type in a rule"() {
        given:
        withMainSourceSet()
        buildFile << '''
            class TaskRules extends RuleSource {
                @Mutate
                void addPrintSourceDisplayNameTask(ModelMap<Task> tasks, @Path("testSuites.main.sources.main") CustomLanguageSourceSet sourceSet) {
                    tasks.create("printSourceData") {
                        doLast {
                            println "sources data: ${sourceSet.data}"
                        }
                    }
                }
            }

            apply type: TaskRules
        '''

        when:
        succeeds "printSourceData"

        then:
        output.contains "sources data: foo"
    }

    def "cannot remove source sets"() {
        given:
        withMainSourceSet()
        buildFile << '''
            class SourceSetRemovalRules extends RuleSource {
                @Mutate
                void clearSourceSets(@Path("testSuites.main.sources") NamedDomainObjectCollection<LanguageSourceSet> sourceSets) {
                    sourceSets.clear()
                }

                @Mutate
                void closeMainComponentSourceSetsForTasks(ModelMap<Task> tasks, @Path("testSuites.main.sources") NamedDomainObjectCollection<LanguageSourceSet> sourceSets) {
                }
            }

            apply type: SourceSetRemovalRules
        '''

        when:
        fails()

        then:
        failureHasCause("This collection does not support element removal.")
    }
}
