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

import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.not

class ConfigurationCacheIntegrityCheckIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    static final String DAEMON_COMMUNICATION_FAILURE = "Could not receive a message from the daemon"
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
            ${brokenSerializable()}

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
        failureDescriptionStartsWith("Configuration cache state could not be cached: " +
            "field `user` of task `:showUser` of type `MyTask`: " +
            "The value cannot be decoded properly with 'JavaObjectSerializationCodec'."
        )
        failureCauseContains("Tag guard mismatch for JavaObjectSerializationCodec:")

        and:
        failure.assertThatDescription(not(containsString(DAEMON_COMMUNICATION_FAILURE)))
    }

    def "integrity checks detect invalid serialization protocol implementation in fingerprint when the broken value is #shape"() {
        def configurationCache = new ConfigurationCacheFixture(this)

        buildFile """
            import org.gradle.api.provider.*

            ${brokenSerializable()}

            $extraClasses

            abstract class MyValueSource implements ValueSource<$valueType, ValueSourceParameters.None> {
                @Override
                $valueType obtain() {
                    return $valueExpression
                }
            }

            tasks.register("showUser") {
                def user = providers.of(MyValueSource) {}.get()$accessor

                doLast {
                    println("Hello, \$user!")
                }
            }
        """

        when:
        configurationCacheRun("showUser", "-D${INTEGRITY_CHECKS}=true")

        then:
        // We only read fingerprint when reusing the cache, it isn't part of load-after-store.
        // Thus the first (store) run succeeds.
        configurationCache.assertStateStored()

        when:
        configurationCacheFails("showUser", "-D${INTEGRITY_CHECKS}=true")

        then:
        failureDescriptionContains(
            'field `value` of `org.gradle.internal.Try$Success` bean found in ' +
                'field `value` of `org.gradle.api.internal.provider.DefaultValueSourceProviderFactory$DefaultObtainedValue` bean found in ' +
                'class `MyValueSource`' +
                ": The value cannot be decoded properly with 'JavaObjectSerializationCodec'. " +
                "It may have been written incorrectly or its data is corrupted.")
        failureCauseContains("Tag guard mismatch for JavaObjectSerializationCodec:")

        and:
        failure.assertThatDescription(not(containsString(DAEMON_COMMUNICATION_FAILURE)))

        where:
        shape                  | valueType            | accessor      | extraClasses                                                                                                                  | valueExpression
        "the value itself"     | "CustomSerializable" | ".name"       | ""                                                                                                                            | "new CustomSerializable('John', 23)"
        "nested in a bean"     | "Wrapper"            | ".inner.name" | "class Wrapper implements Serializable { CustomSerializable inner; Wrapper(CustomSerializable inner) { this.inner = inner } }" | "new Wrapper(new CustomSerializable('John', 23))"
        "an element of a list" | "List"               | "[0].name"    | ""                                                                                                                            | "[new CustomSerializable('John', 23)]"
    }

    def "custom exception raised while reading the fingerprint is deserializable by the client"() {
        def configurationCache = new ConfigurationCacheFixture(this)

        buildFile """
            import org.gradle.api.provider.*

            class BrokenFingerprintException extends RuntimeException {
                BrokenFingerprintException(String message) { super(message) }
            }

            class ThrowsWhenRead implements Serializable {
                private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
                    input.defaultReadObject()
                    throw new BrokenFingerprintException("failed to read fingerprint value")
                }
            }

            abstract class MyValueSource implements ValueSource<ThrowsWhenRead, ValueSourceParameters.None> {
                @Override
                ThrowsWhenRead obtain() {
                    return new ThrowsWhenRead()
                }
            }

            tasks.register("showUser") {
                providers.of(MyValueSource) {}.get()
                doLast {
                    println("Hello!")
                }
            }
        """

        when:
        configurationCacheRun("showUser", "-D${INTEGRITY_CHECKS}=true")

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheFails("showUser", "-D${INTEGRITY_CHECKS}=true")

        then:
        failureCauseContains("failed to read fingerprint value")

        and:
        failure.assertThatDescription(not(containsString(DAEMON_COMMUNICATION_FAILURE)))
    }

    def brokenSerializable() {
        groovySnippet """
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
        """
    }
}
