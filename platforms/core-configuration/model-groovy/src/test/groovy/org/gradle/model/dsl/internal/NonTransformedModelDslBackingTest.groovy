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

import org.gradle.model.InvalidModelRuleDeclarationException
import org.gradle.model.Managed
import org.gradle.model.ModelSet
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.core.ModelRegistrations
import org.gradle.model.internal.core.ModelRuleExecutionException
import org.gradle.model.internal.core.ModelTypeInitializationException
import org.gradle.model.internal.fixture.ProjectRegistrySpec
import org.gradle.model.internal.type.ModelType

class NonTransformedModelDslBackingTest extends ProjectRegistrySpec {

    def modelDsl

    def setup() {
        modelDsl = new NonTransformedModelDslBacking(getRegistry())
    }

    void register(String pathString, Object element) {
        registry.register(ModelRegistrations.bridgedInstance(ModelReference.of(pathString, element.class), element).descriptor("register").build())
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
        registry.realize("foo", List) == [1]
    }

    @Managed
    interface Thing {
        void setName(String name)

        String getName()
    }

    @Managed
    interface Foo {
        ModelSet<Thing> getBar()
    }

    interface Unmanaged {}

    def "can create via DSL"() {
        when:
        modelDsl.configure {
            foo(Foo)
        }

        then:
        registry.get("foo", Foo).bar.empty
    }

    def "can only create top level"() {
        when:
        modelDsl.configure {
            foo.bar(Foo)
        }

        then:
        thrown InvalidModelRuleDeclarationException
    }

    def "can create and configure via DSL"() {
        when:
        modelDsl.configure {
            foo(Foo) {
                bar.create {
                    name = "one"
                }
            }
        }

        then:
        registry.get("foo", Foo).bar.first().name == "one"
    }

    def "cannot create unmanaged"() {
        when:
        modelDsl.configure {
            unmanaged(Unmanaged)
        }

        then:
        thrown ModelTypeInitializationException
    }

    def "can use property accessors in DSL to build model object path"() {
        when:
        modelDsl.configure {
            foo(Foo)
            foo.bar {
                create {
                    it.name = "foo"
                }
            }
        }

        then:
        registry.realize("foo", Foo).bar*.name == ["foo"]
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
        registry.realize("foo", ModelType.UNTYPED)

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
        registry.realize("bah", ModelType.UNTYPED)

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

