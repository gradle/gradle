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

package org.gradle.integtests.resolve.maven

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

class MavenScopesIntegrationTest extends AbstractDependencyResolutionTest {

    def resolve = new ResolveTestFixture(buildFile, "conf")

    def setup() {
        settingsFile << """
            rootProject.name = 'testproject'
        """

        buildFile << """
            plugins {
                id("jvm-ecosystem")
            }

            ${mavenTestRepository()}

            configurations {
                conf
            }
        """

        resolve.prepare()
    }

    def "prefers the runtime variant of a Maven module"() {
        def notRequired = mavenRepo.module('test', 'dont-include-me', '1.0')
        def m1 = mavenRepo.module('test', 'test1', '1.0').publish()
        def m2 = mavenRepo.module('test', 'test2', '1.0').publish()
        def m3 = mavenRepo.module('test', 'test3', '1.0').publish()
        def m4 = mavenRepo.module('test', 'test4', '1.0').publish()
        def m5 = mavenRepo.module('test', 'test5', '1.0')
            .dependsOn(m1, scope: 'compile')
            .dependsOn(m2, scope: 'runtime')
            .dependsOn(notRequired, scope: 'test')
            .dependsOn(notRequired, scope: 'provided')
            .publish()
        def m6 = mavenRepo.module('test', 'test6', '1.0')
            .dependsOn(m3, scope: 'compile')
            .dependsOn(m4, scope: 'runtime')
            .dependsOn(notRequired, scope: 'test')
            .dependsOn(notRequired, scope: 'provided')
            .publish()
        mavenRepo.module('test', 'target', '1.0')
            .dependsOn(m5, scope: 'compile')
            .dependsOn(m6, scope: 'runtime')
            .dependsOn(notRequired, scope: 'test')
            .dependsOn(notRequired, scope: 'provided')
            .publish()

        buildFile << """
            dependencies {
                conf 'test:target:1.0'
            }
        """

        when:
        succeeds 'checkDep'

        then:
        resolve.expectDefaultConfiguration("runtime")
        resolve.expectGraph {
            root(':', ':testproject:') {
                module('test:target:1.0') {
                    module('test:test5:1.0') {
                        module('test:test1:1.0')
                        module('test:test2:1.0')
                    }
                    module('test:test6:1.0') {
                        module('test:test3:1.0')
                        module('test:test4:1.0')
                    }
                }
            }
        }
    }

    def "fails when referencing a scope explicitly by configuration name"() {
        mavenRepo.module('test', 'target', '1.0').publish()

        buildFile << """
            dependencies {
                conf group: 'test', name: 'target', version: '1.0', configuration: 'compile'
            }
        """

        when:
        fails('checkDep')

        then:
        failure.assertHasCause("Could not resolve test:target:1.0.\nRequired by:\n    root project :")
        failure.assertHasCause("Cannot select a variant by configuration name from 'test:target:1.0'.")

        where:
        scopeName << ["compile", "runtime", "test", "provided"]
    }

    def "fails when referencing a scope that does not exist"() {
        mavenRepo.module('test', 'target', '1.0').publish()

        buildFile << """
            dependencies {
                conf group: 'test', name: 'target', version: '1.0', configuration: 'x86_windows'
            }
        """

        when:
        fails('checkDep')

        then:
        failure.assertHasCause("Could not resolve test:target:1.0.\nRequired by:\n    root project :")
        failure.assertHasCause("Cannot select a variant by configuration name from 'test:target:1.0'.")
    }

}
