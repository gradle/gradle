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

package org.gradle.integtests.tooling.r40

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.TextUtil
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.gradle.test.fixtures.server.http.RepositoryHttpServer
import org.gradle.tooling.ProjectConnection
import org.gradle.util.Requires
import org.junit.Rule
import spock.lang.Issue

import static org.gradle.util.TestPrecondition.*

@ToolingApiVersion(">=2.5")
@TargetGradleVersion(">=4.0")
class BuildProgressCrossVersionSpec extends ToolingApiSpecification {

    @Rule
    public final RepositoryHttpServer server = new RepositoryHttpServer(temporaryFolder, targetDist.version.version)

    def "generates events for worker actions executed in-process and forked"() {
        given:
        settingsFile << "rootProject.name = 'single'"
        buildFile << """
        import org.gradle.workers.*
        class TestRunnable implements Runnable {
            @Override public void run() {
                // Do nothing
            }
        }
        task runInProcess {
            doLast {
                def workerExecutor = gradle.services.get(WorkerExecutor)
                workerExecutor.submit(TestRunnable) { config ->
                    config.forkMode = ForkMode.NEVER
                    config.displayName = 'My in-process worker action'
                }
            }
        }
        task runForked {
            doLast {
                def workerExecutor = gradle.services.get(WorkerExecutor)
                workerExecutor.submit(TestRunnable) { config ->
                    config.forkMode = ForkMode.ALWAYS
                    config.displayName = 'My forked worker action'
                }
            }
        }
    """.stripIndent()

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .forTasks('runInProcess', 'runForked')
                    .addProgressListener(events)
                    .run()
        }

        then:
        events.assertIsABuild()

