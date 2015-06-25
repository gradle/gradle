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

package org.gradle.jvm

import org.gradle.integtests.fixtures.EnableModelDsl
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.model.internal.persist.ReusingModelRegistryStore

// Requires daemon because reuse right now doesn't handle the build actually changing
class ModelReuseIntegrationTest extends DaemonIntegrationSpec {

    def setup() {
        EnableModelDsl.enable(executer)

        executer.beforeExecute {
            withArgument("-D$ReusingModelRegistryStore.TOGGLE=true")
        }
    }

    def "can enable reuse with the component model"() {
        when:
        buildScript """
            plugins {
              id "org.gradle.jvm-component"
              id "org.gradle.java-lang"
            }

            model {
                components {
                    main(JvmLibrarySpec)
                }
            }
        """

        file("src/main/java/Thing.java") << "class Thing {}"

        then:
        succeeds "build"
        executedAndNotSkipped ":compileMainJarMainJava"

        when:
        file("src/main/java/Thing.java").text = "class Thing { static int foo = 1; }"

        then:
        succeeds "build"
        executedAndNotSkipped ":compileMainJarMainJava"
    }
}
