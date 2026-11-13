/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class AntBuilderDeprecationIntegrationTest extends AbstractIntegrationSpec {

    def "using #usage of the project is deprecated"() {
        buildFile << usage

        when:
        executer.expectDocumentedDeprecationWarning(deprecation("The Project.${method} method has been deprecated."))

        then:
        succeeds("help")
        outputContains("[ant:echo] hello")

        where:
        usage                                                       | method
        "ant.echo(message: 'hello')"                                | "getAnt()"
        "ant { echo(message: 'hello') }"                            | "ant(Closure)"
        "ant({ it.echo(message: 'hello') } as Action)"              | "ant(Action)"
        "createAntBuilder().echo(message: 'hello')"                 | "createAntBuilder()"
    }

    def "using the Ant builder of a task is deprecated"() {
        buildFile("""
            tasks.register("hello") {
                doLast {
                    ant.echo(message: 'hello')
                }
            }
        """)

        when:
        executer.expectDocumentedDeprecationWarning(deprecation("The Task.getAnt() method has been deprecated."))

        then:
        succeeds("hello")
        outputContains("[ant:echo] hello")
    }

    def "importing an Ant build only warns about the Ant builder"() {
        file("build.xml") << """
            <project>
                <target name="hello">
                    <echo message="hello"/>
                </target>
            </project>
        """
        buildFile("""
            ant.importBuild("build.xml")
        """)

        when:
        executer.expectDocumentedDeprecationWarning(deprecation("The Project.getAnt() method has been deprecated."))

        then:
        succeeds("hello")
        outputContains("[ant:echo] hello")
    }

    def "adding a file collection to an Ant builder is deprecated"() {
        file("dir/a.txt") << "a"
        file("dir/b.txt") << "b"
        buildFile("""
            def builder = createAntBuilder()
            def tree = fileTree("dir")
            tree.include("a.txt")
            builder.copy(todir: "out") {
                tree.addToAntBuilder(delegate, "fileset", FileCollection.AntType.FileSet)
            }
        """)

        when:
        executer.expectDocumentedDeprecationWarning(deprecation("The Project.createAntBuilder() method has been deprecated."))
        executer.expectDocumentedDeprecationWarning(deprecation("The FileCollection.addToAntBuilder(Object, String, AntType) method has been deprecated."))

        then:
        succeeds("help")
        file("out").assertHasDescendants("a.txt")
    }

    def "adding a pattern set to an Ant builder is deprecated"() {
        file("dir/a.txt") << "a"
        file("dir/b.txt") << "b"
        buildFile("""
            def builder = createAntBuilder()
            def patterns = new PatternSet().include("a.txt")
            builder.copy(todir: "out") {
                fileset(dir: "dir") {
                    patterns.addToAntBuilder(delegate, null)
                }
            }
        """)

        when:
        executer.expectDocumentedDeprecationWarning(deprecation("The Project.createAntBuilder() method has been deprecated."))
        executer.expectDocumentedDeprecationWarning(deprecation("The PatternSet.addToAntBuilder(Object, String) method has been deprecated."))

        then:
        succeeds("help")
        file("out").assertHasDescendants("a.txt")
    }

    def "creating an AntTarget task is deprecated"() {
        buildFile("""
            tasks.register("hello", AntTarget)
        """)

        when:
        executer.expectDocumentedDeprecationWarning(deprecation("The org.gradle.api.tasks.ant.AntTarget type has been deprecated."))

        then:
        succeeds("help", "--task", "hello")
    }

    def "listing the project properties does not warn about the Ant builder"() {
        expect:
        succeeds("properties")
        outputContains("ant: org.gradle.api.internal.project.DefaultAntBuilder")
    }

    private static String deprecation(String summary) {
        "${summary} This is scheduled to be removed in Gradle 10. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_ant_builder"
    }

}
