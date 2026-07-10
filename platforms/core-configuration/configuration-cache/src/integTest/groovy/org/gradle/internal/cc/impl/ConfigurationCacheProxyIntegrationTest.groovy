/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.cc.impl

class ConfigurationCacheProxyIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "can store Proxy created in script classloader"() {
        given:
        buildFile """
            import java.lang.reflect.*
            import java.util.function.Supplier

            interface Proxied {
                Integer getValue()
            }

            abstract class PrintTask extends DefaultTask {
                @Input abstract Property<Proxied> getAnswer()
                @TaskAction void print() {
                    println("The answer is " + answer.get().value)
                }
            }

            tasks.register("print", PrintTask) {
                answer.set(
                    Proxy.newProxyInstance(
                        Proxied.classLoader,
                        // Supplier goes as the first interface to ensure Gradle is not trying
                        // to infer the classloader from the list of interfaces
                        new Class[] {Supplier.class, Proxied.class},
                        new InvocationHandler() {
                            @Override Object invoke(Object proxy, Method method, Object[] args) {
                                return 42
                            }
                        }
                    ) as Proxied
                )
            }
        """

        when:
        configurationCacheRun 'print'

        then:
        outputContains 'The answer is 42'
    }
}
