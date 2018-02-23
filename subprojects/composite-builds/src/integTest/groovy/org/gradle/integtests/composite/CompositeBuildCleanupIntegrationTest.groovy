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

class CompositeBuildCleanupIntegrationTest extends AbstractCompositeBuildIntegrationTest {

    def "stale outputs are removed from composite builds"() {
        given:
        dependency("org.test:buildB:1.0")

        def buildB = singleProjectBuild("buildB") {
            buildFile << """
                apply plugin: 'java'
            """
        }
        includedBuilds << buildB

        def staleFiles = [
            file("buildA/build/classes/java/main/stale"),
            file("buildB/build/classes/java/main/stale")]
        staleFiles*.touch()

        when:
        execute(buildA, 'build')
        then:
        staleFiles*.assertDoesNotExist()
    }
}
