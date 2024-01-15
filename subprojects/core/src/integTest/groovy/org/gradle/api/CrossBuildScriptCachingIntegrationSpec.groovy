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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.integtests.fixtures.daemon.DaemonsFixture
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Issue

import java.util.regex.Pattern

class CrossBuildScriptCachingIntegrationSpec extends AbstractIntegrationSpec {

    FileTreeBuilder root
    File cachesDir
    File scriptCachesDir
    File remappedCachesDir
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    private TestFile homeDirectory = testDirectoryProvider.getTestDirectory().file("user-home")

    def setup() {
        executer.requireOwnGradleUserHomeDir()
        root = new FileTreeBuilder(testDirectory)
        cachesDir = new File(homeDirectory, 'caches')
        def versionCaches = new File(cachesDir, GradleVersion.current().version)
        scriptCachesDir = new File(versionCaches, 'scripts')
        remappedCachesDir = new File(versionCaches, 'scripts-remapped')
    }

    def "identical build files are compiled once"() {
        given:
        root {
            core {
                'core.gradle'(this.simpleBuild())
            }
            module1 {
                'module1.gradle'(this.simpleBuild())
            }
            'settings.gradle'(this.settingsWithBuildScriptsUseProjectName('core', 'module1'))
        }

        when:
        run 'help'

        then:
        def scripts = scriptDetails()
        scripts.size() == 3
        hasScript("settings", scripts)
        hasScript(":core", scripts)
        hasScript(":module1", scripts)
        eachScriptIsUnique(scripts)
        scriptCacheSize() == 4 // classpath + body for settings and for the 2 identical scripts
    }

    @ToBeFixedForConfigurationCache(because = "test expect script evaluation")
    def "identical build files are compiled once for distinct invocations"() {
        given:
        root {
            core {
                'core.gradle'(this.simpleBuild())
            }
            module1 {
                'module1.gradle'(this.simpleBuild())
            }
            'settings.gradle'(this.settingsWithBuildScriptsUseProjectName('core', 'module1'))
        }

        when:
        executer.withGradleUserHomeDir(homeDirectory)
        executer.requireDaemon()
        executer.requireIsolatedDaemons()
        run 'help'
        def before = scriptDetails()

        run 'help'

        then:
        def scripts = scriptDetails()
        scripts.size() == 3
        scriptsAreReused(before, scripts)
        scriptCacheSize() == 4 // classpath + body for settings and for the 2 identical scripts
    }

    def "can have two build files with same contents and file name"() {
        given:
        root {
            core {
                'build.gradle'(this.simpleBuild())
            }
            module1 {
                'build.gradle'(this.simpleBuild())
            }
            'settings.gradle'(this.settings('core', 'module1'))
        }

        when:
        run 'help'

        then:
        def scripts = scriptDetails()
        scripts.size() == 3
        hasScript("settings", scripts)
        hasScript(":core", scripts)
        hasScript(":module1", scripts)
        eachScriptIsUnique(scripts)
        scriptCacheSize() == 4 // classpath and body for settings and for the 2 identical scripts
    }

    def "can have two build files with different contents and same file name"() {
        given:
        root {
            core {
                'build.gradle'(this.simpleBuild())
            }
            module1 {
                'build.gradle'(this.simpleBuild('// different contents'))
            }
            'settings.gradle'(this.settings('core', 'module1'))
        }

        when:
        run 'help'

        then:
        def scripts = scriptDetails()
        scripts.size() == 3
        hasScript("settings", scripts)
        hasScript(":core", scripts)
        hasScript(":module1", scripts)
        eachScriptIsUnique(scripts)
        scriptCacheSize() == 6 // classpath + body for settings and for each build.gradle file
    }

    def "reuses scripts when build file changes in a way that does not affect behaviour"() {
        given:
        root {
            core {
                'build.gradle'(this.simpleBuild())
            }
            module1 {
                'build.gradle'(this.simpleBuild())
            }
            'settings.gradle'(this.settings('core', 'module1'))
        }
        run 'help'
        def before = scriptDetails()

        when:
        file('module1/build.gradle').text = simpleBuild('// different')
        run 'help'

        then:
        def scripts = scriptDetails()
        scriptsAreReused(before, scripts)
        scriptCacheSize() == 6 // classpath + body for settings and for each build.gradle file
    }

    def "cache size increases when build file changes"() {
        given:
        root {
            core {
                'build.gradle'(this.simpleBuild())
            }
            module1 {
                'build.gradle'(this.simpleBuild())
            }
            'settings.gradle'(this.settings('core', 'module1'))
        }
        run 'help'
        def before = scriptDetails()

        when:
        file('module1/build.gradle').text = simpleBuild('println("different")')
        run 'help'

        then:
        def scripts = scriptDetails()
        scriptHasChanged(":module1", before, scripts)
        scriptCacheSize() == 6 // classpath + body for settings and for each build.gradle file
    }

