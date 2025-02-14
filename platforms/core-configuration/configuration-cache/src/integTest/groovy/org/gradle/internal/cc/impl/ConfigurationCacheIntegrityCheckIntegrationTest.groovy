/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture

class ConfigurationCacheIntegrityCheckIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    static final String INTEGRITY_CHECKS = StartParameterBuildOptions.ConfigurationCacheIntegrityCheckOption.PROPERTY_NAME

    def "enabling integrity check invalidates CC"() {
        buildFile """
            tasks.register("hello") { doLast { println "Hello" } }
        """
        def configurationCache = new ConfigurationCacheFixture(this)

        when:
        configurationCacheRun("hello")

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun("hello", "-D${INTEGRITY_CHECKS}=false")

        then:
        configurationCache.assertStateLoaded()

        when:
        configurationCacheRun("hello", "-D${INTEGRITY_CHECKS}=true")

        then:
        configurationCache.assertStateStored()
     }

    def "integrity checks detect invalid serialization protocol implementation"() {
        buildFile """
            class CustomSerializable implements Serializable {
                private transient String name
                private transient int age

                CustomSerializable(String name, int age) {
                    this.name = name
                    this.age = age
                }

                String getName() { return name }

                int getAge() { return age }

                private void writeObject(ObjectOutputStream out) throws IOException {
                    out.defaultWriteObject()

                    out.writeObject(name)
                    out.writeInt(age)
                }

                // Custom deserialization
                private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
                    input.defaultReadObject()

                    this.name = (String) input.readObject()
                }
            }

            abstract class MyTask extends DefaultTask {
                CustomSerializable user = new CustomSerializable("John", 25)

                @TaskAction
                def action() {
                    println("name = \${user.name}, age = \${user.age}")
                }
            }

            tasks.register("showUser", MyTask)
        """

        when:
        configurationCacheFails("showUser", "-D${INTEGRITY_CHECKS}=true")

        then:
        failureDescriptionStartsWith("Configuration cache state could not be cached: field `user` of task `:showUser` of type `MyTask`: The value cannot be decoded properly.")
        failureCauseContains("Tag guard mismatch:")
    }
}
