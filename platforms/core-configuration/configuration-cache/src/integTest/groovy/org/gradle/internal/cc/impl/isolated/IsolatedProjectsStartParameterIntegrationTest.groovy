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

    // Exhaustive end-to-end coverage: every notifying mutator of StartParameter and
    // StartParameterInternal is exercised through a real build and asserted to be reported, so a change
    // in behavior for any single method is caught here, not only by the instrumentation unit test. One
    // build per method keeps every case independent and the assertion trivial (a single problem).
    // setTaskNames/setTaskRequests are intentionally absent: they are exempt (see "replacing the
    // requested tasks ..."). Each invocation uses a value matching the current/default state so the
    // build still completes configuration in diagnostics mode.
    def "every mutating method of StartParameter is reported (#signature)"() {
        buildFile("""
            gradle.startParameter.$invocation
        """)

        when:
        isolatedProjectsDiagnosticsFails("help")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":")
            problem("Build file 'build.gradle': line 2: The start parameter cannot be mutated after settings have been evaluated when Isolated Projects is enabled. This happened when calling '$signature'.")
        }

        where:
        [invocation, signature] << PUBLIC_MUTATORS + INTERNAL_MUTATORS
    }

    // Every notifying public mutator of StartParameter, with an invocation whose value matches the
    // current/default state so the build completes in diagnostics mode.
    private static final List<List<String>> PUBLIC_MUTATORS = [
        ["setLogLevel(org.gradle.api.logging.LogLevel.LIFECYCLE)", "setLogLevel(LogLevel)"],
        ["setShowStacktrace(org.gradle.api.logging.configuration.ShowStacktrace.INTERNAL_EXCEPTIONS)", "setShowStacktrace(ShowStacktrace)"],
        ["setConsoleOutput(org.gradle.api.logging.configuration.ConsoleOutput.Auto)", "setConsoleOutput(ConsoleOutput)"],
        ["setConsoleUnicodeSupport(org.gradle.api.logging.configuration.ConsoleUnicodeSupport.Auto)", "setConsoleUnicodeSupport(ConsoleUnicodeSupport)"],
        ["setNonInteractive(false)", "setNonInteractive(boolean)"],
        ["setWarningMode(org.gradle.api.logging.configuration.WarningMode.Summary)", "setWarningMode(WarningMode)"],
        ["setProjectCacheDir(null)", "setProjectCacheDir(File)"],
        ["setExcludedTaskNames([])", "setExcludedTaskNames(Iterable)"],
        ["setCurrentDir(file('.'))", "setCurrentDir(File)"],
        ["setProjectProperties([:])", "setProjectProperties(Map)"],
        ["setSystemPropertiesArgs(new HashMap(gradle.startParameter.systemPropertiesArgs))", "setSystemPropertiesArgs(Map)"],
        ["setGradleUserHomeDir(gradle.gradleUserHomeDir)", "setGradleUserHomeDir(File)"],
        ["setBuildProjectDependencies(true)", "setBuildProjectDependencies(boolean)"],
        ["setDryRun(false)", "setDryRun(boolean)"],
        // addInitScript is not covered: the CLI converter populates initScripts with an immutable
        // list in real distributions, so appending already fails with an UnsupportedOperationException.
        ["setInitScripts([])", "setInitScripts(List)"],
        // setProjectDir(null) would also notify for setCurrentDir(File); the non-null path does not
        ["setProjectDir(projectDir)", "setProjectDir(File)"],
        ["setProfile(false)", "setProfile(boolean)"],
        ["setContinueOnFailure(false)", "setContinueOnFailure(boolean)"],
        ["setOffline(false)", "setOffline(boolean)"],
        ["setRefreshDependencies(false)", "setRefreshDependencies(boolean)"],
        ["setRerunTasks(false)", "setRerunTasks(boolean)"],
        ["setTaskGraph(false)", "setTaskGraph(boolean)"],
        ["setParallelProjectExecutionEnabled(gradle.startParameter.parallelProjectExecutionEnabled)", "setParallelProjectExecutionEnabled(boolean)"],
        ["setBuildCacheEnabled(false)", "setBuildCacheEnabled(boolean)"],
        ["setBuildCacheDebugLogging(false)", "setBuildCacheDebugLogging(boolean)"],
        ["setMaxWorkerCount(gradle.startParameter.maxWorkerCount)", "setMaxWorkerCount(int)"],
        ["setConfigureOnDemand(false)", "setConfigureOnDemand(boolean)"],
        ["setContinuous(false)", "setContinuous(boolean)"],
        // includeBuild is not covered for the same reason: includedBuilds is immutable in real distributions.
        ["setIncludedBuilds([])", "setIncludedBuilds(List)"],
        ["setBuildScan(false)", "setBuildScan(boolean)"],
        ["setNoBuildScan(false)", "setNoBuildScan(boolean)"],
        ["setWriteDependencyLocks(false)", "setWriteDependencyLocks(boolean)"],
        ["setLockedDependenciesToUpdate([])", "setLockedDependenciesToUpdate(List)"],
        ["setWriteDependencyVerifications([])", "setWriteDependencyVerifications(List)"],
        ["setDependencyVerificationMode(org.gradle.api.artifacts.verification.DependencyVerificationMode.STRICT)", "setDependencyVerificationMode(DependencyVerificationMode)"],
        ["setRefreshKeys(false)", "setRefreshKeys(boolean)"],
        ["setExportKeys(false)", "setExportKeys(boolean)"],
    ]

    // Every notifying mutator of StartParameterInternal. The Option.Value-typed setters
    // (setConfigurationCache, setIsolatedProjects, setParallelToolingModelBuilding) are omitted: the
    // option type is awkward to construct in a script and flipping the enablement flags mid-build is
    // meaningless. Values match the current/default state, as above.
    private static final List<List<String>> INTERNAL_MUTATORS = [
        ["setGradleHomeDir(gradle.gradleHomeDir)", "setGradleHomeDir(File)"],
        ["doNotSearchUpwards()", "doNotSearchUpwards()"],
        ["useEmptySettings()", "useEmptySettings()"],
        ["setWatchFileSystemMode(org.gradle.internal.watch.registry.WatchMode.DEFAULT)", "setWatchFileSystemMode(WatchMode)"],
        ["setVfsVerboseLogging(false)", "setVfsVerboseLogging(boolean)"],
        ["setConfigurationCacheProblems(org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheProblemsOption.Value.FAIL)", "setConfigurationCacheProblems(ConfigurationCacheProblemsOption.Value)"],
        ["setConfigurationCacheDebug(false)", "setConfigurationCacheDebug(boolean)"],
        ["setConfigurationCacheIgnoreInputsDuringStore(false)", "setConfigurationCacheIgnoreInputsDuringStore(boolean)"],
        ["setConfigurationCacheIgnoreUnsupportedBuildEventsListeners(false)", "setConfigurationCacheIgnoreUnsupportedBuildEventsListeners(boolean)"],
        ["setConfigurationCacheParallel(gradle.startParameter.configurationCacheParallel)", "setConfigurationCacheParallel(boolean)"],
        ["setConfigurationCacheReadOnly(false)", "setConfigurationCacheReadOnly(boolean)"],
        ["setConfigurationCacheEntriesPerKey(1)", "setConfigurationCacheEntriesPerKey(int)"],
        ["setConfigurationCacheMaxProblems(512)", "setConfigurationCacheMaxProblems(int)"],
        ["setConfigurationCacheIgnoredFileSystemCheckInputs(null)", "setConfigurationCacheIgnoredFileSystemCheckInputs(String)"],
        ["setConfigurationCacheRecreateCache(false)", "setConfigurationCacheRecreateCache(boolean)"],
        ["setConfigurationCacheQuiet(false)", "setConfigurationCacheQuiet(boolean)"],
        ["setConfigurationCacheIntegrityCheckEnabled(false)", "setConfigurationCacheIntegrityCheckEnabled(boolean)"],
        ["setConfigurationCacheHeapDumpDir(null)", "setConfigurationCacheHeapDumpDir(String)"],
        ["setConfigurationCacheFineGrainedPropertyTracking(true)", "setConfigurationCacheFineGrainedPropertyTracking(boolean)"],
        ["setIsolatedProjectsDiagnostics(true)", "setIsolatedProjectsDiagnostics(boolean)"],
        ["setContinuousBuildQuietPeriod(java.time.Duration.ofMillis(250))", "setContinuousBuildQuietPeriod(Duration)"],
        ["setPropertyUpgradeReportEnabled(false)", "setPropertyUpgradeReportEnabled(boolean)"],
        ["enableProblemReportGeneration(true)", "enableProblemReportGeneration(boolean)"],
        ["setDaemonJvmCriteriaConfigured(false)", "setDaemonJvmCriteriaConfigured(boolean)"],
        ["setDevelocityUrl(null)", "setDevelocityUrl(String)"],
        ["setDevelocityPluginVersion(null)", "setDevelocityPluginVersion(String)"],
    ]

}
