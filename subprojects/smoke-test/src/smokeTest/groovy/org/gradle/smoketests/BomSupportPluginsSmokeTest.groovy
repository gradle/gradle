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

package org.gradle.smoketests

import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Unroll

class BomSupportPluginsSmokeTest extends AbstractSmokeTest {
    static bomVersion = "1.5.8.RELEASE"
    static bom = "'org.springframework.boot:spring-boot-dependencies:$bomVersion'"

    @Unroll
    def 'bom support is provided by #bomSupportProvider'() {
        given:
        testProjectDir.newFile('settings.gradle') << """
            rootProject.name = 'springbootproject'
        """
        def buildScript = """
            plugins {
                id "java"
                ${dependencyManagementPlugin}
            }
            ${jcenterRepository()}

            ${bomDeclaration}

            dependencies {
                implementation "org.springframework.boot:spring-boot"
                implementation "org.springframework.boot:spring-boot-autoconfigure"
            
                testImplementation "junit:junit"
                testImplementation "org.springframework:spring-test"
                testImplementation "org.springframework.boot:spring-boot-test"
                testImplementation "org.springframework.boot:spring-boot-test-autoconfigure"
            }
        """
        def resolve = new ResolveTestFixture(new TestFile(buildFile), 'testCompileClasspath')
        resolve.prepare(buildScript)

        when:
        runner('checkDep').build()

        then:
        def expected = expectedDependencyTree(reason1, reason2, reason3)
        def actual = resolve.getResultFile().text.split('\n')
        actual.size() == actual.size()
        actual.each {
            assert expected.contains(it)
        }

        where:
        bomSupportProvider                    | reason1          | reason2          | reason3          | bomDeclaration                                        | dependencyManagementPlugin
        "gradle"                              | "conflict"       | "conflict"       | "requested"      | "dependencies { implementation $bom }"                |""
        "nebula recommender plugin"           | "selectedByRule" | "requested"      | "requested"      | "dependencyRecommendations { mavenBom module: $bom }" |"id 'nebula.dependency-recommender' version '5.0.0'"
        "spring dependency management plugin" | "selectedByRule" | "selectedByRule" | "selectedByRule" | "dependencyManagement { imports { mavenBom $bom } }"  |"id 'io.spring.dependency-management' version '1.0.3.RELEASE'"

    }

