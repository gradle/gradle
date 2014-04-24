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

import org.gradle.model.internal.DefaultModelRegistry
import org.gradle.model.internal.ModelRegistryBackedModelRules
import spock.lang.Ignore
import spock.lang.Specification

class GroovyModelDslTest extends Specification {

    def modelRegistry = new DefaultModelRegistry()
    def modelRules = new ModelRegistryBackedModelRules(modelRegistry)
    def modelDsl = new GroovyModelDsl(modelRules)

    def "can add rules via dsl"() {
        given:
        modelRules.register("foo", [])

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
        modelRules.register("foo.bar", [])

        when:
        modelDsl.configure {
            foo.bar {
                add 1
            }
        }

        then:
        modelRegistry.get("foo.bar", List) == [1]
    }

    @Ignore
    def "does not add rules when not configuring"() {
        given:
        modelRules.register("foo", new TestObject())
        modelRules.register("bah", new TestObject())

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
        MissingPropertyException missingProp = thrown()
        missingProp.property == 'unknown'

        when:
        modelDsl.configure {
            bah {
                defineSomeThing {
                    unknown { }
                }
            }
        }
        modelRegistry.get("bah", Object)

        then:
        MissingMethodException missingMethod = thrown()
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
