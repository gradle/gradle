/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class CompositeBuildCleanupIntegrationTest extends AbstractIntegrationSpec {

    def "stale outputs are removed from composite builds"() {
        given:
        multiProjectBuild("multi-project", ['sub1', 'sub2']) {
            buildFile << """
                subprojects {
                    apply plugin: 'java'
                    dependencies {
                        compile 'org:included:1.0'
                    }
                }
            """
            settingsFile << "includeBuild 'included'"
            file('included/build.gradle') << """
                apply plugin: 'java'
                group = 'org'
                version = '1.0'
            """
            file('included/settings.gradle').touch()
        }
        def staleFiles = [
            file("included/build/classes/java/main/stale"),
            file("sub1/build/classes/java/main/stale"),
            file("sub2/build/classes/java/main/stale") ]
        staleFiles*.touch()

        when:
        succeeds 'build'
        then:
        staleFiles*.assertDoesNotExist()
    }
}
