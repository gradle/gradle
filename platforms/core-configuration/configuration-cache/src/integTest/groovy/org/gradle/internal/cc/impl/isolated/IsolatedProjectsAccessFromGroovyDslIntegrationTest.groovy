/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.cc.impl.isolated

import org.gradle.api.provider.Property
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Issue

class IsolatedProjectsAccessFromGroovyDslIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {
    def "reports problem when build script uses #block block to apply plugins to another project"() {
        createDirs("a", "b")
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile << """
            $block {
                plugins.apply('java-library')
            }
        """

        when:
        isolatedProjectsFails("assemble")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a", ":b")
            problem("Build file 'build.gradle': line 3: Project ':' cannot access 'Project.plugins' functionality on $message", 2)
        }

        where:
        block         | message
        "allprojects" | "subprojects via 'allprojects'"
        "subprojects" | "subprojects"
    }

    def "reports problem when build script uses #block block to access dynamically added elements"() {
        createDirs("a", "b")
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile << """
            $block {
                plugins.apply('java-library')
                java { }
                java.sourceCompatibility
            }
        """

        when:
        isolatedProjectsFails("assemble")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a", ":b")
            problem("Build file 'build.gradle': line 3: Project ':' cannot access 'Project.plugins' functionality on $message", 2)
            problem("Build file 'build.gradle': line 4: Project ':' cannot access 'java' extension on $message", 2)
            problem("Build file 'build.gradle': line 5: Project ':' cannot access 'java' extension on $message", 2)
        }

        where:
        block                               | message
        "allprojects"                       | "subprojects via 'allprojects'"
        "subprojects"                       | "subprojects"
        "configure(childProjects.values())" | "child projects"
    }

    def "reports problem when build script uses #property property to apply plugins to another project"() {
        createDirs("a", "b")
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile << """
            ${property}.each {
                it.plugins.apply('java-library')
            }
        """

        when:
        isolatedProjectsFails("assemble")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a", ":b")
            problem("Build file 'build.gradle': line 3: Project ':' cannot access 'Project.plugins' functionality on $message", 2)
        }

        where:
        property                 | message
        "allprojects"            | "subprojects via 'allprojects'"
        "subprojects"            | "subprojects"
        "childProjects.values()" | "child projects"
    }

    def "reports problem when build script uses project() block to apply plugins to another project"() {
        createDirs("a", "b")
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile << """
            project(':a') {
                plugins.apply('java-library')
            }
        """

        when:
        isolatedProjectsFails("assemble")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a", ":b")
            problem("Build file 'build.gradle': line 3: Project ':' cannot access 'Project.plugins' functionality on another project ':a'")
        }
    }

    def "reports problem when root project build script uses #expression method to apply plugins to another project"() {
        createDirs("a", "b")
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile << """
            ${expression}.plugins.apply('java-library')
        """

        when:
        isolatedProjectsFails("assemble")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a", ":b")
            problem("Build file 'build.gradle': line 2: Project ':' cannot access 'Project.plugins' functionality on another project ':a'")
        }

        where:
        expression          | _
        "project(':a')"     | _
        "findProject(':a')" | _
    }

    def "reports problem when child project build script uses #expression method to apply plugins to sibling project"() {
        createDirs("a", "b")
        settingsFile << """
            include("a")
            include("b")
        """
        file("a/build.gradle") << """
            ${expression}.plugins.apply('java-library')
        """

        when:
        isolatedProjectsFails("assemble")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a", ":b")
            problem("Build file 'a/build.gradle': line 2: Project ':a' cannot access 'Project.plugins' functionality on another project '$target'")
        }

        where:
        expression          | target
        "project(':b')"     | ":b"
        "findProject(':b')" | ":b"
        "rootProject"       | ":"
        "parent"            | ":"
    }

    def "reports problem when root project build script uses chain of methods #chain { } to apply plugins to other projects"() {
        createDirs("a", "b")
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile << """
            $chain { it.plugins.apply('java-library') }
        """

        when:
        isolatedProjectsFails("assemble")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a", ":b")
            problem("Build file 'build.gradle': line 2: Project ':' cannot access 'Project.plugins' functionality on $message", 2)
        }

        where:
        chain                                           | message
        "project(':').allprojects"                      | "subprojects via 'allprojects'"
        "project(':').subprojects"                      | "subprojects"
        "project('b').project(':').allprojects"         | "subprojects via 'allprojects'"
        "project('b').project(':').subprojects"         | "subprojects"
        "project(':').allprojects.each"                 | "subprojects via 'allprojects'"
        "project(':').subprojects.each"                 | "subprojects"
        "project('b').project(':').allprojects.each"    | "subprojects via 'allprojects'"
        "project('b').project(':').subprojects.each"    | "subprojects"
        "findProject('b').findProject(':').subprojects" | "subprojects"
    }

    def "reports problem when project build script uses chain of methods #chain { } to apply plugins to other projects"() {
        createDirs("a", "b")
        settingsFile << """
            include("a")
            include("b")
        """
        file("a/build.gradle") << """
            $chain { it.plugins.apply('java-library') }
        """

        when:
        isolatedProjectsFails("assemble")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a", ":b")
            problem("Build file 'a/build.gradle': line 2: Project ':a' cannot access 'Project.plugins' functionality on $message")
        }

        where:
        chain                                    | message
        "project(':').subprojects"               | "subprojects of project ':'"
        "project(':').subprojects.each"          | "subprojects of project ':'"
        "rootProject.subprojects"                | "subprojects of project ':'"
        "parent.subprojects"                     | "subprojects of project ':'"
        "project(':b').project(':').subprojects" | "subprojects of project ':'"
        "project(':b').parent.subprojects"       | "subprojects of project ':'"
        "project(':').project('b')"              | "another project ':b'"
        "findProject(':').findProject('b').with" | "another project ':b'"
    }

    def "reports problem when project build script uses chain of methods #chain { } to apply plugins to all projects"() {
        createDirs("a", "b")
        settingsFile << """
            include("a")
            include("b")
        """
        file("a/build.gradle") << """
            $chain { it.plugins.apply('java-library') }
        """

        when:
        isolatedProjectsFails("assemble")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a", ":b")
            problem("Build file 'a/build.gradle': line 2: Project ':a' cannot access 'Project.plugins' functionality on subprojects of project ':'", 2)
        }

        where:
        chain                           | _
        "project(':').allprojects"      | _
        "project(':').allprojects.each" | _
    }

    def "reports cross-project model access in Gradle.#invocation"() {
        createDirs("a", "b")
        settingsFile << """
            include("a")
            include("b")
        """
        file("a/build.gradle") << """
            configure(gradle) {
                ${invocation} { println(it.buildDir) }
            }
        """

        when:
        isolatedProjectsFails(":a:help", ":b:help")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a", ":b")
            problem("Build file 'a/build.gradle': line 3: Project ':a' cannot access 'Project.buildDir' functionality on $message", accessedProjects)
        }

        where:
        invocation               | accessedProjects | message
        "configure(rootProject)" | 1                | "another project ':'"
        "rootProject"            | 1                | "another project ':'"
        "allprojects"            | 2                | "subprojects of project ':'"
        // TODO:isolated fix expectations for parallel configuration