    def "remapping scripts doesn't mix up classes with same name"() {
        given:
        root {
            'build.gradle'('''
                    task greet()
                    apply from: "gradle/one.gradle"
                    apply from: "gradle/two.gradle"
                ''')
            gradle {
                'one.gradle'('''
                    class Greeter { String toString() { 'Greetings from One!' } }
                    greet.doLast() { println new Greeter() }
                ''')

                'two.gradle'('''
                    class Greeter { String toString() { 'Greetings from Two!' } }
                    greet.doLast() { println new Greeter() }
                ''')
            }
        }

        when:
        run 'greet'

        then:
        outputContains 'Greetings from One!'
        outputContains 'Greetings from Two!'
    }

    def "reports errors at the correct location when 2 scripts are identical"() {
        given:
        root {
            module1 {
                'module1.gradle'(this.taskThrowingError())
            }
            module2 {
                'module2.gradle'(this.taskThrowingError())
            }
            'settings.gradle'(this.settingsWithBuildScriptsUseProjectName('module1', 'module2'))
        }

        when:
        fails 'module1:someTask'

        then:
        def scripts = scriptDetails()
        scripts.size() == 3
        eachScriptIsUnique(scripts)
        scriptCacheSize() == 4 // classpath + body for settings and for the 2 identical scripts

        and:
        def module1File = file("module1/module1.gradle")
        failure.assertHasFileName("Build file '$module1File'")
        failure.assertHasLineNumber(5)

        when:
        fails 'module2:someTask'

        then:
        def module2File = file("module2/module2.gradle")
        failure.assertHasFileName("Build file '$module2File'")
        failure.assertHasLineNumber(5)
    }

    def "caches scripts applied from remote locations"() {
        server.start()

        given:
        root {
            'build.gradle'(this.applyFromRemote(server))
        }
        server.expect(server.get("shared.gradle").send("""
            ${instrument('"shared"')}
            println 'Echo'
        """))

        when:
        run 'help'

        then:
        outputContains 'Echo'

        and:
        def scripts = scriptDetails()
        scripts.size() == 2
        hasScript(":", scripts)
        hasScript("shared", scripts)
        eachScriptIsUnique(scripts)
        scriptCacheSize() == 4 // classpath + body for each build script
    }

    def "caches scripts applied from remote locations when remote script changes"() {
        server.start()

        given:
        root {
            'build.gradle'(this.applyFromRemote(server))
        }
        server.expect(server.get("shared.gradle").send("""
            ${instrument('"shared"')}
            println 'Echo 0'
        """))

        when:
        run 'help'

        then:
        outputContains 'Echo 0'

        and:
        def before = scriptDetails()

        when:
        server.expect(server.head("shared.gradle"))
        server.expect(server.get("shared.gradle").send("""
            ${instrument('"shared"')}
            println 'Echo 1'
        """))

        run 'help'

        then:
        outputContains 'Echo 1'

        and:
        def scripts = scriptDetails()
        scriptHasChanged("shared", before, scripts)
        scriptCacheSize() == 6 // classpath + body for each build script of this invocation + 1 from the previous invocation
    }

    @Issue("GRADLE-2795")
    def "can change script while build is running"() {
        server.start()

        given:
        buildFile << simpleBuild("""
task someLongRunningTask {
    doLast {
        ${server.callFromBuild("running")}
    }
}
""")
        def handle = server.expectAndBlock("running")

        when:
        def longRunning = executer.withTasks("someLongRunningTask").start()
        handle.waitForAllPendingCalls()

        then:
        def before
        ConcurrentTestUtil.poll {
            // Output is delivered asynchronously, so wait until the details are available
            before = scriptDetails(longRunning.standardOutput)
            assert !before.isEmpty()
        }
        scriptCacheSize() == 2 // classpath + body for build.gradle

        when:
        buildFile << """
task fastTask { }
"""

        def fast = executer.withTasks("fastTask").run()
        assert longRunning.isRunning()
        handle.releaseAll()
        longRunning.waitForExit()

        then:
        def scripts = scriptDetails(fast.output)
        scriptHasChanged(":", before, scripts)
        scriptCacheSize() == 4 // classpath + body for build.gradle version 1, build.gradle version 2
    }

    @ToBeFixedForConfigurationCache(because = "changing buildscript files dependency")
    def "build script is recompiled when project's classpath changes"() {
        createJarWithProperties("lib/foo.jar", [source: 1])
        root {
            'build.gradle'(simpleBuild('''
                buildscript {
                    dependencies {
                        classpath files('lib/foo.jar')
                    }
                }
            '''))
        }

        when:
        run 'help'

        then:
        def before = scriptDetails()

        when:
        createJarWithProperties("lib/foo.jar", [target: 2])
        run 'help'

        then:
        def scripts = scriptDetails()
        scriptsAreReused(before, scripts) // The scripts end up with the same byte code and so the result is reused
        scriptCacheSize() == 3 // single classpath block, plus a build script body for each parent classpath
    }

