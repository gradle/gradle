/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.tasks.diagnostics

import org.gradle.integtests.fixtures.internal.AbstractIntegrationSpec

class DependencyReportTaskIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        distribution.requireOwnUserHomeDir()
    }

    def "circular dependencies"() {
        given:
        file("settings.gradle") << "include 'client', 'a', 'b', 'c'"

        [a: "b", b: "c", c: "a"].each { module, dep ->
            def upped = module.toUpperCase()
            file(module, "build.gradle") << """
                apply plugin: 'java'
                group = "group"
                version = 1.0

                dependencies {
                    compile project(":$dep")
                }
            """
            file(module, "src", "main", "java", "${upped}.java") << "public class $upped {}"
        }

        and:
        file("client", "build.gradle") << """
            apply plugin: 'java'
            
            dependencies {
                compile project(":a")
            }
        """
        
        when:
        run ":client:dependencies"
        
        then:
        output.contains "(*) - dependencies omitted (listed previously)"
    }

}