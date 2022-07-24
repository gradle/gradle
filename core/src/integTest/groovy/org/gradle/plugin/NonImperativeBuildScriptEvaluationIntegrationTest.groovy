/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.plugin

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class NonImperativeBuildScriptEvaluationIntegrationTest extends AbstractIntegrationSpec {

    def "all non-imperative script plugins applied to a project get evaluated"() {
        when:
        file("scriptPlugin1.gradle") << """
            model {
                tasks {
                    create("fromScriptPlugin1")
                }
            }
        """

        file("scriptPlugin2.gradle") << """
            model {
                tasks {
                    create("fromScriptPlugin2")
                }
            }
        """

        buildScript """
            apply from: "scriptPlugin1.gradle"
            apply from: "scriptPlugin2.gradle"
        """

        then:
        // Invoke twice to exercise script caching
        succeeds "fromScriptPlugin1", "fromScriptPlugin2"
        succeeds "fromScriptPlugin1", "fromScriptPlugin2"
    }
}