    @ToBeFixedForConfigurationCache(because = "changing buildscript files dependency")
    def "build script is recompiled when parent project's classpath changes"() {
        createJarWithProperties("lib/foo.jar", [source: 1])
        root {
            'build.gradle'('''
                buildscript {
                    dependencies {
                        classpath files('lib/foo.jar')
                    }
                }
            ''')
            module {
                'module.gradle'(this.simpleBuild())
            }
            'settings.gradle'(this.settingsWithBuildScriptsUseProjectName('module'))
        }

        when:
        run 'help'

        then:
        def before = scriptDetails()

        when:
        createJarWithProperties("lib/foo.jar", [target: 2])
        run 'help'

        then:
        def scripts = scriptDetails()
        scriptsAreReused(before, scripts) // scripts end up with the same byte code and so are reused
        scriptCacheSize() == 8 // classpath and body for settings plus classpath and two bodies for the two build scripts
    }

    def "init script is cached"() {
        root {
            'build.gradle'(this.simpleBuild())
            gradle {
                'init.gradle'("""
                    // init script
                    ${this.instrument('"init"')}
                """)
            }
        }

        when:
        executer.withArgument('-Igradle/init.gradle')
        run 'help'

        then:
        def scripts = scriptDetails()
        scripts.size() == 2
        hasScript('init', scripts)
        scriptCacheSize() == 4 // classpath and body for build script and init script
    }

    def "same script can be applied from init script, settings script and build script"() {
        root {
            'common.gradle'("""
                ${this.instrument('"common"')}
            """)
            'init.gradle'('''
                // init script
                apply from: 'common.gradle'
            ''')
            'settings.gradle'('''
                // settings script
                apply from: 'common.gradle'
            ''')
            'build.gradle'('''
                // build script
                apply from: 'common.gradle'
            ''')
        }

        when:
        executer.withArgument('-Iinit.gradle')
        run 'help'

        then:
        def scripts = scriptDetails()
        scripts.size() == 3 // same script applied 3 times
        scripts.collect { it.className }.unique().size() == 1
        scripts.collect { it.classpath }.unique().size() == 1
        scriptCacheSize() == 8 // classpath and body for each script
    }

    def "same script can be applied from identical init script, settings script and build script"() {
        root {
            'common.gradle'("""
                ${this.instrument('"common"')}
            """)
            'init.gradle'('''
                apply from: 'common.gradle'
            ''')
            'settings.gradle'('''
                apply from: 'common.gradle'
            ''')
            'build.gradle'('''
                apply from: 'common.gradle'
            ''')
        }

        when:
        executer.withArgument('-Iinit.gradle')
        run 'help'

        then:
        def scripts = scriptDetails()
        scripts.size() == 3 // same script applied 3 times
        scripts.collect { it.className }.unique().size() == 1
        scripts.collect { it.classpath }.unique().size() == 1
        scriptCacheSize() == 8 // classpath and body for the common script + identical script x 3 targets
    }

    def "remapped classes have script origin"() {
        root {
            'build.gradle'('''

                void assertScriptOrigin(Object o, Set<String> seen) {
                    assert (o instanceof org.gradle.internal.scripts.ScriptOrigin)
                    // need to get through reflection to bypass the Groovy MOP on closures, which would cause calling the method on the owner instead of the closure itself
                    def originalClassName = o.class.getMethod('getOriginalClassName').invoke(o)
                    def contentHash = o.class.getMethod('getContentHash').invoke(o)
                    assert originalClassName
                    assert contentHash
                    println "Action type: ${originalClassName} (remapped name: ${o.class})"
                    println "Action hash: ${contentHash}"
                    if (!seen.add(contentHash)) {
                       throw new AssertionError("Expected a unique hash, but found duplicate: ${o.contentHash} in $seen")
                    }
                }

                Set<String> seen = []

                assertScriptOrigin(this, seen)

                task one {
                    doLast {
                        { ->
                            assertScriptOrigin(owner, seen) // hack to get a handle on the parent closure
                        }()
                    }
                }

                task two {
                    def v
                    v = { assertScriptOrigin(v, seen) }
                    doFirst(v)
                }

                task three {
                    doLast(new Action() {
                        void execute(Object o) {
                            assertScriptOrigin(this, seen)
                        }
                    })
                }

                task four {
                    doLast {
                        def a = new A()
                        assertScriptOrigin(a, seen)
                    }
                }

                class A {}
            ''')
        }

        when:
        run 'one', 'two', 'three', 'four'

        then:
        noExceptionThrown()
    }

