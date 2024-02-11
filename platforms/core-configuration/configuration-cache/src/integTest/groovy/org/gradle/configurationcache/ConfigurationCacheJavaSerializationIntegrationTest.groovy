/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.configurationcache

class ConfigurationCacheJavaSerializationIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "restores task fields whose value is Serializable and has writeReplace method"() {
        buildFile << """
            class Placeholder implements Serializable {
                String value

                private Object readResolve() {
                    return new OtherBean(prop: "[\$value]")
                }
            }

            class OtherBean implements Serializable {
                String prop

                private Object writeReplace() {
                    return new Placeholder(value: prop)
                }
            }

            class SomeBean {
                OtherBean value
            }

            class SomeTask extends DefaultTask {
                private final SomeBean bean = new SomeBean()
                private final OtherBean value

                SomeTask() {
                    value = new OtherBean(prop: 'a')
                    bean.value = new OtherBean(prop: 'b')
                }

                @TaskAction
                void run() {
                    println "this.value = " + value.prop
                    println "bean.value = " + bean.value.prop
                }
            }

            task ok(type: SomeTask)
        """

        when:
        configurationCacheRun "ok"
        configurationCacheRun "ok"

        then:
        outputContains("this.value = [a]")
        outputContains("bean.value = [b]")
    }

    def "restores task fields whose value is Serializable and has only a writeObject method"() {
        buildFile << """
            class SomeBean implements Serializable {
                String value

                private void writeObject(java.io.ObjectOutputStream oos) {
                    value = "42"
                    oos.defaultWriteObject()
                }
            }

            class SomeTask extends DefaultTask {
                private final SomeBean bean = new SomeBean()

                @TaskAction
                void run() {
                    println "bean.value = " + bean.value
                }
            }

            task ok(type: SomeTask)
        """

        when:
        configurationCacheRun "ok"

        then: "bean is serialized before task runs"
        outputContains("bean.value = 42")

        when:
        configurationCacheRun "ok"

        then:
        outputContains("bean.value = 42")
    }

}
