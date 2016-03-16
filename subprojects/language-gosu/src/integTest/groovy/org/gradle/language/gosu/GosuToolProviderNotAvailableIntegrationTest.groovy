/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.language.gosu

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.jvm.TestJvmComponent
import org.gradle.language.gosu.fixtures.TestGosuComponent

class GosuToolProviderNotAvailableIntegrationTest extends AbstractIntegrationSpec {
    TestJvmComponent app = new TestGosuComponent()

    def setup() {
        buildFile << """
        plugins {
            id 'jvm-component'
            id '${app.languageName}-lang'
        }
        model {
            components {
                myLib(JvmLibrarySpec)
            }
        }
    """
    }

    def "provide decent error message when gosu tools not available"() {
        given:
        app.writeSources(file("src/myLib"))
        app.writeResources(file("src/myLib/resources"))
        when:
        fails("assemble")
        then:
        errorOutput.contains("Cannot provide Gosu Compiler: Cannot resolve external dependency org.gosu-lang.gosu:gosu-ant-tools:1.13.1 because no repositories are defined.") //see GosuLanguagePlugin#configureGosuTask

    }
}
