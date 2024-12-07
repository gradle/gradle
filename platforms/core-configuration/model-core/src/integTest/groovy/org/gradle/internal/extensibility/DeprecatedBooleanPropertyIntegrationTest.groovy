/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.extensibility

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class DeprecatedBooleanPropertyIntegrationTest extends AbstractIntegrationSpec {
    def "does not emit deprecation warning when a decorated class exposes a Boolean property like a field"() {
        buildFile << """
            abstract class MyExtension {
                Boolean property = Boolean.TRUE
            }
            def myext = extensions.create("myext", MyExtension)
            task assertProperty {
                doLast {
                    assert myext.property
                }
            }
        """
        expect:
        succeeds("assertProperty")
    }

    def "does not emit deprecation warning when a decorated class exposes a Boolean getter"() {
        buildFile << """
            abstract class MyExtension {
                Boolean getProperty() { return Boolean.TRUE }
            }
            def myext = extensions.create("myext", MyExtension)
            task assertProperty {
                doLast {
                    assert myext.property
                }
            }
        """
        expect:
        succeeds("assertProperty")
    }

    def "does not emit deprecation warning when a decorated class exposes a boolean is-getter"() {
        buildFile << """
            abstract class MyExtension {
                boolean isProperty() { return Boolean.TRUE }
            }
            def myext = extensions.create("myext", MyExtension)
            task assertProperty {
                doLast {
                    assert myext.property
                }
            }
        """
        expect:
        succeeds("assertProperty")
    }

    def "emits deprecation warning when a decorated class exposes a Boolean is-getter"() {
        buildFile << """
            abstract class MyExtension {
                Boolean isProperty() { return Boolean.TRUE }
            }
            def myext = extensions.create("myext", MyExtension)
            task assertProperty {
                doLast {
                    assert myext.property
                }
            }
        """
        expect:
        executer.expectDocumentedDeprecationWarning("Declaring an 'is-' property with a Boolean type has been deprecated. Starting with Gradle 9.0, this property will be ignored by Gradle. " +
            "The combination of method name and return type is not consistent with Java Bean property rules and will become unsupported in future versions of Groovy. " +
            "Add a method named 'getProperty' with the same behavior and mark the old one with @Deprecated, or change the type of 'MyExtension.isProperty' (and the setter) to 'boolean'. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#groovy_boolean_properties")
        succeeds("assertProperty")
    }

    def "does not emit deprecation warning when a decorated class exposes both a Boolean is-getter and normal getter"() {
        buildFile << """
            abstract class MyExtension {
                // This type serves as an example of how to fix the issue:
                Boolean isProperty() { return Boolean.TRUE } // The deprecated one
                Boolean getProperty() { return Boolean.TRUE } // The non-breaking fix for deprecation, which should fix the warning.
            }
            def myext = extensions.create("myext", MyExtension)
            task assertProperty {
                doLast {
                    assert myext.property
                }
            }
        """
        expect:
        succeeds("assertProperty")
    }

    def "emits a deprecation warning when a non-decorated class used as a task input exposes a Boolean is-getter"() {
        buildFile << """
            class MyValue {
                @Input
                Boolean isProperty() { return Boolean.TRUE }
            }
            class MyTask extends DefaultTask {
                @Nested
                MyValue value = new MyValue()

                @TaskAction
                void doAction() {
                    assert value.property
                }
            }
            tasks.create("assertProperty", MyTask)
        """
        expect:
        executer.expectDocumentedDeprecationWarning("Declaring an 'is-' property with a Boolean type has been deprecated. Starting with Gradle 9.0, this property will be ignored by Gradle. " +
            "The combination of method name and return type is not consistent with Java Bean property rules and will become unsupported in future versions of Groovy. " +
            "Add a method named 'getProperty' with the same behavior and mark the old one with @Deprecated and @ReplacedBy, or change the type of 'MyValue.isProperty' (and the setter) to 'boolean'. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#groovy_boolean_properties")
        succeeds("assertProperty")
    }

    def "does not emit a deprecation warning when a non-decorated class used as a task input exposes a Boolean is-getter and normal getter"() {
        buildFile << """
            class MyValue {
                // This type serves as an example of how legacy Groovy code is fine (this has both an is-getter and a normal getter)
                @Input
                Boolean property = Boolean.TRUE
            }
            class MyTask extends DefaultTask {
                @Nested
                MyValue value = new MyValue()

                @TaskAction
                void doAction() {
                    assert value.isProperty()
                    assert value.getProperty()
                }
            }
            tasks.create("assertProperty", MyTask)
        """
        expect:
        succeeds("assertProperty")
    }

    def "does not emit a deprecation warning when a non-decorated class used as a task input exposes a Boolean is-getter and normal getter (with proper replacement)"() {
        buildFile << """
            class MyValue {
                // This type serves as an example of how to fix the issue:

                @Deprecated // Deprecate the old property to users
                @ReplacedBy("getProperty") // Changed to ignore the property and inform users of the replacement
                Boolean isProperty() { return Boolean.TRUE }

                // The new replacement method, which now is used as the @Input
                @Input
                Boolean getProperty() { return Boolean.TRUE }
            }
            class MyTask extends DefaultTask {
                @Nested
                MyValue value = new MyValue()

                @TaskAction
                void doAction() {
                    assert value.isProperty()
                    assert value.getProperty()
                }
            }
            tasks.create("assertProperty", MyTask)
        """
        expect:
        succeeds("assertProperty")
    }
}
