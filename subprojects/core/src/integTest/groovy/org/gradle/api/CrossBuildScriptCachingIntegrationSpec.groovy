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

import org.gradle.api.internal.hash.DefaultHasher
import org.gradle.api.internal.initialization.loadercache.ClassPathSnapshotter
import org.gradle.api.internal.initialization.loadercache.HashClassPathSnapshotter
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.test.fixtures.server.http.CyclicBarrierHttpServer
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.util.GradleVersion
import org.junit.Rule
import org.mortbay.jetty.Request
import org.mortbay.jetty.handler.AbstractHandler
import spock.lang.Issue
import spock.lang.Shared

import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class CrossBuildScriptCachingIntegrationSpec extends AbstractIntegrationSpec {

    FileTreeBuilder root
    File cachesDir
    File scriptCachesDir
    File remappedCachesDir
    @Rule
    CyclicBarrierHttpServer server = new CyclicBarrierHttpServer()

    @Shared
    ClassPathSnapshotter fileSnapshotter = new HashClassPathSnapshotter(new DefaultHasher())

    def setup() {
        executer.requireOwnGradleUserHomeDir()
        root = new FileTreeBuilder(testDirectory)
        cachesDir = new File(testDirectoryProvider.getTestDirectory().file("user-home"), 'caches')
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
            'settings.gradle'(settings('core', 'module1'))
        }

        when:
        run 'help'

        then:
        def settingsHash = uniqueRemapped('settings')
        def coreHash = uniqueRemapped('core')
        def module1Hash = uniqueRemapped('module1')

        and:
        remappedCacheSize() == 3 // one for each build script
        scriptCacheSize() == 2 // one for settings, one for the 2 identical scripts
        coreHash == module1Hash
        hasCachedScripts(settingsHash, coreHash)
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
            'settings.gradle'("include 'core', 'module1'")
        }

        when:
        run 'help'

        then:
        def settingsHash = uniqueRemapped('settings')
        def buildHashes = hasRemapped('build')

        and:
        remappedCacheSize() == 3 // one for each build script
        scriptCacheSize() == 2 // one for settings, one for the 2 identical scripts
        buildHashes.size() == 2 // two build.gradle files in different dirs
        hasCachedScripts(settingsHash, *buildHashes)
    }

    def "can have two build files with different contents and same file name"() {
        given:
        root {
            core {
                'build.gradle'(this.simpleBuild())
            }
            module1 {
                'build.gradle'(this.simpleBuild('different contents'))
            }
            'settings.gradle'("include 'core', 'module1'")
        }

        when:
        run 'help'

        then:
        def settingsHash = uniqueRemapped('settings')
        def buildHashes = hasRemapped('build')

        and:
        remappedCacheSize() == 3 // one for each build script
        scriptCacheSize() == 3 // one for settings, one for each build.gradle file
        buildHashes.size() == 2 // two build.gradle files in different dirs
        hasCachedScripts(settingsHash, *buildHashes)
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
            'settings.gradle'("include 'core', 'module1'")
        }
        run 'help'

        when:
        root {
            module1 {
                'build.gradle'(this.simpleBuild('different contents'))
            }
        }
        run 'help'

        then:
        def settingsHash = uniqueRemapped('settings')
        def buildHashes = hasRemapped('build')
        remappedCacheSize() == 3 // one for each build script
        scriptCacheSize() == 3 // one for settings, one for each build.gradle file
        buildHashes.size() == 3 // two build.gradle files in different dirs + one new build.gradle
        hasCachedScripts(settingsHash, *buildHashes)
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
            'settings.gradle'(settings('module1', 'module2'))
        }

        when:
        fails 'module1:someTask'

        then:
        def settingsHash = uniqueRemapped('settings')
        def module1Hash = uniqueRemapped('module1')
        def module2Hash = uniqueRemapped('module2')

        and:
        remappedCacheSize() == 3 // one for each build script
        scriptCacheSize() == 2 // one for settings, one for the 2 identical scripts
        module1Hash == module2Hash
        hasCachedScripts(settingsHash, module1Hash)

        and:
        def module1File = file("module1/module1.gradle")
        failure.assertHasFileName("Build file '$module1File'")
        failure.assertHasLineNumber(4)

        when:
        fails 'module2:someTask'

        then:
        def module2File = file("module2/module2.gradle")
        failure.assertHasFileName("Build file '$module2File'")
        failure.assertHasLineNumber(4)
    }

    def "caches scripts applied from remote locations"() {
        def http = new HttpServer()
        get(http, '/shared.gradle') {
            '''println "Echo"'''
        }
        http.start()

        given:
        root {
            'build.gradle'(this.applyFromRemote(http))
        }

        when:
        run 'tasks'

        then:
        outputContains 'Echo'
        def buildHash = uniqueRemapped('build')
        def sharedHash = uniqueRemapped('shared')

        and:
        remappedCacheSize() == 2 // one for each build script
        scriptCacheSize() == 2 // one for each build script
        hasCachedScripts(buildHash, sharedHash)

        cleanup:
        http.stop()
    }

    def "caches scripts applied from remote locations when remote script changes"() {
        def http = new HttpServer()
        int call = 0
        get(http, '/shared.gradle') {
            """ println "Echo ${call++}" """
        }
        http.start()

        given:
        root {
            'build.gradle'(this.applyFromRemote(http))
        }
        def buildHash
        def sharedHash

        when:
        run 'tasks'
        buildHash = uniqueRemapped('build')
        sharedHash = uniqueRemapped('shared')

        then:
        outputContains 'Echo 0'

        and:
        remappedCacheSize() == 2 // one for each build script
        scriptCacheSize() == 2 // one for each build script
        hasCachedScripts(buildHash, sharedHash)

        when:
        run 'tasks'
        buildHash = uniqueRemapped('build')
        def sharedHashs = hasRemapped('shared')

        then:
        outputContains 'Echo 1'

        and:
        remappedCacheSize() == 2 // one for each build script
        scriptCacheSize() == 3 // one for each build script of this invocation + 1 from the previous invocation
        hasCachedScripts(buildHash, *sharedHashs)

        cleanup:
        http.stop()
    }

    @Issue("GRADLE-2795")
    def "can change script while build is running"() {
        given:
        buildFile << """
task someLongRunningTask << {
    new URL("${server.uri}").text
}
"""

        when:
        def longRunning = executer.withTasks("someLongRunningTask").start()
        server.waitFor()

        then:
        remappedCacheSize() == 1 // build.gradle
        scriptCacheSize() == 1 // build.gradle
        hasCachedScripts(uniqueRemapped('build'))

        when:
        buildFile << """
task fastTask { }
"""

        executer.withTasks("fastTask").run()
        assert longRunning.isRunning()
        server.release()
        longRunning.waitForExit()

        then:
        remappedCacheSize() == 1 // build.gradle
        scriptCacheSize() == 2 // build.gradle version 1, build.gradle version 2
        hasCachedScripts(*hasRemapped('build'))

        cleanup:
        server.release()
        longRunning?.waitForExit()
    }

    def "build script is recompiled when project's classpath changes"() {
        root {
            lib {
                'foo.jar'('foo')
            }
            'build.gradle'('''
                buildscript {
                    dependencies {
                        classpath files('lib/foo.jar')
                    }
                }
            ''')
        }

        when:
        run 'help'
        def origJarHash = classpathHashFor('lib/foo.jar')

        then:
        def coreHash = uniqueRemapped('build')
        remappedCacheSize() == 1
        scriptCacheSize() == 1
        hasCachedScripts(coreHash)
        hasCachedScriptForClasspath(coreHash, 'cp_proj', 'cp_proj')
        hasCachedScriptForClasspath(coreHash, 'proj', "proj$origJarHash")

        when:
        root {
            lib {
                'foo.jar'('baz')
            }
        }
        sleep(1000)
        run 'help'
        coreHash = uniqueRemapped('build')
        def updatedJarHash = classpathHashFor('lib/foo.jar')

        then:
        remappedCacheSize() == 1
        scriptCacheSize() == 1
        hasCachedScripts(coreHash)
        hasCachedScriptForClasspath(coreHash, 'cp_proj', 'cp_proj')
        hasCachedScriptForClasspath(coreHash, 'proj', "proj$origJarHash")
        hasCachedScriptForClasspath(coreHash, 'proj', "proj$updatedJarHash")
    }

    private String classpathHashFor(String... files) {
        "${fileSnapshotter.snapshot(new DefaultClassPath(testDirectory.file(files))).hashCode()}"
    }


    int buildScopeCacheSize() {
        def m = output =~ /(?s).*Build scope cache size: (\d+).*/
        m.matches()
        m.group(1).toInteger()
    }

    int crossBuildScopeCacheSize() {
        def m = output =~ /(?s).*Cross-build scope cache size: (\d+).*/
        m.matches()
        m.group(1).toInteger()
    }

    Set<String> buildScopeCacheContents() {
        def m = output =~ /(?s).*Build scope cache contents: \[(.+?)\].*/
        m.matches()
        m.group(1).split(", ") as Set
    }

    Set<String> crossBuildScopeCacheContents() {
        def m = output =~ /(?s).*Cross-build scope cache contents: \[(.+?)\].*/
        m.matches()
        m.group(1).split(", ") as Set
    }

    Map<String, Integer> crossBuildCacheStats() {
        def m = output =~ /(?s).*Cross-build scope cache stats: CacheStats\{((?:(?:\p{Alnum}+=\d+)(?:, )?)+)}.*/
        m.matches()
        def stats = [:]
        m = m.group(1) =~ /(\p{Alnum}+)=(\d+)/
        while (m.find()) {
            stats[m.group(1)] = m.group(2).toInteger()
        }
        stats
    }

    List<String> hasRemapped(String buildFile) {
        def remapped = remappedCachesDir.listFiles().findAll { it.name.startsWith(buildFile) }
        if (remapped) {
            def contentHash = remapped*.list().flatten()
            return contentHash
        }
        throw new AssertionError("Cannot find a remapped build script for '${buildFile}.gradle'")
    }

    String uniqueRemapped(String buildFile) {
        def hashes = hasRemapped(buildFile)
        assert hashes.size() == 1
        hashes[0]
    }

    int remappedCacheSize() {
        remappedCachesDir.list().length
    }

    int scriptCacheSize() {
        scriptCachesDir.list().length
    }

    void hasCachedScripts(String... contentHashes) {
        Set foundInCache = scriptCachesDir.list() as Set
        Set expected = contentHashes as Set
        assert foundInCache == expected
    }

    void hasCachedScriptForClasspath(String contentHash, String dslId, String classpathHash) {
        def contentsDir = new File(scriptCachesDir, contentHash)
        assert contentsDir.exists(): "Unable to find a cached script directory for content hash $contentHash.  Found: ${scriptCachesDir.list().toList()}"
        def dslDir = new File(contentsDir, dslId)
        assert dslDir.exists(): "Unable to find a cached script directory for content hash $contentHash and DSL id $dslId. Found: ${contentsDir.list().toList()}"
        def classpathDir = new File(dslDir, classpathHash)
        assert classpathDir.exists(): "Unable to find a cached script directory for content hash $contentHash, DSL id $dslId and classpath hash $classpathHash. Found: ${dslDir.list().toList()}"
    }

    String simpleBuild(String comment = '') {
        """
            // ${comment}
            apply plugin:'java'
        """
    }

    String settings(String... projects) {
        String includes = "include ${projects.collect { "'$it'" }.join(', ')}"
        """
            $includes
            rootProject.children.each { project ->
                project.projectDir = new File(project.name)
                project.buildFileName = "\${project.name}.gradle"
            }
        """
    }

    String taskThrowingError() {
        '''
            task someTask() {
                doLast {
                    thisMethodDoesNotExist()
                }
            }
        '''
    }

    String applyFromRemote(HttpServer server) {
        """
            apply from: '${server.uri}/shared.gradle'
        """
    }

    void get(HttpServer server, String path, Closure<?> buildFile) {
        server.addHandler(new AbstractHandler() {
            @Override
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) throws IOException, ServletException {
                if (target == path) {
                    response.contentType = 'text/plain'
                    response.outputStream << buildFile().toString()
                    ((Request) request).handled = true
                }
            }
        })
    }
}
