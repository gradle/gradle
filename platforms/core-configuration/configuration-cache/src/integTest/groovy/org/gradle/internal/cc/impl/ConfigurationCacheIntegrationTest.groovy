/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.cc.impl

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Destroys
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheRecreateOption
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture
import spock.lang.Issue

import static org.gradle.internal.cc.impl.SupersetIndexKt.SUPERSET_INDEX_DIR_NAME

class ConfigurationCacheIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "configuration cache is out of incubation"() {
        given:
        settingsFile << ""

        when:
        run("help", "--configuration-cache")

        then:
        result.assertHasPostBuildOutput("Configuration cache entry stored.")
        !output.contains("Configuration cache is an incubating feature.")
    }

    def "configuration cache for Help plugin task '#task' on empty project"() {
        given:
        settingsFile.createFile()
        configurationCacheRun(task, *options)
        def firstRunOutput = removeVfsLogOutput(result.normalizedOutput)
            .replaceAll(/Calculating task graph as no cached configuration is available for tasks: ${task}.*\n/, '')
            .replaceAll(/Configuration cache entry stored.\n/, '')

        when:
        configurationCacheRun(task, *options)
        def secondRunOutput = removeVfsLogOutput(result.normalizedOutput)
            .replaceAll(/Reusing configuration cache.\n/, '')
            .replaceAll(/Configuration cache entry reused.\n/, '')

        then:
        firstRunOutput == secondRunOutput

        where:
        task           | options
        "help"         | []
        "properties"   | []
        "dependencies" | []
        "help"         | ["--task", "help"]
        "help"         | ["--rerun"]
    }

    def "can store task selection success/failure for :help --task"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile.text = """
        task aTask
        """
        when:
        configurationCacheFails "help", "--task", "bTask"
        then:
        failureCauseContains("Task 'bTask' not found in root project")
        configurationCache.assertStateStored()

        when:
        configurationCacheFails "help", "--task", "cTask"
        then:
        failureCauseContains("Task 'cTask' not found in root project")
        configurationCache.assertStateStored()

        when:
        configurationCacheFails "help", "--task", "bTask"
        then:
        failureCauseContains("Task 'bTask' not found in root project")
        configurationCache.assertStateLoaded()

        when:
        configurationCacheRun "help", "--task", "aTask"
        then:
        output.contains "Detailed task information for aTask"
        configurationCache.assertStateStored()

        when:
        configurationCacheFails "help", "--task", "cTask"
        then:
        failureCauseContains("Task 'cTask' not found in root project")
        configurationCache.assertStateLoaded()

        when:
        buildFile << """
        task bTask
        """
        configurationCacheRun "help", "--task", "bTask"
        then:
        output.contains "Detailed task information for bTask"
        configurationCache.assertStateStored()
    }

    @Issue("https://github.com/gradle/gradle/issues/18064")
    def "can build plugin with project dependencies"() {
        given:
        createDirs("my-lib", "my-plugin")
        settingsFile << """
            include 'my-lib'
            include 'my-plugin'
        """
        file('my-lib/build.gradle') << """
            plugins { id 'java' }
        """
        file('my-plugin/build.gradle') << """
            plugins { id 'java-gradle-plugin' }

            dependencies {
              implementation project(":my-lib")
            }

            gradlePlugin {
              plugins {
                myPlugin {
                  id = 'com.example.my-plugin'
                  implementationClass = 'com.example.MyPlugin'
                }
              }
            }
        """
        file('src/main/java/com/example/MyPlugin.java') << """
            package com.example;
            public class MyPlugin implements $Plugin.name<$Project.name> {
              @Override
              public void apply($Project.name project) {
              }
            }
        """
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun "build"
        configurationCacheRun "build"

        then:
        configurationCache.assertStateLoaded()
    }

    def "can copy zipTree"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile """
            def jar = tasks.register("jar", org.gradle.jvm.tasks.Jar) {
                it.from("a.file")
                it.destinationDirectory.set(layout.buildDirectory)
                it.archiveFileName.set("output.jar")
            }

            tasks.register("copy", org.gradle.api.tasks.Copy) {
                it.from(zipTree(${provider}))
                it.destinationDir = new File(project.buildDir, "copied")
            }
        """
        file("a.file") << "42"

        when:
        configurationCacheRun "copy"
        configurationCacheRun "copy"

        then:
        configurationCache.assertStateLoaded()

        where:
        provider                         | _
        "jar.flatMap { it.archiveFile }" | _
        "jar.get().archiveFile"          | _
    }

    @Issue("gradle/gradle#20390")
    def "can deserialize copy task with rename"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile """
            tasks.register('copyAndRename', Copy) {
                from('foo') { rename { 'bar' } }
            }
        """

        when:
        configurationCacheRun "copyAndRename"
        configurationCacheRun "copyAndRename"

        then:
        configurationCache.assertStateLoaded()
    }

    def "can request to recreate the cache"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun "help", "-D${ConfigurationCacheRecreateOption.PROPERTY_NAME}=true"

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "help", "-D${ConfigurationCacheRecreateOption.PROPERTY_NAME}=true"

        then:
        configurationCache.assertStateStored()
        outputContains("Recreating configuration cache")
    }

    def "does not configure build when task graph is already cached for requested tasks"() {

        def configurationCache = newConfigurationCacheFixture()

        given:
        buildFile << """
            println "running build script"

            class SomeTask extends DefaultTask {
                SomeTask() {
                    println("create task")
                }
            }
            task a(type: SomeTask) {
                println("configure task")
            }
            task b {
                dependsOn a
            }
        """

        when:
        configurationCacheRun "a"

        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as no cached configuration is available for tasks: a")
        outputContains("running build script")
        outputContains("create task")
        outputContains("configure task")
        result.assertTasksScheduled(":a")

        when:
        configurationCacheRun "a"

        then:
        configurationCache.assertStateLoaded()
        outputContains("Reusing configuration cache.")
        outputDoesNotContain("running build script")
        outputDoesNotContain("create task")
        outputDoesNotContain("configure task")
        result.assertTasksScheduled(":a")

        when:
        configurationCacheRun "b"

        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as no cached configuration is available for tasks: b")
        outputContains("running build script")
        outputContains("create task")
        outputContains("configure task")
        result.assertTasksScheduled(":a", ":b")

        when:
        configurationCacheRun "a"

        then:
        configurationCache.assertStateLoaded()
        outputContains("Reusing configuration cache.")
        outputDoesNotContain("running build script")
        outputDoesNotContain("create task")
        outputDoesNotContain("configure task")
        result.assertTasksScheduled(":a")
    }

    // region Partial Task Selection Matching
    def "partial task selection causes cache hit against task superset"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile << """
            ['a', 'b', 'c'].each { name ->
                tasks.register(name) {
                    doLast { println name }
                }
            }
        """

        when:
        configurationCacheRun "a", "b", "c"

        then:
        configurationCache.assertStateStored()
        outputContains("a")
        outputContains("b")
        outputContains("c")
        result.assertTasksExecuted(":a", ":b", ":c")

        when:
        configurationCacheRun "a", "b", "c"

        then:
        configurationCache.assertStateLoaded()
        result.assertTasksExecuted(":a", ":b", ":c")

        when:
        configurationCacheRun "a"

        then:
        configurationCache.assertStateLoaded()
        result.assertTasksExecuted(":a")
        result.assertTasksNotScheduled(":b")
        result.assertTasksNotScheduled(":c")
    }

    def "missing entry directory invalidates index row and triggers cold store"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile << """
            ['a', 'b', 'c'].each { name ->
                tasks.register(name) {
                    doLast { println name }
                }
            }
        """

        when:
        configurationCacheRun "a", "b", "c"

        then:
        configurationCache.assertStateStored()

        when: 'the entry directory is removed out from under the index, but the index dir survives'
        def ccRoot = new File(testDirectory, ".gradle/configuration-cache")
        ccRoot.eachDir { dir ->
            if (dir.name != SUPERSET_INDEX_DIR_NAME) {
                dir.deleteDir()
            }
        }
        configurationCacheRun "a"

        then: 'self-heal kicks in: stale row dropped, cold-store at a fresh fullKey'
        configurationCache.assertStateStored()

        and: 'the index file now holds exactly one entry — the stale row was evicted, not just skipped in-memory'
        // SupersetIndexFile binary layout: 4-byte magic, 4-byte version, 4-byte count.
        // If self-heal didn't rewrite the file, the count would be 2 (orphan + new).
        def indexFiles = new File(ccRoot, SUPERSET_INDEX_DIR_NAME).listFiles()
        indexFiles.size() == 1
        java.nio.ByteBuffer.wrap(indexFiles[0].bytes, 8, 4).getInt() == 1

        when: 'the next run for the same request hits exactly against the just-stored fresh entry'
        configurationCacheRun "a"

        then: 'no second cold-store — the new entry survived being committed to disk and indexed'
        configurationCache.assertStateLoaded()
    }

    def "task arguments disable superset matching"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile << """
            tasks.register('printer') {
                doLast { println 'printed' }
            }
            tasks.register('other') {
                doLast { println 'other' }
            }
        """

        when:
        configurationCacheRun "printer", "--rerun", "other"

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "printer"

        then:
        configurationCache.assertStateStored()
    }

    def "task exclusion (-x foo) disables superset matching"() {
        given:
        // Lookup-side guard: any token starting with `-` short-circuits the superset path.
        // `-x foo` is the exclusion form — affects scheduling in ways the index doesn't model.
        def configurationCache = newConfigurationCacheFixture()
        buildFile << """
            ['a', 'b', 'c'].each { name ->
                tasks.register(name) { doLast { println name } }
            }
        """

        when: 'first store with -x to exclude :b'
        configurationCacheRun "a", "b", "c", "-x", "b"

        then:
        configurationCache.assertStateStored()

        when: 'subset request that would normally hit — but original CLI included a -x token, so the entry was not recorded as superset-discoverable; this request also contains no excluded-tokens of its own but cannot find the prior entry via index'
        configurationCacheRun "a"

        then:
        configurationCache.assertStateStored()
    }

    def "stored entry whose CLI contained a '-'-prefixed token is not findable as a superset"() {
        given:
        // Record-side guard in `recordEnvironmentKeyForCacheKey`: an entry whose CLI included
        // a `-`-prefixed token is not added to the superset index. The entry still works as
        // a regular CC exact-key match, but won't surface as a superset for future requests.
        def configurationCache = newConfigurationCacheFixture()
        buildFile << """
            ['a', 'b', 'c'].each { name ->
                tasks.register(name) { doLast { println name } }
            }
        """

        when: 'first store includes --rerun — index recording skipped'
        configurationCacheRun "a", "b", "c", "--rerun"

        then:
        configurationCache.assertStateStored()

        when: 'subset request — would hit the [a, b, c] entry if it had been indexed, but the recording-side guard kept it out of the index'
        configurationCacheRun "a"

        then:
        configurationCache.assertStateStored()

        when: 'exact-match repeat of the original (no superset lookup involved) — regular CC fingerprint match still works'
        configurationCacheRun "a", "b", "c", "--rerun"

        then:
        configurationCache.assertStateLoaded()
    }

    def "empty CLI (project defaults) does not superset-match against any stored entry"() {
        given:
        // Pins the defensive-depth empty-CLI guard in `findCacheEntry`. If the guard
        // regresses, an empty `requestedDistinct` is a subsequence of every stored cliTokens
        // list — so the lookup would happily pick a non-empty stored entry, prune ALL its
        // tasks (none are in the empty request), and load an empty plan. Default tasks
        // would silently not run.
        def configurationCache = newConfigurationCacheFixture()
        buildFile << """
            ['a', 'b', 'c', 'd'].each { name ->
                tasks.register(name) { doLast { println name } }
            }
            defaultTasks 'd'
        """

        when: 'first store: an explicit CLI request unrelated to the default tasks'
        configurationCacheRun "a", "b", "c"

        then:
        configurationCache.assertStateStored()

        when: 'second run with no CLI args — project defaults resolve to :d'
        configurationCacheRun()

        then: 'cold-stores a fresh entry; the empty-CLI guard prevented unsafe superset match against [a, b, c]'
        configurationCache.assertStateStored()
        result.assertTasksExecuted(":d")

        when: 'third run with no CLI args — regular CC exact-key match (not superset) reuses the entry from the previous run'
        configurationCacheRun()

        then:
        configurationCache.assertStateLoaded()
        result.assertTasksExecuted(":d")
    }

    def "different environment keys cannot superset-match"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile << """
            ['a', 'b', 'c'].each { name ->
                tasks.register(name) {
                    doLast { println name }
                }
            }
        """

        when:
        configurationCacheRun "a", "b", "c", "--offline"

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "a"

        then:
        // Different --offline (false on the second run) means different env key.
        configurationCache.assertStateStored()
    }

    def "whenReady listener flags entry as superset-ineligible"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile << """
            ['a', 'b', 'c'].each { name ->
                tasks.register(name) {
                    doLast { println name }
                }
            }
            gradle.taskGraph.whenReady { graph ->
                // Registering any user TaskExecutionGraphListener flips the flag —
                // we don't actually need to inspect the graph here, the registration
                // itself is what makes the entry superset-ineligible.
            }
        """

        when:
        configurationCacheRun "a", "b", "c"

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "a"

        then:
        // Flag set: superset reuse blocked. Cold store at the new key.
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "a", "b", "c"

        then:
        // Exact match still works for flagged entries.
        configurationCache.assertStateLoaded()
    }

    def "whenReady Action overload flags entry as superset-ineligible"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile << """
            import org.gradle.api.Action
            import org.gradle.api.execution.TaskExecutionGraph

            ['a', 'b', 'c'].each { name ->
                tasks.register(name) {
                    doLast { println name }
                }
            }
            // Explicit Action<TaskExecutionGraph> — Groovy method dispatch prefers
            // the Closure overload for a bare `{ graph -> }`, so we have to spell
            // out the Action to exercise DefaultTaskExecutionGraph.whenReady(Action).
            gradle.taskGraph.whenReady(new Action<TaskExecutionGraph>() {
                @Override
                void execute(TaskExecutionGraph graph) {}
            })
        """

        when:
        configurationCacheRun "a", "b", "c"

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "a"

        then:
        // Flag set via the Action overload: superset reuse blocked, fresh cold store.
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "a", "b", "c"

        then:
        // Exact match still works for flagged entries.
        configurationCache.assertStateLoaded()
    }

    def "addTaskExecutionGraphListener flags entry as superset-ineligible"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile << """
            import org.gradle.api.execution.TaskExecutionGraph
            import org.gradle.api.execution.TaskExecutionGraphListener

            ['a', 'b', 'c'].each { name ->
                tasks.register(name) {
                    doLast { println name }
                }
            }
            gradle.taskGraph.addTaskExecutionGraphListener(new TaskExecutionGraphListener() {
                @Override
                void graphPopulated(TaskExecutionGraph graph) {}
            })
        """

        when:
        configurationCacheRun "a", "b", "c"

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "a"

        then:
        // Flag set via addTaskExecutionGraphListener: superset reuse blocked.
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "a", "b", "c"

        then:
        // Exact match still works for flagged entries.
        configurationCache.assertStateLoaded()
    }

    def "deprecated beforeTask Action flags entry as superset-ineligible"() {
        given:
        // The deprecated beforeTask path is itself a CC violation (`--configuration-cache-problems=warn`
        // demotes it to a warning so we can observe the flag-flipping behavior). It still calls
        // `notifyListenerRegistration`, which routes through TaskGraphListenerRegistrationTracker
        // and flips the flag the same way whenReady does. Pinned here so a future cleanup of the
        // deprecated path doesn't silently drop the broadcast.
        def configurationCache = newConfigurationCacheFixture()
        buildFile << """
            ['a', 'b', 'c'].each { name ->
                tasks.register(name) { doLast { println name } }
            }
            gradle.taskGraph.beforeTask { task -> /* noop */ }
        """

        when:
        configurationCacheRun "a", "b", "c", "--configuration-cache-problems=warn"

        then:
        configurationCache.assertStateStored()

        when: 'subset request — entry should be flagged, no superset reuse'
        configurationCacheRun "a", "--configuration-cache-problems=warn"

        then:
        configurationCache.assertStateStored()

        when: 'exact match against the flagged entry still loads'
        configurationCacheRun "a", "b", "c", "--configuration-cache-problems=warn"

        then:
        configurationCache.assertStateLoaded()
    }

    def "deprecated afterTask Action flags entry as superset-ineligible"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile << """
            ['a', 'b', 'c'].each { name ->
                tasks.register(name) { doLast { println name } }
            }
            gradle.taskGraph.afterTask { task -> /* noop */ }
        """

        when:
        configurationCacheRun "a", "b", "c", "--configuration-cache-problems=warn"

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "a", "--configuration-cache-problems=warn"

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "a", "b", "c", "--configuration-cache-problems=warn"

        then:
        configurationCache.assertStateLoaded()
    }

    def "deprecated addTaskExecutionListener flags entry as superset-ineligible"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile << """
            import org.gradle.api.execution.TaskExecutionListener
            import org.gradle.api.tasks.TaskState

            ['a', 'b', 'c'].each { name ->
                tasks.register(name) { doLast { println name } }
            }
            gradle.taskGraph.addTaskExecutionListener(new TaskExecutionListener() {
                @Override void beforeExecute(Task t) {}
                @Override void afterExecute(Task t, TaskState s) {}
            })
        """

        when:
        configurationCacheRun "a", "b", "c", "--configuration-cache-problems=warn"

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "a", "--configuration-cache-problems=warn"

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "a", "b", "c", "--configuration-cache-problems=warn"

        then:
        configurationCache.assertStateLoaded()
    }

    def "TaskExecutionGraph query methods called at configuration time do not disable superset reuse"() {
        given:
        // At configuration time the task graph is empty — none of these query methods
        // can return a value that influences what runs. They are deliberately NOT
        // instrumented as `taskGraphAccessed`. This pins that decision: a build script
        // that touches all of them at config time must still permit subset reuse.
        // Methods exercised: hasTask, findTask, getAllTasks, getFilteredTasks,
        // getDependencies, size, hasScheduledWork, collectScheduledWork.
        def configurationCache = newConfigurationCacheFixture()
        buildFile << """
            ['a', 'b', 'c'].each { name ->
                tasks.register(name) {
                    doLast { println name }
                }
            }
            afterEvaluate {
                def tg = gradle.taskGraph
                println 'hasTask(path) = ' + tg.hasTask(':b')
                println 'findTask = ' + tg.findTask(':b')
                println 'getAllTasks().size = ' + tg.getAllTasks().size()
                println 'getFilteredTasks().size = ' + tg.getFilteredTasks().size()
                println 'size = ' + tg.size()
                println 'hasScheduledWork = ' + tg.hasScheduledWork()
                println 'collectScheduledWork.scheduledNodes.size = ' + tg.collectScheduledWork().scheduledNodes.size()
                // getDependencies throws on an empty graph — wrap so the attempted call
                // still goes through the un-instrumented method dispatch. The point is
                // that even reaching it at config time does not trip taskGraphAccessed.
                try {
                    tg.getDependencies(tasks.getByName('a'))
                    throw new AssertionError('expected IllegalStateException on empty graph')
                } catch (IllegalStateException ignored) {
                    println 'getDependencies threw ISE as expected on empty graph'
                }
            }
        """

        when:
        configurationCacheRun "a", "b", "c"

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "a"

        then:
        // None of the eight query calls flipped the entry's taskGraphAccessed flag,
        // so the stored [a, b, c] entry is reused as a strict superset of [a].
        configurationCache.assertStateLoaded()
        result.assertTasksExecuted(":a")
        result.assertTasksNotScheduled(":b")
        result.assertTasksNotScheduled(":c")
    }

    def "direct hasTask call does not disable superset reuse"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile << """
            ['a', 'b', 'c'].each { name ->
                tasks.register(name) {
                    doLast { println name }
                }
            }
            afterEvaluate {
                // Direct query at configuration time — empty graph, no real decision.
                // Must NOT flag the entry.
                println 'hasTask result: ' + gradle.taskGraph.hasTask(':b')
            }
        """

        when:
        configurationCacheRun "a", "b", "c"

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "a"

        then:
        // Direct hasTask call did NOT trip the flag, so superset reuse works.
        configurationCache.assertStateLoaded()
        result.assertTasksExecuted(":a")
        result.assertTasksNotScheduled(":b")
        result.assertTasksNotScheduled(":c")
    }

    def "cache hit for subset selection runs task and its dependencies"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile << """
            ['a', 'b', 'c', 'd'].each { name ->
                tasks.register(name) {
                    doLast { println name }
                }
            }
            tasks.named('a') { dependsOn 'd' }
        """

        when:
        configurationCacheRun "a", "b", "c"

        then:
        configurationCache.assertStateStored()
        result.assertTasksExecuted(":d", ":a", ":b", ":c")

        when:
        configurationCacheRun "a"

        then:
        configurationCache.assertStateLoaded()
        result.assertTasksExecuted(":d", ":a")
        result.assertTasksNotScheduled(":b")
        result.assertTasksNotScheduled(":c")
    }

    def "cache hit for sibling selection excludes other tasks' dependencies"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile << """
            ['a', 'b', 'c', 'd'].each { name ->
                tasks.register(name) {
                    doLast { println name }
                }
            }
            tasks.named('a') { dependsOn 'd' }
        """

        when:
        configurationCacheRun "a", "b", "c"

        then:
        configurationCache.assertStateStored()
        result.assertTasksExecuted(":d", ":a", ":b", ":c")

        when:
        configurationCacheRun "b"

        then:
        configurationCache.assertStateLoaded()
        result.assertTasksExecuted(":b")
        result.assertTasksNotScheduled(":a")
        result.assertTasksNotScheduled(":c")
        result.assertTasksNotScheduled(":d")
    }

    def "reordered subset is a miss not a hit"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile << """
            ['a', 'b', 'c'].each { name ->
                tasks.register(name) {
                    doLast { println name }
                }
            }
        """

        when:
        configurationCacheRun "a", "b", "c"

        then:
        configurationCache.assertStateStored()

        when: 'requesting the same set in a different order'
        configurationCacheRun "b", "a"

        then: 'subsequence semantics: [b, a] is not a subsequence of [a, b, c] — cold-store'
        configurationCache.assertStateStored()
        result.assertTasksExecuted(":b", ":a")
    }

    def "subset request that would drop a mustRunAfter-target is refused"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile << """
            tasks.register('first') { doLast { println 'first' } }
            tasks.register('second') {
                mustRunAfter 'first'
                doLast { println 'second' }
            }
        """

        when:
        configurationCacheRun "first", "second"

        then:
        configurationCache.assertStateStored()
        result.assertTasksExecuted(":first", ":second")

        when: 'request second alone — second still carries a mustRunAfter :first edge'
        configurationCacheRun "second"

        then: 'gate refuses: pruning :first would leave :second pointing at a dropped task'
        configurationCache.assertStateStored()
        result.assertTasksExecuted(":second")
    }

    def "subset request that would drop a finalizer-target is refused"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile << """
            tasks.register('producer') {
                doLast { println 'producer' }
            }
            tasks.register('consumer') {
                finalizedBy 'producer'
                doLast { println 'consumer' }
            }
        """

        when:
        configurationCacheRun "consumer"

        then: 'producer is pulled in as finalizer'
        configurationCache.assertStateStored()
        result.assertTasksExecuted(":consumer", ":producer")

        when: 'requesting producer alone — finalizer edge from consumer would dangle'
        configurationCacheRun "producer"

        then: 'gate refuses subset; cold-store'
        configurationCache.assertStateStored()
        result.assertTasksExecuted(":producer")
    }

    def "subset request that would drop a custom task with a @Destroys-annotated property is refused"() {
        given:
        // Generalization of the Delete-instance gate: any task type with a property
        // annotated `@Destroys` is treated as side-effecting at store time, because
        // its execution removes files that retained tasks may depend on. Detection
        // uses TypeMetadataStore annotation lookup — no getter invocation, so this
        // does not contribute to the property eval count.
        def configurationCache = newConfigurationCacheFixture()
        buildFile << """
            abstract class CustomDestroyer extends DefaultTask {
                @${Destroys.name}
                abstract ${ConfigurableFileCollection.name} getVictims()

                @TaskAction
                void run() { println 'destroying' }
            }
            tasks.register('wipe', CustomDestroyer) {
                victims.from('no-such-file')
            }
            tasks.register('build') {
                doLast { println 'build' }
            }
        """

        when:
        configurationCacheRun "wipe", "build"

        then:
        configurationCache.assertStateStored()

        when: 'request build alone — wipe would be dropped, but it has a @Destroys property'
        configurationCacheRun "build"

        then: 'destroyables gate refuses the subset; cold-store'
        configurationCache.assertStateStored()

        when: 'original exact-match request — the gate-rejected entry must still be in the index'
        configurationCacheRun "wipe", "build"

        then: 'state loaded: gate rejection on the subset request did NOT evict the entry'
        configurationCache.assertStateLoaded()
    }

    def "subset request that would drop a Delete task is refused"() {
        given:
        // `Delete` is caught by the generalized `@Destroys` gate via `Delete.getTargetFiles()`
        // being `@Destroys`-annotated. The gate fires regardless of whether the deletion
        // target exists. The assertion of interest is that the second run cold-stores
        // (gate refused subset) — not that `:cleanThing` produced any work output.
        def configurationCache = newConfigurationCacheFixture()
        buildFile << """
            tasks.register('cleanThing', Delete) {
                delete 'no-such-file'
            }
            tasks.register('build') {
                doLast { println 'build' }
            }
        """

        when:
        configurationCacheRun "cleanThing", "build"

        then:
        configurationCache.assertStateStored()

        when: 'request build alone — cleanThing would be dropped, but it is a Delete'
        configurationCacheRun "build"

        then: 'side-effecting Delete gate refuses subset; cold-store rather than skip the deletion'
        configurationCache.assertStateStored()

        when: 'original exact-match request — the gate-rejected entry must still be in the index'
        configurationCacheRun "cleanThing", "build"

        then: 'state loaded: gate rejection on the subset request did NOT evict the entry'
        configurationCache.assertStateLoaded()
    }

    def "bare and absolute CLI tokens for the same task do not share an index entry"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile << """
            tasks.register('d') { doLast { println 'd' } }
        """

        when: 'first invocation uses the absolute path'
        configurationCacheRun ":d"

        then:
        configurationCache.assertStateStored()

        when: 'second invocation uses the bare name — verbatim CLI matching keeps them separate'
        configurationCacheRun "d"

        then: 'different cliTokens means no index hit, even though both resolve to :d'
        configurationCache.assertStateStored()

        when: 'third invocation matches the second exactly'
        configurationCacheRun "d"

        then: 'now we hit — bare entry exists and matches verbatim'
        configurationCache.assertStateLoaded()
    }

    def "gate-rejected entry survives in the index for future exact-match reuse"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile << """
            tasks.register('first') { doLast { println 'first' } }
            tasks.register('second') {
                mustRunAfter 'first'
                doLast { println 'second' }
            }
            tasks.register('third') { doLast { println 'third' } }
        """

        when:
        configurationCacheRun "first", "second", "third"

        then:
        configurationCache.assertStateStored()

        when: 'subset request fails the mustRunAfter dangle gate and cold-stores'
        configurationCacheRun "second", "third"

        then:
        configurationCache.assertStateStored()

        when: 'original exact-match request — the gate-rejected entry must still be findable in the index'
        configurationCacheRun "first", "second", "third"

        then: 'state loaded, not re-stored: gate rejection earlier did NOT evict the entry from disk'
        configurationCache.assertStateLoaded()
        result.assertTasksExecuted(":first", ":second", ":third")

        when: 'and repeating the subset request succeeds against the now-cached entry'
        configurationCacheRun "second", "third"

        then:
        configurationCache.assertStateLoaded()

        when: 'and subsets including the mustRunAfter components succeed'
        configurationCacheRun "first", "second"

        then:
        configurationCache.assertStateLoaded()
        result.assertTasksExecuted(":first", ":second")
    }

    def "multi-project bare-name entry is exact-match-only and refuses subset requests"() {
        given:
        // Bare CLI token `d` in a multi-project root resolves to BOTH `:d` and `:sub:d`,
        // so the stored entry has 1 cliToken paired with 2 entryTaskIdentityPaths —
        // a non-1:1 mapping. The dropped identity-path set can't be unambiguously
        // derived from the positional pairing, so `selectBestMatch` excludes the
        // entry from strict-superset matches. Exact-match reuse still works.
        def configurationCache = newConfigurationCacheFixture()
        createDirs("sub")
        settingsFile << """
            include 'sub'
        """
        buildFile << """
            tasks.register('d') { doLast { println 'root d' } }
        """
        file("sub/build.gradle") << """
            tasks.register('d') { doLast { println 'sub d' } }
        """

        when: 'bare-name request resolves multi-project to :d AND :sub:d'
        configurationCacheRun "d"

        then:
        configurationCache.assertStateStored()
        result.assertTasksExecuted(":d", ":sub:d")

        when: 'absolute-path subset request — would superset-match if entry were 1:1, but it is not'
        configurationCacheRun ":d"

        then: 'non-1:1 entry excluded from strict-superset; cold-store'
        configurationCache.assertStateStored()
        result.assertTasksExecuted(":d")

        when: 'original bare-name request — exact match still works'
        configurationCacheRun "d"

        then: 'state loaded: non-1:1 entry remains usable for exact matches'
        configurationCache.assertStateLoaded()
        result.assertTasksExecuted(":d", ":sub:d")
    }

    def "duplicate tokens in the request are deduplicated before subset matching"() {
        given:
        // selectBestMatch deduplicates `requested` via `.distinct()` before comparing
        // against stored cliTokens. The stored side keeps duplicates verbatim — only
        // the request is collapsed. So [a, a, b] requested against [a, b, c] stored
        // becomes [a, b] vs [a, b, c]: size 2 < 3, subsequence holds → strict-superset hit.
        def configurationCache = newConfigurationCacheFixture()
        buildFile << """
            ['a', 'b', 'c'].each { name ->
                tasks.register(name) { doLast { println name } }
            }
        """

        when: 'store [a, b, c]'
        configurationCacheRun "a", "b", "c"

        then:
        configurationCache.assertStateStored()
        result.assertTasksExecuted(":a", ":b", ":c")

        when: 'request [a, a, b] — distinct request is [a, b], a strict subseq of [a, b, c]'
        configurationCacheRun "a", "a", "b"

        then: 'dedup-then-match hits; pruning runs :a and :b only'
        configurationCache.assertStateLoaded()
        result.assertTasksExecuted(":a", ":b")
    }

    def "composite build subset request that stays inside the root build hits even when same-named tasks exist in the included build"() {
        given:
        // Bare task names do not cross composite boundaries: `gradle a b` from the root
        // resolves to :a and :b in the root only, not :included:a / :included:b. The
        // root entry's cliTokens stay 1:1 with its identity paths, so a subset request
        // for `a` still gets a strict-superset hit against [a, b]. The presence of
        // same-named tasks in the included build is a red herring — they are only
        // reached via explicit `:included:<task>` syntax.
        def configurationCache = newConfigurationCacheFixture()
        file("included/settings.gradle") << "rootProject.name = 'included'"
        file("included/build.gradle") << """
            ['a', 'b'].each { name ->
                tasks.register(name) { doLast { println "included " + name } }
            }
        """
        settingsFile << """
            includeBuild 'included'
        """
        buildFile << """
            ['a', 'b'].each { name ->
                tasks.register(name) { doLast { println name } }
            }
        """

        when: 'store [a, b] — bare names stay in root, included build untouched'
        configurationCacheRun "a", "b"

        then:
        configurationCache.assertStateStored()
        result.assertTasksExecuted(":a", ":b")

        when: 'subset request [a] — same-named included tasks must not interfere'
        configurationCacheRun "a"

        then: 'strict-superset hit against the root-only entry'
        configurationCache.assertStateLoaded()
        result.assertTasksExecuted(":a")
    }

    def "composite build subset request reaching across the includeBuild boundary cold-stores"() {
        given:
        // Documents an active limitation: when a request reaches into an included build
        // via :included:<task>, the included build produces its own CC entry separate
        // from the root's. A subset request that drops some included-build tasks does
        // not currently superset-match across that boundary — both builds end up
        // re-storing. The test pins current behavior; if cross-build superset matching
        // is added later, this test will fail and need updating.
        def configurationCache = newConfigurationCacheFixture()
        file("included/settings.gradle") << "rootProject.name = 'included'"
        file("included/build.gradle") << """
            ['a', 'b'].each { name ->
                tasks.register(name) { doLast { println "included " + name } }
            }
        """
        settingsFile << """
            includeBuild 'included'
        """
        buildFile << """
            ['a', 'b'].each { name ->
                tasks.register(name) { doLast { println name } }
            }
        """

        when: 'store [:a, :included:a, :included:b]'
        configurationCacheRun ":a", ":included:a", ":included:b"

        then:
        configurationCache.assertStateStored()
        result.assertTasksExecuted(":a", ":included:a", ":included:b")

        when: 'subset request that drops :included:b — would be a hit in a non-composite build'
        configurationCacheRun ":a", ":included:a"

        then: 'composite boundary defeats subset matching today; cold-store'
        configurationCache.assertStateStored()
        result.assertTasksExecuted(":a", ":included:a")
    }

    def "smallest matching superset is selected when multiple supersets contain the request"() {
        given:
        // selectBestMatch returns `minByOrNull { it.cliTokens.size }` over the
        // eligible supersets. Stage two entries [a, b, c] and [a, d] — both contain
        // [a] as a subsequence — then request [a]. The size-2 entry must win.
        // Note: [a, d] is not a subsequence of [a, b, c] (walking a→a hits, then d
        // finds neither b nor c), so the second store is a genuine cold-store,
        // not a hit against the first.
        def configurationCache = newConfigurationCacheFixture()
        buildFile << """
            ['a', 'b', 'c', 'd'].each { name ->
                tasks.register(name) { doLast { println name } }
            }
        """

        when: 'first store: [a, b, c]'
        configurationCacheRun "a", "b", "c"

        then:
        configurationCache.assertStateStored()

        when: 'second store: [a, d] — not a subseq of [a, b, c], so cold-stores'
        configurationCacheRun "a", "d"

        then:
        configurationCache.assertStateStored()
        result.assertTasksExecuted(":a", ":d")

        when: 'request [a] — both stored entries are valid supersets; smallest wins'
        configurationCacheRun "a"

        then: 'hit; only :a runs (no :b/:c from the larger superset, no :d from the smaller)'
        configurationCache.assertStateLoaded()
        result.assertTasksExecuted(":a")

        when: 'both prior entries remain in the index — verify exact-match against the larger'
        configurationCacheRun "a", "b", "c"

        then: '[a, b, c] entry still loadable, proving the [a] request did not evict it'
        configurationCache.assertStateLoaded()

        when: 'and exact-match against the smaller'
        configurationCacheRun "a", "d"

        then:
        configurationCache.assertStateLoaded()
    }
    // endregion Partial Task Selection Matching

    def "configuration cache for multi-level projects"() {
        given:
        createDirs("a", "a/b", "a/c")
        settingsFile << """
            include 'a:b', 'a:c'
        """
        configurationCacheRun ":a:b:help", ":a:c:help"
        def firstRunOutput = result.groupedOutput

        when:
        configurationCacheRun ":a:b:help", ":a:c:help"

        then:
        result.groupedOutput.task(":a:b:help").output == firstRunOutput.task(":a:b:help").output
        result.groupedOutput.task(":a:c:help").output == firstRunOutput.task(":a:c:help").output
    }

    def "captures changes applied in task graph whenReady listener"() {
        buildFile << """
            class SomeTask extends DefaultTask {
                @Internal
                String value

                @TaskAction
                void run() {
                    println "value = " + value
                }
            }

            task ok(type: SomeTask)

            gradle.taskGraph.whenReady {
                ok.value = 'value'
            }
        """

        when:
        configurationCacheRun "ok"
        configurationCacheRun "ok"

        then:
        outputContains("value = value")
    }

    def "can init two projects in a row"() {
        def configurationCache = new ConfigurationCacheFixture(this)
        when:
        useTestDirectoryThatIsNotEmbeddedInAnotherBuild()
        configurationCacheRun "init", "--dsl", "groovy", "--type", "basic"

        then:
        result.assertTasksExecuted(":init")
        configurationCache.assertStateStored {}
        succeeds 'properties'
        def projectName1 = testDirectory.name
        outputContains("name: ${projectName1}")

        when:
        useTestDirectoryThatIsNotEmbeddedInAnotherBuild()
        configurationCacheRun "init", "--dsl", "groovy", "--type", "basic"

        then:
        result.assertTasksExecuted(":init")
        succeeds 'properties'
        def projectName2 = testDirectory.name
        outputContains("name: ${projectName2}")
        projectName1 != projectName2
    }

    def "start parameter indicates whether configuration cache was requested"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        buildFile """
            def startParameter = gradle.startParameter
            tasks.help {
                doLast {
                    println "isConfigurationCacheRequested=" + startParameter.isConfigurationCacheRequested()
                }
            }
        """

        when:
        expectStartParameterIsConfigurationCacheRequestedWarning()
        run "help"
        then:
        configurationCache.assertNoConfigurationCache()
        outputContains("isConfigurationCacheRequested=false")

        when:
        expectStartParameterIsConfigurationCacheRequestedWarning()
        configurationCacheRun "help"
        then:
        configurationCache.assertStateStored()
        outputContains("isConfigurationCacheRequested=true")

        when:
        expectStartParameterIsConfigurationCacheRequestedWarning()
        configurationCacheRun "help"
        then:
        configurationCache.assertStateLoaded()
        outputContains("isConfigurationCacheRequested=true")
    }

    def "configuration cache is marked requested even if disabled due to --export-keys"() {
        def configurationCache = newConfigurationCacheFixture()

        buildFile """
            def isConfigurationCacheRequested = services.get(BuildFeatures).configurationCache.requested.orElse(false).get()
            tasks.help {
                doLast {
                    println "isConfigurationCacheRequested=" + isConfigurationCacheRequested
                }
            }
        """

        when:
        run "help"
        then:
        configurationCache.assertNoConfigurationCache()
        outputContains("isConfigurationCacheRequested=false")

        when:
        configurationCacheRun "help", "--export-keys"
        then:
        configurationCache.assertNoConfigurationCache()
        outputContains("isConfigurationCacheRequested=true")
    }

    private def expectStartParameterIsConfigurationCacheRequestedWarning() {
        executer.expectDocumentedDeprecationWarning(
            "The StartParameter.isConfigurationCacheRequested property has been deprecated. " +
                "This is scheduled to be removed in Gradle 10. " +
                "Please use 'configurationCache.requested' property on 'BuildFeatures' service instead. " +
                "Consult the upgrading guide for further information: " +
                "https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_startparameter_is_configuration_cache_requested",
        )
    }

    //TODO: test the expected output directly
    protected static String removeVfsLogOutput(String normalizedOutput) {
        normalizedOutput
            .replaceAll(/Received \d+ file system events .*\n/, '')
            .replaceAll(/Spent \d+ ms processing file system events since last build\n/, '')
            .replaceAll(/Spent \d+ ms registering watches for file system events\n/, '')
            .replaceAll(/Virtual file system .*\n/, '')
    }
}