//        "beforeProject"          | 1                | "another project ':b'"
//        "afterProject"           | 1                | "another project ':b'"
    }

    @ToBeImplemented("when Isolated Projects becomes incremental for task execution")
    def "reports cross-project model access in composite build access to Gradle.#invocation"() {
        createDirs("a", "include")
        settingsFile << """
            include("a")
            includeBuild("include")
        """
        file("include/build.gradle") << """
            gradle.${invocation}.allprojects { if (it.path == ":") println(it.buildDir) }
        """

        when:
        isolatedProjectsFails(":include:help")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":include", ":", ":a")
            // TODO:isolated expected behavior for incremental configuration
//            projectsConfigured(":include")
            problem("Build file 'include/build.gradle': line 2: Project ':include' cannot access 'Project.buildDir' functionality on subprojects of project ':'")
        }

        where:
        invocation | _
        "parent"   | _
        "root"     | _
    }

    def "reports cross-project model access from a listener added to Gradle.projectsEvaluated"() {
        createDirs("a", "b")
        settingsFile << """
            include("a")
            include("b")
        """
        file("a/build.gradle") << """
            gradle.projectsEvaluated {
                it.allprojects { println it.buildDir }
            }
        """

        when:
        isolatedProjectsFails(":a:help", ":b:help")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a", ":b")
            problem("Build file 'a/build.gradle': line 3: Project ':a' cannot access 'Project.buildDir' functionality on subprojects of project ':'", 2)
        }
    }

    @ToBeImplemented("when Isolated Projects becomes incremental for task execution")
    def "reports cross-project model from ProjectEvaluationListener registered in Gradle.#invocation"() {
        createDirs("a", "b")
        settingsFile << """
            include("a")
            include("b")
        """
        file("a/build.gradle") << """
            class MyListener implements ProjectEvaluationListener {
                void beforeEvaluate(Project project) { }
                void afterEvaluate(Project project, ProjectState projectState) {
                    println project.buildDir
                }
            }
            gradle.$invocation(new MyListener())
        """

        when:
        // TODO:isolated expected behavior for incremental configuration
//        isolatedProjectsFails(":a:help", ":b:help")
        isolatedProjectsRun(":a:help", ":b:help")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":a", ":b")
        }
        // TODO:isolated expected behavior for incremental configuration