    private static expectedDependencyTree(String reason1, String reason2, String reason3) {
"""component:[id:org.springframework.boot:spring-boot-dependencies:1.5.8.RELEASE][mv:org.springframework.boot:spring-boot-dependencies:1.5.8.RELEASE][reason:requested]
component:[id:commons-logging:commons-logging:1.2][mv:commons-logging:commons-logging:1.2][reason:requested]
component:[id:org.springframework.boot:spring-boot-test-autoconfigure:1.5.8.RELEASE][mv:org.springframework.boot:spring-boot-test-autoconfigure:1.5.8.RELEASE][reason:$reason1]
component:[id:junit:junit:4.12][mv:junit:junit:4.12][reason:$reason2]
component:[id:org.springframework:spring-test:4.3.12.RELEASE][mv:org.springframework:spring-test:4.3.12.RELEASE][reason:$reason2]
component:[id:org.springframework.boot:spring-boot:1.5.8.RELEASE][mv:org.springframework.boot:spring-boot:1.5.8.RELEASE][reason:$reason2]
component:[id:org.springframework.boot:spring-boot-test:1.5.8.RELEASE][mv:org.springframework.boot:spring-boot-test:1.5.8.RELEASE][reason:$reason2]
component:[id:org.springframework.boot:spring-boot-autoconfigure:1.5.8.RELEASE][mv:org.springframework.boot:spring-boot-autoconfigure:1.5.8.RELEASE][reason:$reason2]
component:[id:org.hamcrest:hamcrest-core:1.3][mv:org.hamcrest:hamcrest-core:1.3][reason:$reason3]
component:[id:org.springframework:spring-aop:4.3.12.RELEASE][mv:org.springframework:spring-aop:4.3.12.RELEASE][reason:$reason3]
component:[id:org.springframework:spring-beans:4.3.12.RELEASE][mv:org.springframework:spring-beans:4.3.12.RELEASE][reason:$reason3]
component:[id:org.springframework:spring-core:4.3.12.RELEASE][mv:org.springframework:spring-core:4.3.12.RELEASE][reason:$reason3]
component:[id:org.springframework:spring-context:4.3.12.RELEASE][mv:org.springframework:spring-context:4.3.12.RELEASE][reason:$reason3]
component:[id:org.springframework:spring-expression:4.3.12.RELEASE][mv:org.springframework:spring-expression:4.3.12.RELEASE][reason:$reason3]
first-level:[org.springframework.boot:spring-boot-dependencies:1.5.8.RELEASE:default]
first-level:[org.springframework.boot:spring-boot-test-autoconfigure:1.5.8.RELEASE:default]
first-level:[org.springframework.boot:spring-boot-test:1.5.8.RELEASE:default]
first-level:[org.springframework.boot:spring-boot-autoconfigure:1.5.8.RELEASE:default]
first-level:[org.springframework.boot:spring-boot:1.5.8.RELEASE:default]
first-level:[org.springframework:spring-test:4.3.12.RELEASE:default]
first-level:[junit:junit:4.12:default]
first-level-filtered:[org.springframework.boot:spring-boot-dependencies:1.5.8.RELEASE:default]
first-level-filtered:[junit:junit:4.12:default]
first-level-filtered:[org.springframework:spring-test:4.3.12.RELEASE:default]
first-level-filtered:[org.springframework.boot:spring-boot:1.5.8.RELEASE:default]
first-level-filtered:[org.springframework.boot:spring-boot-test:1.5.8.RELEASE:default]
first-level-filtered:[org.springframework.boot:spring-boot-test-autoconfigure:1.5.8.RELEASE:default]
first-level-filtered:[org.springframework.boot:spring-boot-autoconfigure:1.5.8.RELEASE:default]
lenient-first-level:[org.springframework.boot:spring-boot-dependencies:1.5.8.RELEASE:default]
lenient-first-level:[org.springframework.boot:spring-boot-test-autoconfigure:1.5.8.RELEASE:default]
lenient-first-level:[org.springframework.boot:spring-boot-test:1.5.8.RELEASE:default]
lenient-first-level:[org.springframework.boot:spring-boot-autoconfigure:1.5.8.RELEASE:default]
lenient-first-level:[org.springframework.boot:spring-boot:1.5.8.RELEASE:default]
lenient-first-level:[org.springframework:spring-test:4.3.12.RELEASE:default]
lenient-first-level:[junit:junit:4.12:default]
lenient-first-level-filtered:[org.springframework.boot:spring-boot-dependencies:1.5.8.RELEASE:default]
lenient-first-level-filtered:[junit:junit:4.12:default]
lenient-first-level-filtered:[org.springframework:spring-test:4.3.12.RELEASE:default]
lenient-first-level-filtered:[org.springframework.boot:spring-boot:1.5.8.RELEASE:default]
lenient-first-level-filtered:[org.springframework.boot:spring-boot-test:1.5.8.RELEASE:default]
lenient-first-level-filtered:[org.springframework.boot:spring-boot-test-autoconfigure:1.5.8.RELEASE:default]
lenient-first-level-filtered:[org.springframework.boot:spring-boot-autoconfigure:1.5.8.RELEASE:default]
configuration:[org.springframework.boot:spring-boot-dependencies:1.5.8.RELEASE]
configuration:[org.springframework.boot:spring-boot-test-autoconfigure:1.5.8.RELEASE]
configuration:[org.springframework.boot:spring-boot-test:1.5.8.RELEASE]
configuration:[org.springframework.boot:spring-boot:1.5.8.RELEASE]
configuration:[org.springframework:spring-test:4.3.12.RELEASE]
configuration:[junit:junit:4.12]
configuration:[org.hamcrest:hamcrest-core:1.3]
configuration:[org.springframework:spring-context:4.3.12.RELEASE]
configuration:[org.springframework:spring-aop:4.3.12.RELEASE]
configuration:[org.springframework:spring-beans:4.3.12.RELEASE]
configuration:[org.springframework:spring-core:4.3.12.RELEASE]
configuration:[commons-logging:commons-logging:1.2]
configuration:[org.springframework:spring-expression:4.3.12.RELEASE]
configuration:[org.springframework.boot:spring-boot-autoconfigure:1.5.8.RELEASE]
configuration:[org.springframework.boot:spring-boot-test-autoconfigure:1.5.8.RELEASE]
configuration:[org.springframework.boot:spring-boot-test:1.5.8.RELEASE]
configuration:[org.springframework.boot:spring-boot-autoconfigure:1.5.8.RELEASE]
configuration:[org.springframework.boot:spring-boot:1.5.8.RELEASE]
configuration:[org.springframework:spring-test:4.3.12.RELEASE]
configuration:[junit:junit:4.12]
root:[id:project :][mv::springbootproject:unspecified][reason:root]
component:[id:project :][mv::springbootproject:unspecified][reason:root]
dependency:[from:project :][org.springframework.boot:spring-boot-dependencies:1.5.8.RELEASE->org.springframework.boot:spring-boot-dependencies:1.5.8.RELEASE]
dependency:[from:org.springframework.boot:spring-boot-dependencies:1.5.8.RELEASE][junit:junit:4.12->junit:junit:4.12]
dependency:[from:junit:junit:4.12][org.hamcrest:hamcrest-core:1.3->org.hamcrest:hamcrest-core:1.3]
dependency:[from:org.springframework.boot:spring-boot-dependencies:1.5.8.RELEASE][org.springframework:spring-aop:4.3.12.RELEASE->org.springframework:spring-aop:4.3.12.RELEASE]
dependency:[from:org.springframework:spring-aop:4.3.12.RELEASE][org.springframework:spring-beans:4.3.12.RELEASE->org.springframework:spring-beans:4.3.12.RELEASE]
dependency:[from:org.springframework:spring-beans:4.3.12.RELEASE][org.springframework:spring-core:4.3.12.RELEASE->org.springframework:spring-core:4.3.12.RELEASE]
dependency:[from:org.springframework:spring-core:4.3.12.RELEASE][commons-logging:commons-logging:1.2->commons-logging:commons-logging:1.2]
dependency:[from:org.springframework:spring-aop:4.3.12.RELEASE][org.springframework:spring-core:4.3.12.RELEASE->org.springframework:spring-core:4.3.12.RELEASE]
dependency:[from:org.springframework.boot:spring-boot-dependencies:1.5.8.RELEASE][org.springframework:spring-beans:4.3.12.RELEASE->org.springframework:spring-beans:4.3.12.RELEASE]
dependency:[from:org.springframework.boot:spring-boot-dependencies:1.5.8.RELEASE][org.springframework:spring-context:4.3.12.RELEASE->org.springframework:spring-context:4.3.12.RELEASE]
dependency:[from:org.springframework:spring-context:4.3.12.RELEASE][org.springframework:spring-aop:4.3.12.RELEASE->org.springframework:spring-aop:4.3.12.RELEASE]
dependency:[from:org.springframework:spring-context:4.3.12.RELEASE][org.springframework:spring-beans:4.3.12.RELEASE->org.springframework:spring-beans:4.3.12.RELEASE]
dependency:[from:org.springframework:spring-context:4.3.12.RELEASE][org.springframework:spring-core:4.3.12.RELEASE->org.springframework:spring-core:4.3.12.RELEASE]
dependency:[from:org.springframework:spring-context:4.3.12.RELEASE][org.springframework:spring-expression:4.3.12.RELEASE->org.springframework:spring-expression:4.3.12.RELEASE]
dependency:[from:org.springframework:spring-expression:4.3.12.RELEASE][org.springframework:spring-core:4.3.12.RELEASE->org.springframework:spring-core:4.3.12.RELEASE]
dependency:[from:org.springframework.boot:spring-boot-dependencies:1.5.8.RELEASE][org.springframework:spring-core:4.3.12.RELEASE->org.springframework:spring-core:4.3.12.RELEASE]
dependency:[from:org.springframework.boot:spring-boot-dependencies:1.5.8.RELEASE][org.springframework:spring-expression:4.3.12.RELEASE->org.springframework:spring-expression:4.3.12.RELEASE]
dependency:[from:org.springframework.boot:spring-boot-dependencies:1.5.8.RELEASE][org.springframework:spring-test:4.3.12.RELEASE->org.springframework:spring-test:4.3.12.RELEASE]
dependency:[from:org.springframework:spring-test:4.3.12.RELEASE][junit:junit:4.12->junit:junit:4.12]
dependency:[from:org.springframework:spring-test:4.3.12.RELEASE][org.hamcrest:hamcrest-core:1.3->org.hamcrest:hamcrest-core:1.3]
dependency:[from:org.springframework:spring-test:4.3.12.RELEASE][org.springframework:spring-aop:4.3.12.RELEASE->org.springframework:spring-aop:4.3.12.RELEASE]
dependency:[from:org.springframework:spring-test:4.3.12.RELEASE][org.springframework:spring-beans:4.3.12.RELEASE->org.springframework:spring-beans:4.3.12.RELEASE]
dependency:[from:org.springframework:spring-test:4.3.12.RELEASE][org.springframework:spring-context:4.3.12.RELEASE->org.springframework:spring-context:4.3.12.RELEASE]
dependency:[from:org.springframework:spring-test:4.3.12.RELEASE][org.springframework:spring-core:4.3.12.RELEASE->org.springframework:spring-core:4.3.12.RELEASE]
dependency:[from:org.springframework.boot:spring-boot-dependencies:1.5.8.RELEASE][org.springframework.boot:spring-boot:1.5.8.RELEASE->org.springframework.boot:spring-boot:1.5.8.RELEASE]
dependency:[from:org.springframework.boot:spring-boot:1.5.8.RELEASE][org.springframework:spring-core:4.3.12.RELEASE->org.springframework:spring-core:4.3.12.RELEASE]
dependency:[from:org.springframework.boot:spring-boot:1.5.8.RELEASE][org.springframework:spring-context:4.3.12.RELEASE->org.springframework:spring-context:4.3.12.RELEASE]
dependency:[from:org.springframework.boot:spring-boot:1.5.8.RELEASE][junit:junit:4.12->junit:junit:4.12]
dependency:[from:org.springframework.boot:spring-boot:1.5.8.RELEASE][org.springframework:spring-test:4.3.12.RELEASE->org.springframework:spring-test:4.3.12.RELEASE]
dependency:[from:org.springframework.boot:spring-boot-dependencies:1.5.8.RELEASE][org.springframework.boot:spring-boot-test:1.5.8.RELEASE->org.springframework.boot:spring-boot-test:1.5.8.RELEASE]
dependency:[from:org.springframework.boot:spring-boot-test:1.5.8.RELEASE][org.springframework.boot:spring-boot:1.5.8.RELEASE->org.springframework.boot:spring-boot:1.5.8.RELEASE]
dependency:[from:org.springframework.boot:spring-boot-test:1.5.8.RELEASE][junit:junit:4.12->junit:junit:4.12]
dependency:[from:org.springframework.boot:spring-boot-test:1.5.8.RELEASE][org.hamcrest:hamcrest-core:1.3->org.hamcrest:hamcrest-core:1.3]
dependency:[from:org.springframework.boot:spring-boot-test:1.5.8.RELEASE][org.springframework:spring-test:4.3.12.RELEASE->org.springframework:spring-test:4.3.12.RELEASE]
dependency:[from:org.springframework.boot:spring-boot-dependencies:1.5.8.RELEASE][org.springframework.boot:spring-boot-test-autoconfigure:1.5.8.RELEASE->org.springframework.boot:spring-boot-test-autoconfigure:1.5.8.RELEASE]
dependency:[from:org.springframework.boot:spring-boot-test-autoconfigure:1.5.8.RELEASE][org.springframework.boot:spring-boot-test:1.5.8.RELEASE->org.springframework.boot:spring-boot-test:1.5.8.RELEASE]
dependency:[from:org.springframework.boot:spring-boot-test-autoconfigure:1.5.8.RELEASE][org.springframework.boot:spring-boot-autoconfigure:1.5.8.RELEASE->org.springframework.boot:spring-boot-autoconfigure:1.5.8.RELEASE]
dependency:[from:org.springframework.boot:spring-boot-autoconfigure:1.5.8.RELEASE][org.springframework.boot:spring-boot:1.5.8.RELEASE->org.springframework.boot:spring-boot:1.5.8.RELEASE]
dependency:[from:org.springframework.boot:spring-boot-test-autoconfigure:1.5.8.RELEASE][org.springframework:spring-test:4.3.12.RELEASE->org.springframework:spring-test:4.3.12.RELEASE]
dependency:[from:org.springframework.boot:spring-boot-dependencies:1.5.8.RELEASE][org.springframework.boot:spring-boot-autoconfigure:1.5.8.RELEASE->org.springframework.boot:spring-boot-autoconfigure:1.5.8.RELEASE]
dependency:[from:org.springframework.boot:spring-boot-dependencies:1.5.8.RELEASE][org.hamcrest:hamcrest-core:1.3->org.hamcrest:hamcrest-core:1.3]
dependency:[from:project :][org.springframework.boot:spring-boot:->org.springframework.boot:spring-boot:1.5.8.RELEASE]
dependency:[from:project :][org.springframework.boot:spring-boot-autoconfigure:->org.springframework.boot:spring-boot-autoconfigure:1.5.8.RELEASE]
dependency:[from:project :][junit:junit:->junit:junit:4.12]
dependency:[from:project :][org.springframework:spring-test:->org.springframework:spring-test:4.3.12.RELEASE]
dependency:[from:project :][org.springframework.boot:spring-boot-test:->org.springframework.boot:spring-boot-test:1.5.8.RELEASE]
dependency:[from:project :][org.springframework.boot:spring-boot-test-autoconfigure:->org.springframework.boot:spring-boot-test-autoconfigure:1.5.8.RELEASE]
file:spring-boot-test-autoconfigure-1.5.8.RELEASE.jar
file:spring-boot-test-1.5.8.RELEASE.jar
file:spring-boot-autoconfigure-1.5.8.RELEASE.jar
file:spring-boot-1.5.8.RELEASE.jar
file:spring-test-4.3.12.RELEASE.jar
file:junit-4.12.jar
file:hamcrest-core-1.3.jar
file:spring-context-4.3.12.RELEASE.jar
file:spring-aop-4.3.12.RELEASE.jar
file:spring-beans-4.3.12.RELEASE.jar
file:spring-expression-4.3.12.RELEASE.jar
file:spring-core-4.3.12.RELEASE.jar
file:commons-logging-1.2.jar
file-incoming:spring-boot-test-autoconfigure-1.5.8.RELEASE.jar
file-incoming:spring-boot-test-1.5.8.RELEASE.jar
file-incoming:spring-boot-autoconfigure-1.5.8.RELEASE.jar
file-incoming:spring-boot-1.5.8.RELEASE.jar
file-incoming:spring-test-4.3.12.RELEASE.jar
file-incoming:junit-4.12.jar
file-incoming:hamcrest-core-1.3.jar
file-incoming:spring-context-4.3.12.RELEASE.jar
file-incoming:spring-aop-4.3.12.RELEASE.jar
file-incoming:spring-beans-4.3.12.RELEASE.jar
file-incoming:spring-expression-4.3.12.RELEASE.jar
file-incoming:spring-core-4.3.12.RELEASE.jar
file-incoming:commons-logging-1.2.jar
file-artifact-incoming:spring-boot-test-autoconfigure-1.5.8.RELEASE.jar
file-artifact-incoming:spring-boot-test-1.5.8.RELEASE.jar
file-artifact-incoming:spring-boot-autoconfigure-1.5.8.RELEASE.jar
file-artifact-incoming:spring-boot-1.5.8.RELEASE.jar
file-artifact-incoming:spring-test-4.3.12.RELEASE.jar
file-artifact-incoming:junit-4.12.jar
file-artifact-incoming:hamcrest-core-1.3.jar
file-artifact-incoming:spring-context-4.3.12.RELEASE.jar
file-artifact-incoming:spring-aop-4.3.12.RELEASE.jar
file-artifact-incoming:spring-beans-4.3.12.RELEASE.jar
file-artifact-incoming:spring-expression-4.3.12.RELEASE.jar
file-artifact-incoming:spring-core-4.3.12.RELEASE.jar
file-artifact-incoming:commons-logging-1.2.jar
file-filtered:spring-core-4.3.12.RELEASE.jar
file-filtered:commons-logging-1.2.jar
file-filtered:spring-expression-4.3.12.RELEASE.jar
file-filtered:spring-beans-4.3.12.RELEASE.jar
file-filtered:spring-aop-4.3.12.RELEASE.jar
file-filtered:spring-context-4.3.12.RELEASE.jar
file-filtered:hamcrest-core-1.3.jar
file-filtered:junit-4.12.jar
file-filtered:spring-test-4.3.12.RELEASE.jar
file-filtered:spring-boot-1.5.8.RELEASE.jar
file-filtered:spring-boot-autoconfigure-1.5.8.RELEASE.jar
file-filtered:spring-boot-test-1.5.8.RELEASE.jar
file-filtered:spring-boot-test-autoconfigure-1.5.8.RELEASE.jar
file-collection-filtered:spring-core-4.3.12.RELEASE.jar
file-collection-filtered:commons-logging-1.2.jar
file-collection-filtered:spring-expression-4.3.12.RELEASE.jar
file-collection-filtered:spring-beans-4.3.12.RELEASE.jar
file-collection-filtered:spring-aop-4.3.12.RELEASE.jar
file-collection-filtered:spring-context-4.3.12.RELEASE.jar
file-collection-filtered:hamcrest-core-1.3.jar
file-collection-filtered:junit-4.12.jar
file-collection-filtered:spring-test-4.3.12.RELEASE.jar
file-collection-filtered:spring-boot-1.5.8.RELEASE.jar
file-collection-filtered:spring-boot-autoconfigure-1.5.8.RELEASE.jar
file-collection-filtered:spring-boot-test-1.5.8.RELEASE.jar
file-collection-filtered:spring-boot-test-autoconfigure-1.5.8.RELEASE.jar
file-resolved-config:spring-boot-test-autoconfigure-1.5.8.RELEASE.jar
file-resolved-config:spring-boot-test-1.5.8.RELEASE.jar
file-resolved-config:spring-boot-autoconfigure-1.5.8.RELEASE.jar
file-resolved-config:spring-boot-1.5.8.RELEASE.jar
file-resolved-config:spring-test-4.3.12.RELEASE.jar
file-resolved-config:junit-4.12.jar
file-resolved-config:hamcrest-core-1.3.jar
file-resolved-config:spring-context-4.3.12.RELEASE.jar
file-resolved-config:spring-aop-4.3.12.RELEASE.jar
file-resolved-config:spring-beans-4.3.12.RELEASE.jar
file-resolved-config:spring-expression-4.3.12.RELEASE.jar
file-resolved-config:spring-core-4.3.12.RELEASE.jar
file-resolved-config:commons-logging-1.2.jar
file-resolved-config-filtered:spring-core-4.3.12.RELEASE.jar
file-resolved-config-filtered:commons-logging-1.2.jar
file-resolved-config-filtered:spring-expression-4.3.12.RELEASE.jar
file-resolved-config-filtered:spring-beans-4.3.12.RELEASE.jar
file-resolved-config-filtered:spring-aop-4.3.12.RELEASE.jar
file-resolved-config-filtered:spring-context-4.3.12.RELEASE.jar
file-resolved-config-filtered:hamcrest-core-1.3.jar
file-resolved-config-filtered:junit-4.12.jar
file-resolved-config-filtered:spring-test-4.3.12.RELEASE.jar
file-resolved-config-filtered:spring-boot-1.5.8.RELEASE.jar
file-resolved-config-filtered:spring-boot-autoconfigure-1.5.8.RELEASE.jar
file-resolved-config-filtered:spring-boot-test-1.5.8.RELEASE.jar
file-resolved-config-filtered:spring-boot-test-autoconfigure-1.5.8.RELEASE.jar
file-artifact-resolved-config:spring-boot-test-autoconfigure-1.5.8.RELEASE.jar
file-artifact-resolved-config:spring-boot-test-1.5.8.RELEASE.jar
file-artifact-resolved-config:spring-boot-autoconfigure-1.5.8.RELEASE.jar
file-artifact-resolved-config:spring-boot-1.5.8.RELEASE.jar
file-artifact-resolved-config:spring-test-4.3.12.RELEASE.jar
file-artifact-resolved-config:junit-4.12.jar
file-artifact-resolved-config:hamcrest-core-1.3.jar
file-artifact-resolved-config:spring-context-4.3.12.RELEASE.jar
file-artifact-resolved-config:spring-aop-4.3.12.RELEASE.jar
file-artifact-resolved-config:spring-beans-4.3.12.RELEASE.jar
file-artifact-resolved-config:spring-expression-4.3.12.RELEASE.jar
file-artifact-resolved-config:spring-core-4.3.12.RELEASE.jar
file-artifact-resolved-config:commons-logging-1.2.jar
file-lenient-config:spring-boot-test-autoconfigure-1.5.8.RELEASE.jar
file-lenient-config:spring-boot-test-1.5.8.RELEASE.jar
file-lenient-config:spring-boot-autoconfigure-1.5.8.RELEASE.jar
file-lenient-config:spring-boot-1.5.8.RELEASE.jar
file-lenient-config:spring-test-4.3.12.RELEASE.jar
file-lenient-config:junit-4.12.jar
file-lenient-config:hamcrest-core-1.3.jar
file-lenient-config:spring-context-4.3.12.RELEASE.jar
file-lenient-config:spring-aop-4.3.12.RELEASE.jar
file-lenient-config:spring-beans-4.3.12.RELEASE.jar
file-lenient-config:spring-expression-4.3.12.RELEASE.jar
file-lenient-config:spring-core-4.3.12.RELEASE.jar
file-lenient-config:commons-logging-1.2.jar
file-lenient-config-filtered:spring-core-4.3.12.RELEASE.jar
file-lenient-config-filtered:commons-logging-1.2.jar
file-lenient-config-filtered:spring-expression-4.3.12.RELEASE.jar
file-lenient-config-filtered:spring-beans-4.3.12.RELEASE.jar
file-lenient-config-filtered:spring-aop-4.3.12.RELEASE.jar
file-lenient-config-filtered:spring-context-4.3.12.RELEASE.jar
file-lenient-config-filtered:hamcrest-core-1.3.jar
file-lenient-config-filtered:junit-4.12.jar
file-lenient-config-filtered:spring-test-4.3.12.RELEASE.jar
file-lenient-config-filtered:spring-boot-1.5.8.RELEASE.jar
file-lenient-config-filtered:spring-boot-autoconfigure-1.5.8.RELEASE.jar
file-lenient-config-filtered:spring-boot-test-1.5.8.RELEASE.jar
file-lenient-config-filtered:spring-boot-test-autoconfigure-1.5.8.RELEASE.jar
file-artifact-lenient-config:spring-boot-test-autoconfigure-1.5.8.RELEASE.jar
file-artifact-lenient-config:spring-boot-test-1.5.8.RELEASE.jar
file-artifact-lenient-config:spring-boot-autoconfigure-1.5.8.RELEASE.jar
file-artifact-lenient-config:spring-boot-1.5.8.RELEASE.jar
file-artifact-lenient-config:spring-test-4.3.12.RELEASE.jar
file-artifact-lenient-config:junit-4.12.jar
file-artifact-lenient-config:hamcrest-core-1.3.jar
file-artifact-lenient-config:spring-context-4.3.12.RELEASE.jar
file-artifact-lenient-config:spring-aop-4.3.12.RELEASE.jar
file-artifact-lenient-config:spring-beans-4.3.12.RELEASE.jar
file-artifact-lenient-config:spring-expression-4.3.12.RELEASE.jar
file-artifact-lenient-config:spring-core-4.3.12.RELEASE.jar
file-artifact-lenient-config:commons-logging-1.2.jar
file-artifact-lenient-config-filtered:spring-core-4.3.12.RELEASE.jar
file-artifact-lenient-config-filtered:commons-logging-1.2.jar
file-artifact-lenient-config-filtered:spring-expression-4.3.12.RELEASE.jar
file-artifact-lenient-config-filtered:spring-beans-4.3.12.RELEASE.jar
file-artifact-lenient-config-filtered:spring-aop-4.3.12.RELEASE.jar
file-artifact-lenient-config-filtered:spring-context-4.3.12.RELEASE.jar
file-artifact-lenient-config-filtered:hamcrest-core-1.3.jar
file-artifact-lenient-config-filtered:junit-4.12.jar
file-artifact-lenient-config-filtered:spring-test-4.3.12.RELEASE.jar
file-artifact-lenient-config-filtered:spring-boot-1.5.8.RELEASE.jar
file-artifact-lenient-config-filtered:spring-boot-autoconfigure-1.5.8.RELEASE.jar
file-artifact-lenient-config-filtered:spring-boot-test-1.5.8.RELEASE.jar
file-artifact-lenient-config-filtered:spring-boot-test-autoconfigure-1.5.8.RELEASE.jar
artifact:[org.springframework.boot:spring-boot-test-autoconfigure:1.5.8.RELEASE][spring-boot-test-autoconfigure.jar]
artifact:[org.springframework.boot:spring-boot-test:1.5.8.RELEASE][spring-boot-test.jar]
artifact:[org.springframework.boot:spring-boot-autoconfigure:1.5.8.RELEASE][spring-boot-autoconfigure.jar]
artifact:[org.springframework.boot:spring-boot:1.5.8.RELEASE][spring-boot.jar]
artifact:[org.springframework:spring-test:4.3.12.RELEASE][spring-test.jar]
artifact:[junit:junit:4.12][junit.jar]
artifact:[org.hamcrest:hamcrest-core:1.3][hamcrest-core.jar]
artifact:[org.springframework:spring-context:4.3.12.RELEASE][spring-context.jar]
artifact:[org.springframework:spring-aop:4.3.12.RELEASE][spring-aop.jar]
artifact:[org.springframework:spring-beans:4.3.12.RELEASE][spring-beans.jar]
artifact:[org.springframework:spring-expression:4.3.12.RELEASE][spring-expression.jar]
artifact:[org.springframework:spring-core:4.3.12.RELEASE][spring-core.jar]
artifact:[commons-logging:commons-logging:1.2][commons-logging.jar]
lenient-artifact:[org.springframework.boot:spring-boot-test-autoconfigure:1.5.8.RELEASE][spring-boot-test-autoconfigure.jar]
lenient-artifact:[org.springframework.boot:spring-boot-test:1.5.8.RELEASE][spring-boot-test.jar]
lenient-artifact:[org.springframework.boot:spring-boot-autoconfigure:1.5.8.RELEASE][spring-boot-autoconfigure.jar]
lenient-artifact:[org.springframework.boot:spring-boot:1.5.8.RELEASE][spring-boot.jar]
lenient-artifact:[org.springframework:spring-test:4.3.12.RELEASE][spring-test.jar]
lenient-artifact:[junit:junit:4.12][junit.jar]
lenient-artifact:[org.hamcrest:hamcrest-core:1.3][hamcrest-core.jar]
lenient-artifact:[org.springframework:spring-context:4.3.12.RELEASE][spring-context.jar]
lenient-artifact:[org.springframework:spring-aop:4.3.12.RELEASE][spring-aop.jar]
lenient-artifact:[org.springframework:spring-beans:4.3.12.RELEASE][spring-beans.jar]
lenient-artifact:[org.springframework:spring-expression:4.3.12.RELEASE][spring-expression.jar]
lenient-artifact:[org.springframework:spring-core:4.3.12.RELEASE][spring-core.jar]
lenient-artifact:[commons-logging:commons-logging:1.2][commons-logging.jar]
filtered-lenient-artifact:[org.springframework:spring-core:4.3.12.RELEASE][spring-core.jar]
filtered-lenient-artifact:[commons-logging:commons-logging:1.2][commons-logging.jar]
filtered-lenient-artifact:[org.springframework:spring-expression:4.3.12.RELEASE][spring-expression.jar]
filtered-lenient-artifact:[org.springframework:spring-beans:4.3.12.RELEASE][spring-beans.jar]
filtered-lenient-artifact:[org.springframework:spring-aop:4.3.12.RELEASE][spring-aop.jar]
filtered-lenient-artifact:[org.springframework:spring-context:4.3.12.RELEASE][spring-context.jar]
filtered-lenient-artifact:[org.hamcrest:hamcrest-core:1.3][hamcrest-core.jar]
filtered-lenient-artifact:[junit:junit:4.12][junit.jar]
filtered-lenient-artifact:[org.springframework:spring-test:4.3.12.RELEASE][spring-test.jar]
filtered-lenient-artifact:[org.springframework.boot:spring-boot:1.5.8.RELEASE][spring-boot.jar]
filtered-lenient-artifact:[org.springframework.boot:spring-boot-autoconfigure:1.5.8.RELEASE][spring-boot-autoconfigure.jar]
filtered-lenient-artifact:[org.springframework.boot:spring-boot-test:1.5.8.RELEASE][spring-boot-test.jar]
filtered-lenient-artifact:[org.springframework.boot:spring-boot-test-autoconfigure:1.5.8.RELEASE][spring-boot-test-autoconfigure.jar]
""".split('\n')
    }
}
