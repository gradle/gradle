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

package org.gradle.language.base.plugins


import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ComponentModelBasePluginIntegrationTest extends AbstractIntegrationSpec {

    def "creates default source set for component"() {
        given:
        buildFile << '''
            import org.gradle.api.NamedDomainObjectFactory
            import org.gradle.language.base.LanguageSourceSet
            import org.gradle.language.base.internal.SourceTransformTaskConfig
            import org.gradle.language.base.internal.registry.LanguageRegistration
            import org.gradle.language.base.internal.registry.LanguageTransform
            import org.gradle.language.base.sources.BaseLanguageSourceSet
            import org.gradle.platform.base.BinarySpec
            import org.gradle.platform.base.internal.*
            import org.gradle.platform.base.TransformationFileType
            import org.gradle.language.base.internal.registry.*
            import org.gradle.internal.reflect.*

            class TestComponent extends BaseComponentSpec {
                public Set<Class<? extends TransformationFileType>> getInputTypes() {
                    [TestTransformationFileType]
                }
            }

            class Rules extends RuleSource {
                @ComponentType
                void registerComponentType(ComponentTypeBuilder<ComponentSpecInternal> builder) {
                    builder.defaultImplementation(TestComponent)
                }

                @Mutate
                void registerLanguage(LanguageRegistry registry) {
                    registry.add(new TestLanguageRegistration())
                }

                @Mutate
                void registerLanguageTransformation(LanguageTransformContainer transforms) {
                    transforms.add(new TestLanguageTransformation())
                }

                @Mutate
                void addComponent(ComponentSpecContainer components) {
                    components.create("test", ComponentSpecInternal)
                }

                @Mutate
                void addValidateTask(ModelMap<Task> tasks, ComponentSpecContainer components) {
                    tasks.create("validate") {
                        doLast {
                            println "components.test.sources.test: ${components.test.sources.test.getClass().simpleName}"
                        }
                    }
                }
            }

            class TestLanguageRegistration implements LanguageRegistration {
                String getName() {
                    "test"
                }

                Class getSourceSetType() {
                    TestSourceSet
                }

                NamedDomainObjectFactory getSourceSetFactory(String parentName) {
                    { name -> BaseLanguageSourceSet.create(TestSourceSetImplementation, name, parentName, null, new DirectInstantiator()) }
                }
            }

            class TestLanguageTransformation implements LanguageTransform {
                Class getSourceSetType() {
                    TestSourceSet
                }

                Class getOutputType() {
                    TestTransformationFileType
                }

                Map<String, Class<?>> getBinaryTools() {
                    throw new UnsupportedOperationException()
                }

                SourceTransformTaskConfig getTransformTask() {
                    throw new UnsupportedOperationException()
                }

                boolean applyToBinary(BinarySpec binary) {
                    throw new UnsupportedOperationException()
                }
            }

            class TestTransformationFileType implements TransformationFileType {
            }


            interface TestSourceSet extends LanguageSourceSet {
            }

            class TestSourceSetImplementation extends BaseLanguageSourceSet implements TestSourceSet {
            }

            apply type: Rules
        '''

        when:
        succeeds "validate"

        then:
        output.contains("components.test.sources.test: TestSourceSetImplementation")
    }
}
