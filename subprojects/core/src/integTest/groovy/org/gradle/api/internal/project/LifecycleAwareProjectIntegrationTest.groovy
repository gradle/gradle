/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.project

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

@Requires(IntegTestPreconditions.NotIsolatedProjects)
class LifecycleAwareProjectIntegrationTest extends AbstractIntegrationSpec {

    def 'Different equal instances of LifecycleAwareProject bear the same state'() {
        given:
        settingsFile """
            rootProject.name = 'root'
            include(":a")
        """
        buildFile """
            allprojects {
                ext.foo = "bar"
            }
            allprojects {
                println("\$name foo=\$foo")
            }
        """

        when:
        run "help", "-q"

        then:
        outputContains "root foo=bar\na foo=bar"
    }

    def 'LifecycleAwareProject delegates hasProperty correctly'() {
        given:
        settingsFile """
            rootProject.name = 'root'
            include(":a")
        """
        file("a/build.gradle") << ""
        buildFile """
            project(':a') {
                println("a contains foo: \${it.hasProperty('foo')}")
            }
        """

        when:
        run "help", "-Pfoo=bar"

        then:
        outputContains "a contains foo: true"
    }

    def 'LifecycleAwareProject delegates setProperty correctly'() {
        given:
        settingsFile """
            rootProject.name = 'root'
            include(":a")
        """
        file("a/build.gradle") << ""
        buildFile """
            project(':a') {
                foo='bar1'
            }
            project(':a') {
                println("a foo=\$foo")
            }
        """

        when:
        run "help", "-Pfoo=bar"

        then:
        outputContains "a foo=bar1"
    }

    def 'LifecycleAwareProject wrappers are symmetrically equals to wrapped projects and to each other'() {
        given:
        settingsFile """
            include(":a")
        """

        buildFile """
            ext["wrappedBySubprojects"] = subprojects.toList().get(0)
            ext["wrappedByAllprojects"] = allprojects.toList().get(1)
            ext["wrappedByProject"] = project(":a")
        """

        buildFile("a/build.gradle", """
                def wrappedBySubprojectsAccess = rootProject.ext["wrappedBySubprojects"]
                def wrappedByAllprojectsAccess = rootProject.ext["wrappedByAllprojects"]
                def wrappedByProjectAccess = rootProject.ext["wrappedByProject"]

                def projects = [wrappedByAllprojectsAccess, wrappedBySubprojectsAccess, wrappedByProjectAccess, project]

                [projects, projects].combinations()
                    .findAll { a, b -> a.class != b.class }
                    .forEach { a, b ->
                        if (!a.equals(b)) {
                            println("Equality contract is broken for \${a.class} and \${b.class}")
                        }
                    }
        """)

        when:
        run "help"

        then:
        outputDoesNotContain("Equality contract is broken for ")
    }
}
