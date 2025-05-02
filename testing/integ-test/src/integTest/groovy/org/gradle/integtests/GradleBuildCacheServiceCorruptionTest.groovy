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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Ignore

class GradleBuildCacheServiceCorruptionTest extends AbstractIntegrationSpec {

    @Ignore
    def "GradleBuild corrupts cache"() {
        settingsFile << """
            rootProject.name = "root"
            include 'a', 'b'
        """
        buildFile << """
            allprojects {
                apply plugin: 'java'
            }
            subprojects {
                task breakBuild(type: GradleBuild) {
                    dir = rootDir
                    tasks = ["jar"]
                }
            }
        """
        executer.withArguments("--parallel", "--info")
        expect:
        succeeds("clean", "breakBuild")
        // This fails when running with the forking executor
        succeeds("clean", "breakBuild")
    }
}
