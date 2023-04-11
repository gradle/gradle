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
        executer.expectDocumentedDeprecationWarning("'MyExtension' declares a property with a Boolean type. This behavior has been deprecated. This will change in Gradle 9.0. " +
            "The combination of method name and return type is not consistent with Java Bean property rules and will become unsupported in future versions of Groovy. " +
            "Change the return type of 'isProperty' to boolean or rename 'isProperty' to 'getProperty'. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#groovy_boolean_properties")
        succeeds("assertProperty")
    }
}
