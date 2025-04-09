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

/**
 * https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-dependencies
 */
class BomSupportPluginsSmokeTest extends AbstractSmokeTest {
    static bomVersion = "3.4.4"
    static bom = "'org.springframework.boot:spring-boot-dependencies:${bomVersion}'"
    // This comes from the BOM
    static springVersion = "6.2.5"

    def 'bom support is provided by #bomSupportProvider'() {
        given:
        def springVersion = springVersion
        def bomVersion = bomVersion

        settingsFile << """
            rootProject.name = 'springbootproject'
        """
        buildFile << """
            plugins {
                id "java"
                ${dependencyManagementPlugin}
            }
            ${mavenCentralRepository()}

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
        resolve.prepare()

        when:
        def runner = runner('checkDep')
        runner.build()

        then:
        def micrometerObservationDeps = {
            edge("io.micrometer:micrometer-commons:1.14.5", "io.micrometer:micrometer-commons:1.14.5").byReason(reason)
        }
        def junitDeps = {
            switch (bomSupportProvider) {
                case "gradle":
                    edge("org.hamcrest:hamcrest-core:1.3", "org.hamcrest:hamcrest-core:2.2") {
                        byReason("conflict resolution: between versions 2.2 and 1.3")
                        edge("org.hamcrest:hamcrest:2.2", "org.hamcrest:hamcrest:2.2")
                    }
                    break
                case "nebula recommender plugin":
                    module("org.hamcrest:hamcrest-core:1.3").byReason(reason)
                    break
                case "spring dependency management plugin":
                    edge("org.hamcrest:hamcrest-core:1.3", "org.hamcrest:hamcrest-core:2.2") {
                        byReason(reason)
                        variant("compile", [
                            'org.gradle.category': 'library',
                            'org.gradle.libraryelements': 'jar',
                            'org.gradle.status': 'release',
                            'org.gradle.usage': 'java-api'
                        ])
                        edge("org.hamcrest:hamcrest:2.2", "org.hamcrest:hamcrest:2.2") {
                            byReason(reason)
                            variant("compile", [
                                'org.gradle.category': 'library',
                                'org.gradle.libraryelements': 'jar',
                                'org.gradle.status': 'release',
                                'org.gradle.usage': 'java-api'
                            ])
                        }
                    }
                    break
            }
        }
        def springCoreDeps = {
            module("org.springframework:spring-jcl:${springVersion}").byReason(reason)
        }
        def springExpressionDeps = {
            module("org.springframework:spring-core:${springVersion}")
        }
        def springBeansDeps = {
            module("org.springframework:spring-core:${springVersion}")
        }
        def springAopDeps = {
            module("org.springframework:spring-beans:${springVersion}")
            module("org.springframework:spring-core:${springVersion}")
        }
        def springContextDeps = {
            module("org.springframework:spring-aop:${springVersion}", springAopDeps).byReason(reason)
            module("org.springframework:spring-beans:${springVersion}", springBeansDeps).byReason(reason)
            module("org.springframework:spring-core:${springVersion}")
            module("org.springframework:spring-expression:${springVersion}", springExpressionDeps).byReason(reason)
            module("io.micrometer:micrometer-observation:1.14.5", micrometerObservationDeps).byReason(reason)
        }
        def springTestDeps = {
            module("org.springframework:spring-core:${springVersion}")
        }
        def springBootDeps = {
            module("org.springframework:spring-core:${springVersion}", springCoreDeps).byReason(reason)
            module("org.springframework:spring-context:${springVersion}", springContextDeps).byReason(reason)
        }
        def springBootAutoconfigureDeps = {
            module("org.springframework.boot:spring-boot:$bomVersion")
        }
        def springBootTestDeps = {
            module("org.springframework.boot:spring-boot:$bomVersion")
            module("org.springframework:spring-test:$springVersion")
        }
        def springBootTestAutoconfigureDeps = {
            module("org.springframework.boot:spring-boot-test:$bomVersion")
            module("org.springframework.boot:spring-boot-autoconfigure:$bomVersion")
            module("org.springframework.boot:spring-boot:$bomVersion")
        }

        resolve.expectDefaultConfiguration('compile')
        resolve.expectGraph {
            root(':', ':springbootproject:') {
                if (directBomDependency) {
                    module("org.springframework.boot:spring-boot-dependencies:$bomVersion:${bomSupportProvider == 'gradle' ? 'platform-compile' : 'compile'}") {
                        constraint("org.springframework:spring-core:${springVersion}")
                        constraint("org.springframework:spring-aop:${springVersion}")
                        constraint("org.springframework:spring-beans:${springVersion}")
                        constraint("org.springframework:spring-context:${springVersion}")
                        constraint("org.springframework:spring-expression:${springVersion}")
                        constraint("org.springframework:spring-test:${springVersion}")
                        constraint("org.springframework:spring-jcl:${springVersion}")
                        constraint("org.springframework.boot:spring-boot:$bomVersion")
                        constraint("org.springframework.boot:spring-boot-test:$bomVersion")
                        constraint("org.springframework.boot:spring-boot-autoconfigure:$bomVersion")
                        constraint("org.springframework.boot:spring-boot-test-autoconfigure:$bomVersion")
                        constraint("junit:junit:4.13.2")
                        constraint("org.hamcrest:hamcrest:2.2")
                        constraint("org.hamcrest:hamcrest-core:2.2")
                        constraint("io.micrometer:micrometer-commons:1.14.5")
                        constraint("io.micrometer:micrometer-observation:1.14.5")
                        noArtifacts()
                    }
                }
                edge("org.springframework.boot:spring-boot-test-autoconfigure", "org.springframework.boot:spring-boot-test-autoconfigure:$bomVersion", springBootTestAutoconfigureDeps).byReason(reason)
                edge("org.springframework.boot:spring-boot-test", "org.springframework.boot:spring-boot-test:$bomVersion", springBootTestDeps).byReason(reason)
                edge("org.springframework.boot:spring-boot-autoconfigure", "org.springframework.boot:spring-boot-autoconfigure:$bomVersion", springBootAutoconfigureDeps).byReason(reason)
                edge("org.springframework.boot:spring-boot", "org.springframework.boot:spring-boot:$bomVersion", springBootDeps).byReason(reason)
                edge("org.springframework:spring-test", "org.springframework:spring-test:${springVersion}", springTestDeps).byReason(reason)
                edge("junit:junit", "junit:junit:4.13.2", junitDeps).byReason(reason)
            }
            nodes.each {
                if (directBomDependency) {
                    it.maybeByConstraint()
                } else if (reason == "requested") {
                    it.maybeSelectedByRule()
                }
            }
        }

        where:
        bomSupportProvider                    | directBomDependency | reason             | bomDeclaration                                        | dependencyManagementPlugin
        "gradle"                              | true                | "requested"        | "dependencies { implementation platform($bom) }"      | ""
        "nebula recommender plugin"           | false               | "requested"        | "dependencyRecommendations { mavenBom module: $bom }" | "id 'com.netflix.nebula.dependency-recommender' version '${AbstractSmokeTest.TestedVersions.nebulaDependencyRecommender}'"
        "spring dependency management plugin" | false               | "selected by rule" | "dependencyManagement { imports { mavenBom $bom } }"  | "id 'io.spring.dependency-management' version '${AbstractSmokeTest.TestedVersions.springDependencyManagement}'"
    }
}
