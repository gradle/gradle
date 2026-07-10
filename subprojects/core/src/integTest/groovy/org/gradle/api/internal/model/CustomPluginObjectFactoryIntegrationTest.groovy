/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.model

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class CustomPluginObjectFactoryIntegrationTest extends AbstractIntegrationSpec {

    def "plugin can create unnamed instances of class using injected factory"() {
        buildFile """

            @groovy.transform.Canonical
            class CustomExtension {
                NestedExtension nested
                void nested(Action<? super NestedExtension> action) {
                    action.execute(nested)
                }
            }

            class NestedExtension {
                ObjectFactory factory

                @javax.inject.Inject
                NestedExtension(ObjectFactory of) {
                    factory = of
                }

                String name

                Person getRiker() {
                   factory.newInstance(Person, 'Riker')
                }

                void checkRiker(Action<? super Person> check) {
                    check.execute(getRiker())
                }
            }

            class Person {

                @javax.inject.Inject
                Person(String name) { this.name = name}

                String name
            }

            class CustomPlugin implements Plugin<Project> {
                ObjectFactory factory

                @javax.inject.Inject
                CustomPlugin(ObjectFactory objects) {
                    factory = objects
                }

                void apply(Project project) {
                    project.extensions.create('custom', CustomExtension, factory.newInstance(NestedExtension))
                }
            }

            apply plugin: CustomPlugin

            custom {
                nested {
                    name = 'foo'
                }
            }

            tasks.register('checkFoo') {
                def nested = project.extensions.custom.nested
                doLast {
                    assert nested.name == 'foo'
                    nested.checkRiker {
                        assert name == 'Riker'
                    }
                }
            }

"""

        when:
        run "checkFoo"

        then:
        noExceptionThrown()
    }



}
