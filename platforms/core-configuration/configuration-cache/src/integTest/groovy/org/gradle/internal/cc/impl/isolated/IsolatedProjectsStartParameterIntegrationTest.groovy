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

package org.gradle.internal.cc.impl.isolated

class IsolatedProjectsStartParameterIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    def "StartParameter access from a build script after settings evaluation honors the IP contract (#description)"() {
        // One explicit statement of the contract: reads and the exempt task-name replacement are
        // allowed, while every mutation -- via a setter or through a collection view -- is reported.
        // The other tests cover locations, individual setters and view paths in detail; this one makes
        // the allowed-vs-forbidden boundary self-evident in a single place. Forbidden cases use
        // fail-fast: the violation is thrown before the backing collection is touched, so the outcome
        // does not depend on whether that collection is mutable.
        buildFile("""
            gradle.startParameter.$operation
        """)

        expect:
        if (forbiddenSignature == null) {
            isolatedProjectsRun("help")
            fixture.assertStateStored {
                projectsConfigured(":")
            }
        } else {
            isolatedProjectsFailsUsing(IsolatedProjectsMode.FAIL_FAST, "help")
            fixture.assertIsolatedProjectsProblems(IsolatedProjectsMode.FAIL_FAST) {
                projectsConfigured(":")
                problem("Build file 'build.gradle': line 2: The start parameter cannot be mutated after settings have been evaluated when Isolated Projects is enabled. This happened when calling '${forbiddenSignature}'.")
            }
        }