//        fixture.assertStateStoredAndDiscarded {
//            projectsConfigured(":", ":a", ":b")
//            problem("Build file 'a/build.gradle': line 5: Project ':a' cannot access 'Project.buildDir' functionality on another project ':b'")
//        }

        where:
        invocation                     | _
        "addListener"                  | _
        "addProjectEvaluationListener" | _
    }

    def "listener removal works properly in Gradle.#add + Gradle.#remove"() {
        createDirs("a", "b")
        settingsFile << """
            include("a")
            include("b")
        """
        file("a/build.gradle") << """
            class MyListener implements ProjectEvaluationListener {
                void beforeEvaluate(Project project) { }
                void afterEvaluate(Project project, ProjectState projectState) {
                    println project.buildDir
                }
            }
            def listener = new MyListener()
            gradle.$add(listener)
            gradle.$remove(listener)
        """

        when:
        isolatedProjectsRun(":a:help", ":b:help")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":a", ":b")
        }

        where:
        add                            | remove
        "addListener"                  | "removeListener"
        "addProjectEvaluationListener" | "removeProjectEvaluationListener"
    }

    def "task graph should track cross-project model access in listeners with `#statement`"() {
        createDirs("a")
        file("settings.gradle") << "include('a')"
        file("build.gradle") << """
            class MyListener implements TaskExecutionGraphListener {
                void graphPopulated(TaskExecutionGraph graph) {
                    graph.hasTask(":x:unknown")
                }
            }
            $statement
        """

        when:
        isolatedProjectsFails(":help", ":a:help")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a")
            problem("Build file 'build.gradle': line $line: Project ':' cannot access the tasks in the task graph that were created by other projects")
            failureCauseContains("Project ':' cannot access the tasks in the task graph that were created by other projects; tried to access ':x:unknown'")
        }

        where:
        statement                                                             | line
        "gradle.taskGraph.whenReady { graph -> graph.hasTask(':x:unknown') }" | 7
        "gradle.taskGraph.addTaskExecutionGraphListener(new MyListener())"    | 4
    }

    def "checking cross-project model access in task graph call `#statement` with #tasksToRun, should succeed: #shouldSucceed"() {
        createDirs("b")
        settingsFile << """
            include("b")
        """
        file("build.gradle") << """
            plugins {
                id("java")
            }
        """
        file("b/build.gradle") << """
            plugins {
                id("java")
            }
            dependencies {
                implementation(project(":"))
            }

            gradle.taskGraph.whenReady { graph ->
                $statement
            }
        """

        when:
        if (shouldSucceed) {
            isolatedProjectsRun(*tasksToRun)
        } else {
            isolatedProjectsFails(*tasksToRun)
        }

        then:
        if (shouldSucceed) {
            fixture.assertStateStored {
                projectsConfigured(":", ":b")
            }
        } else {
            fixture.assertStateStoredAndDiscarded {
                projectsConfigured(":", ":b")
                problem("Build file 'b/build.gradle': line 10: Project ':b' cannot access the tasks in the task graph that were created by other projects")
            }
        }

        where:
        statement                            | tasksToRun                       | shouldSucceed
        "graph.hasTask(':b:bTask')"          | [":b:help"]                      | true
        "graph.hasTask(':b:help')"           | [":b:help"]                      | true
        "graph.hasTask(':help')"             | [":b:help"]                      | false
        "graph.hasTask(':x:unknown')"        | [":b:help"]                      | false
        "graph.allTasks"                     | [":b:help"]                      | false
        "graph.allTasks"                     | [":b:help", ":help"]             | false
        "graph.getDependencies(help)"        | [":b:help"]                      | false
        "graph.getDependencies(compileJava)" | [":b:compileJava"]               | false
        "graph.filteredTasks"                | [":b:compileJava"]               | false
        "graph.filteredTasks"                | [":b:compileJava", "-x:classes"] | false
    }

    def "reports cross-project model access on #kind lookup in the parent project using `#expr`"() {
        createDirs("a")
        settingsFile << """
            include("a")
        """
        file("build.gradle") << """
            $setExpr
        """
        file("a/build.gradle") << """
            println($expr)
        """

        when:
        isolatedProjectsFails(":a:help")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a")
            problem("Build file 'a/build.gradle': line 2: Project ':a' cannot dynamically look up a $kind in the parent project ':'")
        }

        where:
        kind       | setExpr         | expr
        "property" | "ext.foo = 1"   | "foo"
        "property" | "ext.foo = 1"   | "hasProperty('foo')"
        "property" | "ext.foo = 1"   | "property('foo')"
        "property" | "ext.foo = 1"   | "findProperty('foo')"
        "property" | "ext.foo = 1"   | "getProperty('foo')"
        "property" | "ext.foo = 1"   | "properties"
        "method"   | "def foo() { }" | "foo()"
    }

    def 'no duplicate problems reported for dynamic property lookup in transitive parents'() {
        createDirs("sub", "sub/sub-a", "sub/sub-b")
        settingsFile << """
            include(":sub")
            include(":sub:sub-a")
            include(":sub:sub-b")
        """
        buildFile << """
            ext.foo = "fooValue"
        """
        file("sub/sub-a/build.gradle") << """
            println(foo)
        """
        file("sub/sub-b/build.gradle") << """
            println(foo)
        """

        when:
        isolatedProjectsFails(":sub:sub-a:help", ":sub:sub-b:help")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":sub", ":sub:sub-a", ":sub:sub-b")
            problem("Build file 'sub/sub-a/build.gradle': line 2: Project ':sub:sub-a' cannot dynamically look up a property in the parent project ':sub'")
            problem("Build file 'sub/sub-b/build.gradle': line 2: Project ':sub:sub-b' cannot dynamically look up a property in the parent project ':sub'")
        }
    }

    def 'user code in #description dynamic property lookup triggers a new isolation problem'() {
        settingsFile << """
            include(":sub")
            include(":sub:sub-sub")
        """
        buildFile << """
            ext.foo = "fooValue"
        """
        file("sub/build.gradle") << """
            abstract class Unusual {
                Project p
                @Inject Unusual(Project p) { this.p = p }
                Object getBar() {
                    $lookup
                }
            }

            // Convention plugin members are exposed as members of the project
            convention.plugins['unusual'] = objects.newInstance(Unusual)
        """
        file("sub/sub-sub/build.gradle") << """
            println(bar)
        """
        executer.expectDocumentedDeprecationWarning(
            "The org.gradle.api.plugins.Convention type has been deprecated. " +
                "This is scheduled to be removed in Gradle 9.0. " +
                "Consult the upgrading guide for further information: " +
                "https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_access_to_conventions"
        )

        when:
        isolatedProjectsFails(":sub:sub-sub:help")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":sub", ":sub:sub-sub")
            problem("Build file 'sub/build.gradle': line 6: Project ':sub' cannot dynamically look up a property in the parent project ':'")
            problem("Build file 'sub/sub-sub/build.gradle': line 2: Project ':sub:sub-sub' cannot dynamically look up a property in the parent project ':sub'")
        }

        where:
        description | lookup
        "stringy"   | 'p.property("foo")'
        "direct"    | 'p.foo'
    }

    @Issue("https://github.com/gradle/gradle/issues/22949")
    def "invocations of GroovyObject methods on DefaultProject track the dynamic call context"() {
        createDirs("a")
        settingsFile << """
            include("a")
        """
        file("build.gradle") << """
            ext.foo = 1
            def bar() { }
        """
        file("a/build.gradle") << """
            ext.baz = 0

            def o = project as GroovyObject
            o.getProperty('foo')
            o.invokeMethod('bar', new Object[] {})
            o.setProperty('baz', 1)

            assert project.hasProperty('baz')
        """

        when:
        isolatedProjectsFails(":a:help")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a")
            problem("Build file 'a/build.gradle': line 5: Project ':a' cannot dynamically look up a property in the parent project ':'")
            problem("Build file 'a/build.gradle': line 6: Project ':a' cannot dynamically look up a method in the parent project ':'")
        }
    }

    def "reports problem when cross-project access happens in a script-owned configure-action"() {
        given:
        createDirs("a", "aa")
        settingsFile """
            include(":a")
            include(":a:aa")
        """
        buildFile """
            project.extensions.extraProperties["projectProperty"] = "hello"
        """

        buildFile "a/aa/myscript.gradle", """
            // Using `withPlugin` as an example of a configure action
            project.pluginManager.withPlugin('base', {
                println("My property: " + projectProperty)
            })
        """
        buildFile "a/aa/build.gradle", """
            plugins {
                id "base"
            }
            apply from: 'myscript.gradle'
        """

        when:
        isolatedProjectsFails("help")

        then:
        outputContains("My property: hello")

        // an additional subproject demonstrates that the problems are duplicated as the property lookup traverses up the project hierarchy
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a", ":a:aa")
            problem("Script 'a/aa/myscript.gradle': line 4: Project ':a' cannot dynamically look up a property in the parent project ':'")
            problem("Script 'a/aa/myscript.gradle': line 4: Project ':a:aa' cannot dynamically look up a property in the parent project ':a'")
        }
    }

    def "build script can query basic details of projects in allprojects block"() {
        createDirs("a", "b")
        settingsFile << """
            rootProject.name = "root"
            include("a")
            include("b")
        """
        buildFile << """
            plugins {
                id('java-library')
            }
            allprojects {
                println("project name = " + name)
                println("project path = " + path)
                println("project projectDir = " + projectDir)
                println("project rootDir = " + rootDir)
                println("project toString = " + it)
                it.name
                project.name
                project.path
                allprojects { }
            }
        """

        when:
        isolatedProjectsRun("assemble")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":a", ":b")
        }
        outputContains("project name = root")
        outputContains("project name = a")
        outputContains("project name = b")
    }

    @ToBeImplemented
    def "build script can query basic details of projects in a #description called from allprojects block"() {
        createDirs("a", "b")
        settingsFile << """
            rootProject.name = "root"
            include("a")
            include("b")
        """
        buildFile << """
            $declaration printInfo(def p) {
                println("project name = " + p.name)
            }

            allprojects {
                printInfo(it)
            }

            task something {}
        """

        when:
        // TODO:isolated should succeed without problems
        isolatedProjectsFails("something")

        then:
        outputContains("project name = root")
        outputContains("project name = a")
        outputContains("project name = b")

        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a", ":b")
            problem("Build file 'build.gradle': line 7: Project ':' cannot access 'printInfo' extension on subprojects via 'allprojects'", 2)
        }

        where:
        description       | declaration
        "function"        | "def"
        "static function" | "static def"
    }

    def "build script can query basic details of isolated projects in allprojects block"() {
        createDirs("a", "b")
        settingsFile << """
            rootProject.name = "root"
            include("a")
            include("b")
        """
        buildFile << """
            plugins {
                id('java-library')
            }
            allprojects { p ->
                def isolatedProject = p.isolated

                println("project name = " + isolatedProject.name)
                println("project path = " + isolatedProject.path)
                println("project buildTreePath = " + isolatedProject.buildTreePath)
                println("project projectDir = " + isolatedProject.projectDirectory)
                println("project rootDir = " + isolatedProject.rootProject.projectDirectory)
            }
        """

        when:
        isolatedProjectsRun("assemble")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":a", ":b")
        }
        outputContains("project name = root")
        outputContains("project name = a")
        outputContains("project name = b")
    }

    def "reports problem on #expr buildDependencies.getDependencies(...)"() {
        given:
        buildFile << """
            $setup
            def buildable = $expr
            configurations.create("test")
            println(buildable.buildDependencies.getDependencies(null))
        """

        when:
        if (expr == "dependencies.project([path: ':', configuration: 'test'])") {
            executer.expectDocumentedDeprecationWarning("Accessing the build dependencies of project dependency ':' has been deprecated. This will fail with an error in Gradle 9.0. Add the dependency to a resolvable configuration and use the configuration to track task dependencies. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecate_self_resolving_dependency")
        }
        isolatedProjectsFails(":help")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":")
            problem("Build file 'build.gradle': line 5: Project ':' cannot access task dependencies directly")
        }

        where:
        expr                                                       | setup
        "files()"                                                  | ""
        "files() + files()"                                        | ""
        "fileTree(buildDir)"                                       | ""
        "fileTree(buildDir) + fileTree(rootDir)"                   | ""
        "resources.text.fromFile('1.txt', 'UTF-8')"                | ""
        "fromTask"                                                 | "def fromTask = new Object() { def buildDependencies = tasks.help.taskDependencies }"
        "artifacts.add('default', new File('a.txt'))"              | "configurations.create('default')"
        "dependencies.project([path: ':', configuration: 'test'])" | "plugins { id('java') }"
        "configurations.compileClasspath"                          | "plugins { id('java') }"
        "configurations.compileClasspath.dependencies"             | "plugins { id('java') }"
        "sourceSets.main.java"                                     | "plugins { id('java') }"
        "sourceSets.main.output"                                   | "plugins { id('java') }"
        "configurations.apiElements.allArtifacts"                  | "plugins { id('java') }"
        "configurations.apiElements.allArtifacts.toList()[0]"      | "plugins { id('java') }"
        "testing.suites.test"                                      | "plugins { id('java'); id('jvm-test-suite') }"
        "testing.suites.test.targets.toList()[0]"                  | "plugins { id('java'); id('jvm-test-suite') }"
        "publishing.publications.maven.artifacts.toList()[0]"      | "plugins { id('java'); id('maven-publish') }; publishing.publications.create('maven', MavenPublication) { from(components['java']) }"
    }

    def "mentions the specific project and build file in getDependencies(...) problems"() {
        given:
        createDirs("a", "a/b")
        settingsFile << """
            include(":a")
            include(":a:b")
        """
        file("a/b/build.gradle") << """
            def buildable = files()
            println(buildable.buildDependencies.getDependencies(null))
        """

        when:
        isolatedProjectsFails(":a:b:help")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a", ":a:b")
            problem("Build file 'a/b/build.gradle': line 3: Project ':a:b' cannot access task dependencies directly")
        }
    }

    def "project can access itself"() {
        createDirs("a", "b")
        settingsFile << """
            rootProject.name = "root"
            include("a")
            include("b")
        """
        buildFile << """
            rootProject.plugins.apply('java-library')
            project(':').plugins.apply('java-library')
            project(':a').parent.plugins.apply('java-library')
        """

        when:
        isolatedProjectsRun("assemble")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":a", ":b")
        }
    }

    def "fails on invoke method of unconfigured project"() {
        given:
        settingsFile << """
            include(':a')
            include(':a:sub')
        """

        file("a/build.gradle") << """
            def unconfiguredProject = project(':a:sub')
            println 'Unconfigured project value = ' + unconfiguredProject.foo()
        """

        file("a/sub/build.gradle") << """
            String foo(){ 'configured' }
        """

        when:
        isolatedProjectsFails 'help', WARN_PROBLEMS_CLI_OPT

        then:
        failure.assertHasErrorOutput("Could not find method foo() for arguments [] on project ':a:sub' of type org.gradle.api.Project")
        problems.assertResultHasProblems(failure) {
            withProblem("Build file '${relativePath('a/build.gradle')}': line 3: Project ':a' cannot access 'foo' extension on another project ':a:sub'")
        }

    }

    def "fails on access property of unconfigured project"() {
        given:
        settingsFile << """
            include(':a')
            include(':a:sub')
        """

        file("a/build.gradle") << """
            def unconfiguredProject = project(':a:sub')
            println 'Unconfigured project value = ' + unconfiguredProject.myExtension.get()
        """

        file("a/sub/build.gradle") << """
            import ${Property.name}

            interface MyExtension {
                Property<String> getFoo()
            }

            def myExtension = extensions.create('myExtension', MyExtension)
            myExtension.foo.set('configured')
        """

        when:
        isolatedProjectsFails 'help', WARN_PROBLEMS_CLI_OPT

        then:
        failure.assertHasErrorOutput("Could not get unknown property 'myExtension' for project ':a:sub' of type org.gradle.api.Project")
        problems.assertResultHasProblems(failure) {
            withProblem("Build file '${relativePath('a/build.gradle')}': line 3: Project ':a' cannot access 'myExtension' extension on another project ':a:sub'")
        }
    }

    def "supports nested structure of build layout"() {
        settingsFile << """
            include ':a'
            include ':a:tests'
            include ':a:tests:integ-tests'
        """
        file("a/build.gradle") << ""
        file("a/tests/build.gradle") << ""
        file("a/tests/integ-tests/build.gradle") << ""

        when:
        isolatedProjectsRun 'build'

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":a", ":a:tests", ":a:tests:integ-tests")
        }
    }

    def "can use #api(Closure) API added by runtime decoration"() {
        settingsFile << """
            include ':a'
        """
        file("a/build.gradle") << ""
        buildFile << """
            project(':a') {
                $invocation
            }
        """

        when:
        isolatedProjectsFails 'help'

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a")
            problem("Build file 'build.gradle': line 3: Project ':' cannot access 'Project.$api' functionality on another project ':a'", 1)
        }

        where:
        api                 | invocation
        "normalization"     | "normalization { runtimeClasspath{} }"
        "dependencyLocking" | "dependencyLocking { lockAllConfigurations() }"
    }

    def 'child project access preserves a referrer'() {
        settingsFile """
            include(":a")
        """

        buildFile """
            version = "v1"
            println "root.version = " + childProjects.values().first().parent.version
        """

        file("a/build.gradle") << """
            version = "v1"
            println "a.version = " + parent.childProjects.values().first().version
        """

        when:
        isolatedProjectsRun "help", "-q"

        then:
        outputContains "root.version = v1\na.version = v1"
    }

    def 'access via Gradle instance preserves a referrer'() {
        settingsFile """
            include(":a")
        """

        file("a/build.gradle") << """
            version = "v1"
            println "a.version = " + gradle.rootProject.getSubprojects()[0].version
        """

        when:
        isolatedProjectsRun "help"

        then:
        outputContains "a.version = v1"
    }
}
