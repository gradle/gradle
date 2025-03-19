/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.TextUtil
import org.gradle.test.fixtures.file.DoesNotSupportNonAsciiPaths
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.gradle.test.fixtures.server.http.RepositoryHttpServer
import org.gradle.tooling.ProjectConnection
import org.gradle.util.GradleVersion
import spock.lang.Timeout

import java.util.concurrent.TimeUnit

@IntegrationTestTimeout(300)
@TargetGradleVersion('>=4.0')
@DoesNotSupportNonAsciiPaths(reason = "Gradle versions prior to 8.3 have issues with non-ascii paths and worker daemons – see https://github.com/gradle/gradle/issues/13843")
class ProjectConfigurationChildrenProgressCrossVersionSpec extends AbstractProgressCrossVersionSpec {

    private RepositoryHttpServer server

    def setup() {
        server = new RepositoryHttpServer(temporaryFolder, targetDist.version.version)
        server.before()
    }

    def cleanup() {
        server.after()
    }

    private String expectedDisplayName(String name, String extension, String version) {
        getTargetVersion() < GradleVersion.version("6.0") ? "$name.$extension" : "$name-$version.$extension"
    }

    private String workerActionClass(String name) {
        if (getTargetVersion() < GradleVersion.version("6.0")) {
            "class ${name}WorkAction implements Runnable { public void run() { } }"
        } else {
            "abstract class ${name}WorkAction implements WorkAction<WorkParameters.None> { public void execute() { } }"
        }
    }

    private String workerSubmit(String actionName) {
        if (getTargetVersion() < GradleVersion.version("6.0")) {
            "workerExecutor.submit(${actionName}WorkAction) { c -> c.isolationMode = IsolationMode.${actionName == 'Forked' ? 'PROCESS' : 'NONE'} }"
        } else {
            "workerExecutor.${actionName == 'Forked' ? 'process' : 'no'}Isolation().submit(${actionName}WorkAction) { }"
        }
    }

    def "generates events for worker actions executed in-process and forked"() {
        given:
        settingsFile << "rootProject.name = 'single'"
        buildFile << """
            import org.gradle.workers.*

            ${workerActionClass('InProcess')}
            ${workerActionClass('Forked')}

            task runInProcess {
                doLast {
                    def workerExecutor = services.get(WorkerExecutor)
                    ${workerSubmit('InProcess')}
                }
            }
            task runForked {
                doLast {
                    def workerExecutor = services.get(WorkerExecutor)
                    ${workerSubmit('Forked')}
                }
            }
        """.stripIndent()

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .setStandardOutput(System.out)
                    .forTasks('runInProcess', 'runForked')
                    .addProgressListener(events)
                    .run()
        }

        then:
        events.assertIsABuild()

