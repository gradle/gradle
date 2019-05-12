/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.junit.Test

class DependencyHandlerExtensionAwareDSLSpec extends AbstractIntegrationSpec {

    def setup() {
        // Required because of: https://github.com/gradle/gradle/issues/7413
        executer.requireGradleDistribution()
    }

    @Test
    void "Can type-safely use DependencyHandler ExtensionAware with the Groovy DSL"() {
        when:
        buildFile << """
        dependencies {
            extensions["theAnswer"] = {
                42
            }
        }
        dependencies {
            assert(extensions["theAnswer"]() == 42) : "Can access from a different dependencies block"
        }
        """
        then:
        succeeds("help")
    }

    @Test
    void "Can type-safely use DependencyHandler ExtensionAware with the Kotlin DSL"() {
        when:
        buildKotlinFile << """
        dependencies {
            val theAnswer: () -> Int by extra {
                { 42 }
            }
        }
        dependencies {
            val theAnswer: () -> Int by dependencies.extra
            assert(theAnswer() == 42) {
                "Can access from a different dependencies block"
            }
        }
        """
        then:
        succeeds("help")
    }
}