        where:
        description              | operation                            | forbiddenSignature
        "read a scalar"          | "offline"                            | null
        "read a map view"        | "projectProperties.containsKey('x')" | null
        "iterate a set view"     | "excludedTaskNames.each { }"         | null
        "replace the task names" | "setTaskNames(['help'])"             | null
        "mutate via a setter"    | "setOffline(true)"                   | "setOffline(boolean)"
        "mutate a map view"      | "projectProperties.put('p', 'v')"    | "getProjectProperties().put(Object, Object)"
        "mutate a set view"      | "excludedTaskNames.add('x')"         | "getExcludedTaskNames().add(Object)"
    }

    def "mutating StartParameter after settings evaluation is a violation (#location)"() {
        // The violation pipeline (onMutableCall -> listener -> IP problem) is the same for every setter
        // and only the call location varies, so one representative setter exercised from each location
        // covers it; that every individual setter is instrumented is checked by the fast reflective unit
        // test StartParameterMutationInstrumentationTest. The root case has no included build on purpose:
        // adding one would make its configured-project set depend on the mode (in fail-fast the root
        // throws before the included build is configured, in diagnostics everything is configured).
        if (scriptPath.startsWith("included/")) {
            settingsFile("""
                includeBuild("included")
            """)
        }
        file(scriptPath) << """
            ${apiExpr}.setDryRun(true)
        """

        when:
        isolatedProjectsFailsUsing(mode, "help")

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(*configured)
            problem("Build file '${scriptPath}': line 2: The start parameter cannot be mutated after settings have been evaluated when Isolated Projects is enabled. This happened when calling 'setDryRun(boolean)'.")
        }

        where:
        location           | scriptPath              | apiExpr                        | configured
        "root project"     | "build.gradle"          | "gradle.startParameter"        | [":"]
        "included project" | "included/build.gradle" | "gradle.startParameter"        | [":", ":included"]
        "parent build"     | "included/build.gradle" | "gradle.parent.startParameter" | [":", ":included"]

        combined:
        mode << ALL_MODES
    }

    def "mutating StartParameter from root build settings script is allowed"() {
        settingsFile("""
            gradle.startParameter.setDryRun(true)
        """)

        when:
        isolatedProjectsRun("help")

        then:
        fixture.assertStateStored {
            projectsConfigured(":")
        }
    }

    def "mutating StartParameter from included build settings script is allowed"() {
        settingsFile("""
            includeBuild("included")
        """)
        file("included/settings.gradle") << """
            gradle.startParameter.setDryRun(true)
        """

        when:
        isolatedProjectsRun("help")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":included")
        }
    }

    def "configuring the StartParameter of a GradleBuild task is not a violation"() {
        // The task's StartParameter is a copy created to define the nested build; mutating it
        // configures a build that has not started yet, which is the window where mutation is legal.
        // The mutation listener only guards a running build's own StartParameter.
        settingsFile("""
            rootProject.name = 'outer'
        """)
        buildFile("""
            tasks.register('nested', GradleBuild) {
                dir = file('other')
                tasks = ['hello']
                startParameter.setOffline(true)
                startParameter.projectProperties.put('greeting', 'hi from outer')
            }
        """)
        file("other/settings.gradle") << """
            rootProject.name = 'inner'
        """
        file("other/build.gradle") << """
            def greeting = providers.gradleProperty('greeting')
            def offline = gradle.startParameter.offline
            tasks.register('hello') {
                doLast { println("inner says: \${greeting.get()}, offline=\$offline") }
            }
        """

        when:
        isolatedProjectsRun("nested")

        then:
        outputContains("inner says: hi from outer, offline=true")
        fixture.assertStateStored {
            // ":" is the outer build; ":other" is the nested build run by the task
            projectsConfigured(":", ":other")
        }
    }

    def "mutating StartParameter from init script is allowed"() {
        file("init.gradle") << """
            gradle.startParameter.setContinueOnFailure(true)
        """
        buildFile("""
            tasks.register("ok")
        """)

        when:
        isolatedProjectsRun("ok", "-I", "init.gradle")

        then:
        postBuildOutputContains("Configuration cache entry stored.")
    }

    def "reading StartParameter from a build script after settings evaluation is not a violation"() {
        // Only mutation is reported; reads stay silent, including reads that go through the
        // mutation-notifying collection-view wrappers (Set/Map/List and their keySet/values/entrySet
        // views, iterators and entries). These reads run at configuration time, which must complete for
        // the build to reach projectsConfigured(":"), so asserting their values in the script is
        // reliable: a wrong value fails the build, and the trailing marker proves every assert ran.
        // The decisive check is still that none of these reads is reported: assertStateStored succeeds.
        buildFile("""
            def sp = gradle.startParameter
            assert sp.offline == false
            assert sp.excludedTaskNames.contains('x') == false
            assert sp.excludedTaskNames.collect { it } == []
            assert sp.projectProperties['seed'] == 'value'
            assert sp.projectProperties.keySet().contains('seed')
            assert sp.projectProperties.values().contains('value')
            assert sp.projectProperties.entrySet().find { it.key == 'seed' }?.value == 'value'
            assert sp.taskRequests.any { it.args.contains('ok') }
            println("all reads verified")
            tasks.register("ok")
        """)

        when:
        isolatedProjectsRun("ok", "-Pseed=value")

        then:
        outputContains("all reads verified")
        fixture.assertStateStored {
            projectsConfigured(":")
        }
    }

    def "replacing the requested tasks from a build script is currently allowed"() {
        // setTaskNames and setTaskRequests are exempt from violation reporting for now: tooling model
        // builders, e.g. during IDE sync, still legitimately replace the tasks to run while the build
        // is already running. This pins the exemption until a better pattern exists for them.
        buildFile("""
            tasks.register('a')
            tasks.register('b') { doLast { println 'b executed' } }
            gradle.startParameter.setTaskNames(['b'])
        """)

        when:
        isolatedProjectsRun("a")

        then:
        outputContains("b executed")
        fixture.assertStateStored {
            projectsConfigured(":")
        }
    }

    def "mutating a StartParameter collection view is reported with the access path (#invocation)"() {
        buildFile("""
            gradle.startParameter.$invocation
        """)

        when:
        // Fail-fast only: the violation is thrown before the delegate collection is touched, making
        // the outcome independent of the delegate's mutability. In diagnostics mode the mutation
        // proceeds into the delegate, and some backing collections (e.g. the project properties
        // populated by the CLI converter) are immutable in real distributions, so the build would
        // additionally fail with an UnsupportedOperationException there.
        isolatedProjectsFailsUsing(IsolatedProjectsMode.FAIL_FAST, "help", "-Pseed=value")

        then:
        fixture.assertIsolatedProjectsProblems(IsolatedProjectsMode.FAIL_FAST) {
            projectsConfigured(":")
            problem("Build file 'build.gradle': line 2: The start parameter cannot be mutated after settings have been evaluated when Isolated Projects is enabled. This happened when calling '${signature}'.")
        }

        where:
        invocation                                                                        | signature
        "projectProperties.put('p', 'v')"                                                 | "getProjectProperties().put(Object, Object)"
        "projectProperties.keySet().remove('seed')"                                       | "getProjectProperties().keySet().remove(Object)"
        "projectProperties.keySet().with { def i = it.iterator(); i.next(); i.remove() }" | "getProjectProperties().keySet().iterator().remove()"
        "projectProperties.entrySet().find { it.key == 'seed' }.setValue('x')"            | "getProjectProperties().entrySet().Entry.setValue(Object)"
        "excludedTaskNames.removeIf { it == 'x' }"                                        | "getExcludedTaskNames().removeIf(Predicate)"
    }

}