    def "same applied script is compiled once for different projects with different classpath"() {
        root {
            'common.gradle'('println "poke"')
        }

        when:
        def iterations = 3
        def builder = root
        iterations.times { n ->
            createJarWithProperties("foo${n}.jar", [value: n])
            new File(root.baseDir, 'build.gradle').delete()
            builder {
                'build.gradle'("""
                    buildscript {
                        dependencies {
                            classpath files('foo${n}.jar')
                        }
                    }

                    apply from: 'common.gradle'
                """)
            }
            run 'help'
        }

        then:
        scriptCacheSize() == 2 * (1 + iterations) // common + 1 build script per iteration
    }

    @ToBeFixedForConfigurationCache(because = "test expect script evaluation")
    def "script doesn't get recompiled if daemon disappears"() {
        root {
            buildSrc {
                'build.gradle'('''
                    apply plugin: 'java'
                ''')
                src {
                    main {
                        java {
                            'Foo.java'('public class Foo {}')
                        }
                    }
                }
            }
            'build.gradle'(this.simpleBuild('''apply from:'main.gradle' '''))
            'main.gradle'(this.simpleBuild('''
                task success {
                    doLast {
                        println 'ok'
                    }
                }
            '''))
        }
        executer.requireIsolatedDaemons()
        executer.requireDaemon()
        executer.withGradleUserHomeDir(homeDirectory)

        when:
        succeeds 'success'
        def before = scriptDetails()
        daemons.daemon.kill()

        succeeds 'success'

        then:
        def scripts = scriptDetails()
        scriptsAreReused(before, scripts)
        scriptCacheSize() == 6
    }

    DaemonsFixture getDaemons() {
        new DaemonLogsAnalyzer(executer.daemonBaseDir)
    }

    int scriptCacheSize() {
        scriptCachesDir.listFiles().collect { it.directory }.size()
    }

    void hasScript(String path, List<ClassDetails> scripts) {
        assert scripts.find { it.path == path }
    }

    void scriptHasChanged(String path, List<ClassDetails> before, List<ClassDetails> after) {
        def script1 = before.find { it.path == path }
        def script2 = after.find { it.path == path }
        assert script1 != null && script2 != null
        assert script1.className == script2.className
        assert script1.classpath != script2.classpath
    }

    void scriptsAreReused(List<ClassDetails> before, List<ClassDetails> after) {
        assert before.size() == after.size()
        for (int i = 0; i < before.size(); i++) {
            def script1 = before[i]
            def script2 = after[i]
            assert script1.path == script2.path
            assert script1.className == script2.className
            assert script1.classpath == script2.classpath
        }
    }

    void eachScriptIsUnique(List<ClassDetails> scripts) {
        assert scripts.collect { it.path }.unique().size() == scripts.size()
        assert scripts.collect { it.className }.unique().size() == scripts.size()
        assert scripts.collect { it.classpath }.unique().size() == scripts.size()
    }

    List<ClassDetails> scriptDetails(String text = result.output) {
        def pattern = Pattern.compile("script=(.*)=(.*),(.*)")
        def lines = text.readLines()
        def result = []
        lines.forEach { line ->
            def matcher = pattern.matcher(line)
            if (!matcher.matches()) {
                return
            }
            result.add(new ClassDetails(matcher.group(1), matcher.group(2), matcher.group(3)))
        }
        return result
    }

    String instrument(String idExpr) {
        return """println("script=" + $idExpr + "=" + getClass().name + "," + getClass().classLoader.getURLs().collect { new File(it.toURI()) })"""
    }

    String simpleBuild(String content = '') {
        """
            ${content}
            ${instrument("project.path")}
        """
    }

    String settings(String... projects) {
        createDirs(projects)
        String includes = "include ${projects.collect { "'$it'" }.join(', ')}"
        """
            ${instrument("'settings'")}
            $includes
        """
    }

    String settingsWithBuildScriptsUseProjectName(String... projects) {
        createDirs(projects)
        String includes = "include ${projects.collect { "'$it'" }.join(', ')}"
        """
            ${instrument("'settings'")}
            $includes
            rootProject.children.each { project ->
                project.projectDir = new File(project.name)
                project.buildFileName = "\${project.name}.gradle"
            }
        """
    }

    String taskThrowingError() {
        simpleBuild('''
            task someTask() {
                doLast {
                    thisMethodDoesNotExist()
                }
            }
        ''')
    }

    String applyFromRemote(BlockingHttpServer server) {
        simpleBuild("""
            apply from: '${server.uri}/shared.gradle'
        """)
    }

    class ClassDetails {
        final String path
        final String className
        final String classpath

        ClassDetails(String path, String className, String classpath) {
            this.path = path
            this.className = className
            this.classpath = classpath
        }
    }
}