        and:
        events.operation('Task :runInProcess').descendant('InProcessWorkAction')
        events.operation('Task :runForked').descendant('ForkedWorkAction')
    }

    def "does not generate events for non-existing build scripts"() {
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

        and:
        events.operation('Configure project :').descendants {
            it.descriptor.displayName.contains("Apply script")
        }.isEmpty()
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
        [buildSrcFile, buildFile, aBuildFile, bBuildFile].each { it << '' }

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

        and:
        events.operation('Configure project :buildSrc').child applyBuildScript(buildSrcFile, ':buildSrc')
        events.operation('Configure project :').child applyBuildScriptRootProject("multi")
        events.operation('Configure project :a').child applyBuildScript(aBuildFile, ':a')
        events.operation('Configure project :b').child applyBuildScript(bBuildFile, ':b')
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
        def events = ProgressEvents.create()
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

        def initScripts = events.operations(applyInitScript(initScript))
        !initScripts.empty // Root build, plus buildSrc for Gradle >=8.0
        initScripts.each { operation ->
            operation.child applyInitScriptPlugin(scriptPlugin1)
            operation.child applyInitScriptPlugin(scriptPlugin2)
        }

        events.operation(applyBuildScript(buildSrcScript, ':buildSrc')).with { applyBuildSrc ->
            applyBuildSrc.child applyBuildScriptPlugin(scriptPlugin1, ':buildSrc')
            applyBuildSrc.child applyBuildScriptPlugin(scriptPlugin2, ':buildSrc')
        }

        events.operation(applySettingsFile()).with { applySettings ->
            applySettings.child applySettingsScriptPlugin(scriptPlugin1, "multi")
            applySettings.child applySettingsScriptPlugin(scriptPlugin2, "multi")
        }

        events.operation(applyBuildScriptRootProject("multi")).with { applyRootProject ->
            applyRootProject.child applyBuildScriptPluginRootProject(scriptPlugin1, "multi")
            applyRootProject.child applyBuildScriptPluginRootProject(scriptPlugin2, "multi")
        }

        events.operation(applyBuildScript(aBuildFile, ':a')).with { applyProjectA ->
            applyProjectA.child applyBuildScriptPlugin(scriptPlugin1, ':a')
            applyProjectA.child applyBuildScriptPlugin(scriptPlugin2, ':a')
        }

        events.operation(applyBuildScript(bBuildFile, ':b')).with { applyProjectB ->
            applyProjectB.child applyBuildScriptPlugin(scriptPlugin1, ':b')
            applyProjectB.child applyBuildScriptPlugin(scriptPlugin1, ':b')
        }
    }

    @Timeout(value = 10, unit = TimeUnit.MINUTES)
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
               maven { url = '${mavenHttpRepo.uri}' }
            }

            dependencies {
                implementation project(':a')
                implementation "group:projectB:1.0"
                implementation "group:projectC:1.+"
                implementation "group:projectD:2.0-SNAPSHOT"
            }
            configurations.compileClasspath.each { println it }
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

        def applyBuildScript = events.operation applyBuildScriptRootProject("root")

        applyBuildScript.child("Resolve dependencies of :compileClasspath").with {
            it.child "Configure project :a"
            it.descendant "Download ${server.uri}${projectB.pomPath}"
            it.descendant "Download ${server.uri}/repo/group/projectC/maven-metadata.xml"
            it.descendant "Download ${server.uri}${projectC.pomPath}"
            it.descendant "Download ${server.uri}${projectD.metaDataPath}"
            it.descendant "Download ${server.uri}${projectD.pomPath}"
        }

        def resolveArtifacts = applyBuildScript.child(resolveConfigurationFiles(':compileClasspath'))

        resolveArtifacts.child("Resolve ${expectedDisplayName('projectB', 'jar', '1.0')} (group:projectB:1.0)")
            .child "Download ${server.uri}${projectB.artifactPath}"

        resolveArtifacts.child("Resolve ${expectedDisplayName('projectC', 'jar', '1.5')} (group:projectC:1.5)")
            .child "Download ${server.uri}${projectC.artifactPath}"

        resolveArtifacts.child("Resolve ${expectedDisplayName('projectD', 'jar', '2.0-SNAPSHOT')} (group:projectD:2.0-SNAPSHOT)", "Resolve ${expectedDisplayName('projectD', 'jar', '2.0-SNAPSHOT')} (group:projectD:2.0-SNAPSHOT:${projectD.uniqueSnapshotVersion})")
            .child "Download ${server.uri}${projectD.artifactPath}"
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
                implementation project(':a')
            }
            // Triggers configuration of a due to the dependency
            configurations.compileClasspath.each { println it }
"""
        def aBuildfile = file("a/build.gradle") << """
            dependencies {
                implementation project(':b')
            }
            // Triggers configuration of a due to the dependency
            configurations.compileClasspath.each { println it }
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

        def applyRootBuildScript = configureRoot.child(applyBuildScriptRootProject("multi"))
        def resolveCompile = applyRootBuildScript.child("Resolve dependencies of :compileClasspath")
        applyRootBuildScript.child(resolveConfigurationFiles(':compileClasspath'))

        def applyProjectABuildScript = resolveCompile.child("Configure project :a").child(applyBuildScript(aBuildfile, ':a'))
        def resolveCompileA = applyProjectABuildScript.child("Resolve dependencies of :a:compileClasspath")
        applyProjectABuildScript.child(resolveConfigurationFiles(':a:compileClasspath'))

        resolveCompileA.child("Configure project :b")
    }

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
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .addProgressListener(events)
                    .run()
        }

        then:
        events.assertIsABuild()

        events.operation(applyBuildScriptRootProject('root')).with { applyRoot ->
            applyRoot.child(applyBuildScriptPluginRootProject(scriptPluginGroovy1, 'root')).with { applyGroovy1 ->
                applyGroovy1.child(applyBuildScriptPluginRootProject(scriptPluginKotlin, 'root')).with { applyKotlin ->
                    applyKotlin.child(applyBuildScriptPluginRootProject(scriptPluginGroovy2, 'root'))
                }
            }
        }
    }

    MavenHttpRepository getMavenHttpRepo() {
        return new MavenHttpRepository(server, "/repo", mavenRepo)
    }

    MavenFileRepository getMavenRepo(String name = "repo") {
        return new MavenFileRepository(file(name))
    }

    private String resolveConfigurationFiles(String path) {
        if (targetVersion.baseVersion >= GradleVersion.version("8.7")) {
            return "Resolve files of configuration '" + path + "'"
        } else {
            return "Resolve files of " + path
        }
    }
}
