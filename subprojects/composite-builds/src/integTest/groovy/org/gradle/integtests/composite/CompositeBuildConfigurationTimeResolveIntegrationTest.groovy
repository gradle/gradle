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

import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.MavenModule

/**
 * Tests for resolving dependencies at configuration-time in a composite build.
 * These tests demonstrate actual behaviour, not necessarily desired behaviour.
 */
class CompositeBuildConfigurationTimeResolveIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile buildB
    BuildTestFile buildC

    MavenModule publishedModuleB
    TestFile buildBjar

    def setup() {
        publishedModuleB = mavenRepo.module("org.test", "buildC", "1.0").publish()

        buildA.buildFile << """
            println "Configured buildA"
            task resolve(type: Copy) {
                from configurations.compileClasspath
                into 'libs'
            }
"""

        buildB = multiProjectBuild("buildB", ['b1', 'b2']) {
            buildFile << """
                println "Configured buildB"
                allprojects {
                    apply plugin: 'java'
                    repositories {
                        maven { url = "${mavenRepo.uri}" }
                    }
                }
"""
        }
        includedBuilds << buildB
        buildBjar = buildB.file('build/libs/buildB-1.0.jar')


        buildC = singleProjectBuild("buildC") {
            buildFile << """
                println "Configured buildC"
                apply plugin: 'java'
"""
        }
        includedBuilds << buildC

    }

    def "references but does not build substituted dependency resolved at configuration time"() {
        configurationTimeDependency 'org.test:buildB:1.0'

        when:
        execute buildA, ":resolve"

        then:
        executed ":resolve"
        notExecuted ":buildB:jar"
        outputContains "[$buildBjar]"

        configured("buildB") == 1
    }

    def "uses substituted dependency when same root build dependency is resolved at both configuration and execution time"() {
        configurationTimeDependency 'org.test:buildB:1.0'
        dependency 'org.test:buildB:1.0'

        when:
        execute buildA, ":resolve"

        then:
        executedInOrder ":buildB:jar", ":resolve"
        outputContains "[$buildBjar]"

        configured("buildB") == 1
    }

    def "references substituted dependencies when root build dependencies are resolved at both configuration and execution time"() {
        configurationTimeDependency 'org.test:buildB:1.0'
        dependency 'org.test:b1:1.0'

        when:
        execute buildA, ":resolve"

        then:
        executedInOrder ":buildB:b1:jar", ":resolve"
        notExecuted ":buildB:jar"

        outputContains("[$buildBjar]")
        assertResolved buildB.file('b1/build/libs/b1-1.0.jar')

        configured("buildB") == 1
    }

    def "included build references substituted dependency from preceding included build"() {
        dependency 'org.test:buildC:1.0'
        configurationTimeDependency buildC, 'org.test:buildB:1.0'

        when:
        execute buildA, ":help"

        then:
        executed ":help"
        notExecuted ":buildB:jar"
        outputContains("[$buildBjar]")

        configured("buildB") == 1
    }

    def "included build does not use substituted dependency from subsequent included build"() {
        dependency 'org.test:buildB:1.0'
        configurationTimeDependency buildB, 'org.test:buildC:1.0'
        buildB.buildFile << """
"""

        when:
        execute buildA, ":help"

        then:
        executedInOrder ":help"
        notExecuted ":buildB:jar"
        outputContains("[${publishedModuleB.artifactFile}]")

        configured("buildB") == 1
    }

    private void configurationTimeDependency(BuildTestFile sourceBuild = buildA, String notation) {
        sourceBuild.buildFile << """
            configurations {
                dummyConf
            }
            dependencies {
                dummyConf '$notation'
            }
            task dummy {
                println configurations.dummyConf.collect { it }
            }
"""
    }

    private void assertResolved(TestFile... files) {
        String[] names = files.collect { it.name }
        buildA.file('libs').assertHasDescendants(names)
        files.each {
            buildA.file('libs/' + it.name).assertIsCopyOf(it)
        }
    }

    private int configured(def build) {
        result.output.count("Configured " + build)
    }

    private void executedInOrder(String... tasks) {
        result.assertTaskOrder(tasks)
    }
}
