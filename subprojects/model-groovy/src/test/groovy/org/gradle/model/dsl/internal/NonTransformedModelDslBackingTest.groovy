/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.model.internal.core.*
import org.gradle.model.internal.registry.DefaultModelRegistry
import spock.lang.Specification

class NonTransformedModelDslBackingTest extends Specification {

    def modelRegistry = new DefaultModelRegistry()
    def modelDsl = new NonTransformedModelDslBacking(getModelRegistry())

    void register(String pathString, Object element) {
        modelRegistry.create(ModelCreators.of(ModelReference.of(pathString, element.class), element).simpleDescriptor("register").build())
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
        modelRegistry.get(ModelPath.path("foo"), ModelType.of(List)) == [1]
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
        modelRegistry.get(ModelPath.path("foo.bar"), ModelType.of(List)) == [1]
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
        modelRegistry.get(ModelPath.path("foo"), ModelType.UNTYPED)

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
        modelRegistry.get(ModelPath.path("bah"), ModelType.UNTYPED)

        then:
        e = thrown(ModelRuleExecutionException)
        def missingMethod = e.cause
        assert missingMethod instanceof MissingMethodException
        missingMethod.method == 'unknown'
    }

    static class TestObject {
        String prop

        def defineSomeThing(Closure cl) {
            cl.delegate = this
            cl.call()
        }
    }
}