        and:
        events.operation('Task :runInProcess').descendant('My in-process worker action')
        events.operation('Task :runForked').descendant('My forked worker action')
    }

    def "generates events for interleaved project configuration and dependency resolution"() {
        given:
        settingsFile << """
            rootProject.name = 'multi'
            include 'a', 'b'
        """
        buildFile << """
            allprojects { apply plugin: 'java' }
            dependencies {
                compile project(':a')
            }
            configurations.compile.each { println it }
"""
        file("a/build.gradle") << """
            dependencies {
                compile project(':b')
            }
            configurations.compile.each { println it }
"""

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .addProgressListener(events)
                    .run()
        }

        then:
        events.assertIsABuild()

        def configureRoot = events.operation("Configure project :")

        def applyRootBuildScript = configureRoot.child("Apply build file '${buildFile}' to root project 'multi'")

        def resolveCompile = applyRootBuildScript.child("Resolve dependencies :compile")
        applyRootBuildScript.child("Resolve artifact a.jar (project :a)")
        applyRootBuildScript.child("Resolve artifact b.jar (project :b)")

        def applyProjectABuildScript = resolveCompile.child("Configure project :a").child("Apply build file '${file('a/build.gradle')}' to project ':a'")

        def resolveCompileA = applyProjectABuildScript.child("Resolve dependencies :a:compile")
        applyProjectABuildScript.child("Resolve artifact b.jar (project :b)")

        resolveCompileA.child("Configure project :b")
    }

    @LeaksFileHandles
    def "generates events for downloading artifacts"() {
        given:
        toolingApi.requireIsolatedUserHome()

        def projectB = mavenHttpRepo.module('group', 'projectB', '1.0').publish()
        def projectC = mavenHttpRepo.module('group', 'projectC', '1.5').publish()
        def projectD = mavenHttpRepo.module('group', 'projectD', '2.0-SNAPSHOT').publish()

        settingsFile << """
            rootProject.name = 'root'
            include 'a'
        """.stripIndent()
        buildFile << """
            allprojects {
                apply plugin:'java'
            }
            repositories {
               maven { url '${mavenHttpRepo.uri}' }
            }
            
            dependencies {
                compile project(':a')
                compile "group:projectB:1.0"
                compile "group:projectC:1.+"
                compile "group:projectD:2.0-SNAPSHOT"
            }
            configurations.compile.each { println it }
        """.stripIndent()
        when:
        projectB.pom.expectGet()
        projectB.artifact.expectGet()
        projectC.rootMetaData.expectGet()
        projectC.pom.expectGet()
        projectC.artifact.expectGet()

        projectD.pom.expectGet()
        projectD.metaData.expectGet()
        projectD.artifact.expectGet()

        and:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .addProgressListener(events).run()
        }

        then:
        events.assertIsABuild()

        def applyBuildScript = events.operation "Apply build file '${buildFile}' to root project 'root'"

        applyBuildScript.child("Resolve dependencies :compile").with {
            it.child "Configure project :a"
            it.child "Download http://localhost:${server.port}${projectB.pomPath}"
            it.child "Download http://localhost:${server.port}/repo/group/projectC/maven-metadata.xml"
            it.child "Download http://localhost:${server.port}${projectC.pomPath}"
            it.child "Download http://localhost:${server.port}${projectD.metaDataPath}"
            it.child "Download http://localhost:${server.port}${projectD.pomPath}"
        }

        applyBuildScript.child("Resolve artifact a.jar (project :a)").children.isEmpty()

        applyBuildScript.child("Resolve artifact projectB.jar (group:projectB:1.0)")
            .child "Download http://localhost:${server.port}${projectB.artifactPath}"

        applyBuildScript.child("Resolve artifact projectC.jar (group:projectC:1.5)")
            .child "Download http://localhost:${server.port}${projectC.artifactPath}"

        applyBuildScript.child("Resolve artifact projectD.jar (group:projectD:2.0-SNAPSHOT)")
            .child "Download http://localhost:${server.port}${projectD.artifactPath}"

        cleanup:
        toolingApi.daemons.killAll()
    }

    def "generates events for applied init-scripts"() {
        given:
        def initScript1 = file('init1.gradle')
        def initScript2 = file('init2.gradle')
        [initScript1, initScript2].each { it << '' }

        when:
        def events = new ProgressEvents()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .withArguments('--init-script', initScript1.toString(), '--init-script', initScript2.toString())
                    .addProgressListener(events)
                    .run()
        }

        then:
        events.assertIsABuild()

        and:
        events.operation('Run init scripts').with {
            it.child "Apply initialization script '${initScript1}' to build"
            it.child "Apply initialization script '${initScript2}' to build"
        }
    }

    def "generates events for applied build scripts"() {
        given:
        settingsFile << '''
            rootProject.name = 'multi'
            include 'a', 'b'
        '''.stripIndent()
        def buildSrcFile = file('buildSrc/build.gradle')
        def aBuildFile = file('a/build.gradle')
        def bBuildFile = file('b/build.gradle')
        [buildSrcFile, aBuildFile, bBuildFile].each { it << '' }

        when:
        def events = new ProgressEvents()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .addProgressListener(events)
                    .run()
        }

        then:
        events.assertIsABuild()

        and:
        events.operation('Configure project :buildSrc').child "Apply build file '${buildSrcFile}' to project ':buildSrc'"
        events.operation('Configure project :').child "Apply build file '${buildFile}' to root project 'multi'"
        events.operation('Configure project :a').child "Apply build file '${aBuildFile}' to project ':a'"
        events.operation('Configure project :b').child "Apply build file '${bBuildFile}' to project ':b'"
    }

    def "generates events for applied script plugins"() {
        given:
        def scriptPlugin1 = file('scriptPlugin1.gradle')
        def scriptPlugin2 = file('scriptPlugin2.gradle')
        [scriptPlugin1, scriptPlugin2].each { it << '' }

        and:
        def initScript = file('init.gradle')
        def buildSrcScript = file('buildSrc/build.gradle')
        settingsFile << '''
            rootProject.name = 'multi'
            include 'a', 'b'
        '''.stripIndent()
        def aBuildFile = file('a/build.gradle')
        def bBuildFile = file('b/build.gradle')
        [initScript, buildSrcScript, settingsFile, buildFile, aBuildFile, bBuildFile].each {
            it << """
                apply from: '${TextUtil.normaliseFileSeparators(scriptPlugin1.absolutePath)}'
                apply from: '${TextUtil.normaliseFileSeparators(scriptPlugin2.absolutePath)}'
            """.stripIndent()
        }

        when:
        def events = new ProgressEvents()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .withArguments('--init-script', initScript.toString())
                    .addProgressListener(events)
                    .run()
        }

        then:
        events.assertIsABuild()

        and:
        println events.describeOperationsTree()

        events.operation("Apply initialization script '${initScript}' to build").with { applyInitScript ->
            applyInitScript.child "Apply script '${scriptPlugin1}' to build"
            applyInitScript.child "Apply script '${scriptPlugin2}' to build"
        }

        events.operation("Apply build file '${buildSrcScript}' to project ':buildSrc'").with { applyBuildSrc ->
            applyBuildSrc.child "Apply script '${scriptPlugin1}' to project ':buildSrc'"
            applyBuildSrc.child "Apply script '${scriptPlugin2}' to project ':buildSrc'"
        }

        events.operation("Apply settings file '${settingsFile}' to settings '${settingsFile.parentFile.name}'").with { applySettings ->
            applySettings.child "Apply script '${scriptPlugin1}' to settings 'multi'"
            applySettings.child "Apply script '${scriptPlugin2}' to settings 'multi'"
        }

        events.operation("Apply build file '${buildFile}' to root project 'multi'").with { applyRootProject ->
            applyRootProject.child "Apply script '${scriptPlugin1}' to root project 'multi'"
            applyRootProject.child "Apply script '${scriptPlugin2}' to root project 'multi'"
        }

        events.operation("Apply build file '${aBuildFile}' to project ':a'").with { applyProjectA ->
            applyProjectA.child "Apply script '${scriptPlugin1}' to project ':a'"
            applyProjectA.child "Apply script '${scriptPlugin2}' to project ':a'"
        }

        events.operation("Apply build file '${bBuildFile}' to project ':b'").with { applyProjectB ->
            applyProjectB.child "Apply script '${scriptPlugin1}' to project ':b'"
            applyProjectB.child "Apply script '${scriptPlugin2}' to project ':b'"
        }
    }

    @Requires([FIX_TO_WORK_ON_JAVA9, NOT_JDK_IBM, NOT_WINDOWS])
    def "generates events for nested script plugin applications of different types"() {
        given:
        def scriptPluginGroovy1 = file('scriptPluginGroovy1.gradle')
        def scriptPluginKotlin = file('scriptPluginKotlin.gradle.kts')
        def scriptPluginGroovy2 = file('scriptPluginGroovy2.build') //defaults to Groovy script plugin factory

        settingsFile << "rootProject.name = 'root'"

        buildFile << "apply from: '${scriptPluginGroovy1.name}'"
        scriptPluginGroovy1 << "apply from: '${scriptPluginKotlin.name}'"
        scriptPluginKotlin << "apply { from(\"$scriptPluginGroovy2.name\") }"
        scriptPluginGroovy2 << ""

        when:
        def events = new ProgressEvents()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .addProgressListener(events)
                    .run()
        }

        then:
        events.assertIsABuild()

        events.operation("Apply build file '${buildFile.absolutePath}' to root project 'root'").with { applyRoot ->
            applyRoot.child("Apply script '${scriptPluginGroovy1.absolutePath}' to root project 'root'").with { applyGroovy1 ->
                applyGroovy1.child("Apply script '${scriptPluginKotlin.absolutePath}' to root project 'root'").with { applyKotlin ->
                    applyKotlin.child("Apply script '${scriptPluginGroovy2.absolutePath}' to root project 'root'")
                }
            }
        }
    }

    @Issue("gradle/gradle#1641")
    @LeaksFileHandles
    def "generates download events during maven publish"() {
        given:
        toolingApi.requireIsolatedUserHome()
        if (targetDist.version.version == "3.5-rc-1") {
            return
        }
        def module = mavenHttpRepo.module('group', 'publish', '1')

        // module is published
        module.publish()

        // module will be published a second time via 'maven-publish'
        module.artifact.expectPut()
        module.artifact.sha1.expectPut()
        module.artifact.md5.expectPut()
        module.pom.expectPut()
        module.pom.sha1.expectPut()
        module.pom.md5.expectPut()
        module.rootMetaData.expectGet()
        module.rootMetaData.sha1.expectGet()
        module.rootMetaData.expectGet()
        module.rootMetaData.sha1.expectGet()
        module.rootMetaData.expectPut()
        module.rootMetaData.sha1.expectPut()
        module.rootMetaData.md5.expectPut()

        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'maven-publish'
            version = '1'
            group = 'group'

            publishing {
                repositories {
                    maven { url "${mavenHttpRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """

        when:
        def events = ProgressEvents.create()

        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('publish')
                    .addProgressListener(events).run()
        }

        then:
        println events.describeOperationsTree()
        def roots = events.operations.findAll { it.parent == null }
        roots.any { it.descriptor.name == 'Run build' }

        def orphans = roots.findAll { it.descriptor.name != 'Run build' }
        orphans.size() == 4
        orphans.findAll { it.descriptor.name.startsWith('Unmanaged thread operation #') } == orphans
        orphans[0].child "Download ${module.rootMetaData.uri}"
        orphans[1].child "Download ${module.rootMetaData.sha1.uri}"
        orphans[2].child "Download ${module.rootMetaData.uri}"
        orphans[3].child "Download ${module.rootMetaData.sha1.uri}"

        cleanup:
        toolingApi.daemons.killAll()
    }

    MavenHttpRepository getMavenHttpRepo() {
        return new MavenHttpRepository(server, "/repo", mavenRepo)
    }

    MavenFileRepository getMavenRepo(String name = "repo") {
        return new MavenFileRepository(file(name))
    }
}
