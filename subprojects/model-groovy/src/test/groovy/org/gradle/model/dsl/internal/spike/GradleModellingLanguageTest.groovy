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

package org.gradle.model.dsl.internal.spike

import org.gradle.model.dsl.internal.spike.fixture.GradleModellingLanguageCompilingTestClassLoader
import org.gradle.model.internal.core.ModelPath
import spock.lang.Specification

class GradleModellingLanguageTest extends Specification {

    ModelRegistry registry = new ModelRegistry()

    void buildScript(String script) {
        Class<Script> scriptClass = new GradleModellingLanguageCompilingTestClassLoader().parseClass(script)
        Script scriptInstance = scriptClass.newInstance()
        scriptInstance.binding.setVariable("modelRegistryHelper", new ModelRegistryDslHelper(registry))
        scriptInstance.run()
    }

    def getModelValueAt(String path) {
        registry.get(ModelPath.path(path))
    }

    void "simple top level assignment"() {
        when:
        buildScript """
            model {
                foo << { 2 }
            }
        """

        then:
        getModelValueAt("foo") == 2
    }

    void "simple top level assignment using sugared syntax"() {
        when:
        buildScript """
            model {
                foo = 2
                bar = 1 + 2
            }
        """

        then:
        getModelValueAt("foo") == 2
        getModelValueAt("bar") == 3
    }

    void "scoped assignments"() {
        given:
        registry.create(ModelPath.path("person"), ModelCreators.of(new Person()))

        when:
        buildScript """
            model {
                person {
                    firstName = "foo"
                    lastName = "bar"
                }
            }
        """

        then:
        def person = getModelValueAt("person")
        person.firstName == "foo"
        person.lastName == "bar"
    }

    void "multipart path assignment"() {
        given:
        registry.create(ModelPath.path("book"), ModelCreators.of(new Book()))
        registry.create(ModelPath.path("book.author"), ModelCreators.of(new Person()))

        when:
        buildScript """
            model {
                book {
                    author {
                        firstName = "foo"
                    }
                    author.lastName = "bar"
                }
            }
        """

        then:
        def book = getModelValueAt("book")
        book.author.firstName == "foo"
        book.author.lastName == "bar"
    }

    void "simple root based reference"() {
        when:
        buildScript '''
            model {
                p1 {
                    firstName = "foo"
                }
                p2 {
                    firstName = $.p1.firstName
                }
            }
        '''

        then:
        getModelValueAt("p2.firstName") == "foo"
    }

    void "root based reference used in an expression"() {
        when:
        buildScript '''
            model {
                p1 {
                    firstName = "foo"
                    lastName = "bar"
                }
                p2 {
                    firstName = $.p1.firstName.toUpperCase() + $.p1.lastName
                }
            }
        '''

        then:
        getModelValueAt("p2.firstName") == "FOObar"
    }

    void "transitive references via variable defined inside a value expression"() {
        given:
        registry.create(ModelPath.path("p1"), ModelCreators.resultOf { throw new Exception("this code should not be executed") })

        when:
        buildScript '''
            model {
                p1 {
                    firstName = "foo"
                    lastName = "bar"
                }
                p2 {
                    firstName << {
                        def p1 = $.p1
                        p1.firstName.toUpperCase() + p1.lastName
                    }
                }
            }
        '''

        then:
        getModelValueAt("p2.firstName") == "FOObar"
    }

    void "transitive references via variable defined outside of a value expression"() {
        given:
        registry.create(ModelPath.path("p1"), ModelCreators.resultOf { throw new Exception("this code should not be executed") })
        registry.create(ModelPath.path("p2"), ModelCreators.resultOf { throw new Exception("this code should not be executed") })

        when:
        buildScript '''
            model {
                p1 {
                    firstName = "foo"
                }
                p2 {
                    firstName = "bar"
                }
                def p = $.p1
                def pFirstName = p.firstName
                p3 {
                    firstName = p.firstName
                    lastName << {
                        def fromP1 = pFirstName.toUpperCase()
                        def p2 = $.p2
                        fromP1 + p2.firstName
                    }
                }
            }
        '''

        then:
        getModelValueAt("p3.firstName") == "foo"
        getModelValueAt("p3.lastName") == "FOObar"
    }
}

class Person {
    String firstName
    String lastName
}

class Book {
    String title
    Person author
}