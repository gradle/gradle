/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.model.dsl.internal

import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.core.ModelType
import org.gradle.model.internal.core.rule.Inputs
import org.gradle.model.internal.core.rule.ModelCreator
import org.gradle.model.internal.core.rule.ModelRuleExecutionException
import org.gradle.model.internal.core.rule.describe.ModelRuleSourceDescriptor
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleSourceDescriptor
import org.gradle.model.internal.registry.DefaultModelRegistry
import spock.lang.Specification

class GroovyModelDslTest extends Specification {

    def modelRegistry = new DefaultModelRegistry()
    def modelDsl = new GroovyModelDsl(getModelRegistry())

    void register(String path, Object element) {
        def reference = new ModelReference(new ModelPath(path), ModelType.of(element.class))
        modelRegistry.create(path, [], new ModelCreator() {
            @Override
            ModelReference getReference() {
                reference
            }

            @Override
            Object create(Inputs inputs) {
                return element
            }

            @Override
            ModelRuleSourceDescriptor getSourceDescriptor() {
                return new SimpleModelRuleSourceDescriptor("register")
            }
        })
    }

    def "can add rules via dsl"() {
        given:
        register("foo", [])

        when:
        modelDsl.configure {
            foo {
                add 1
            }
        }

        then:
        modelRegistry.get("foo", List) == [1]
    }

    def "can use property accessors in DSL to build model object path"() {
        given:
        register("foo.bar", [])

        when:
        modelDsl.configure {
            foo.bar {
                add 1
            }
        }

        then:
        modelRegistry.get("foo.bar", List) == [1]
    }

    def "does not add rules when not configuring"() {
        given:
        register("foo", new TestObject())
        register("bah", new TestObject())

        when:
        modelDsl.configure {
            foo {
                defineSomeThing {
                    unknown
                }
            }
        }
        modelRegistry.get("foo", Object)

        then:
        def e = thrown(ModelRuleExecutionException)
        def missingProp = e.cause
        missingProp instanceof MissingPropertyException
        missingProp.property == 'unknown'

        when:
        modelDsl.configure {
            bah {
                defineSomeThing {
                    unknown {}
                }
            }
        }
        modelRegistry.get("bah", Object)

        then:
        e = thrown(ModelRuleExecutionException)
        def missingMethod = e.cause
        assert missingMethod instanceof MissingMethodException
        missingMethod.method == 'unknown'
    }
}

class TestObject {
    String prop

    def defineSomeThing(Closure cl) {
        cl.delegate = this
        cl.call()
    }
}
