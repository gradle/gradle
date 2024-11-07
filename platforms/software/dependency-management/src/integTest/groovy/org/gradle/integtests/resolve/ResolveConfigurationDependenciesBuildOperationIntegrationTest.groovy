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

package org.gradle.integtests.resolve

import org.gradle.api.internal.artifacts.configurations.ResolveConfigurationDependenciesBuildOperationType
import org.gradle.api.internal.initialization.DefaultScriptHandler
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.BuildOperationNotificationsFixture
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.resolve.ResolveFailureTestFixture
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.server.http.AuthScheme
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.junit.Test

class ResolveConfigurationDependenciesBuildOperationIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def failedResolve = new ResolveFailureTestFixture(buildFile, "compile")
    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    @SuppressWarnings("GroovyUnusedDeclaration")
    def operationNotificationsFixture = new BuildOperationNotificationsFixture(executer, temporaryFolder)

    def "resolved configurations are exposed via build operation"() {
        setup:
        buildFile << """
            allprojects {
                apply plugin: "java"
                repositories {
                    maven { url = '${mavenHttpRepo.uri}' }
                }
            }
            dependencies {
                implementation 'org.foo:hiphop:1.0'
                implementation 'org.foo:unknown:1.0' //does not exist
                implementation project(":child")
                implementation 'org.foo:rock:1.0' //contains unresolved transitive dependency
            }
        """
        failedResolve.prepare("compileClasspath")
        createDirs("child")
        settingsFile << "include 'child'"
        def m1 = mavenHttpRepo.module('org.foo', 'hiphop').publish()
        def m2 = mavenHttpRepo.module('org.foo', 'unknown')
        def m3 = mavenHttpRepo.module('org.foo', 'broken')
        def m4 = mavenHttpRepo.module('org.foo', 'rock').dependsOn(m3).publish()

        m1.allowAll()
        m2.allowAll()
        m3.pom.expectGetBroken()
        m4.allowAll()

        when:
        fails "checkDeps"

        then:
        failedResolve.assertFailurePresent(failure)
        def op = operations.first(ResolveConfigurationDependenciesBuildOperationType)
        op.details.configurationName == "compileClasspath"
        op.details.projectPath == ":"
        op.details.buildPath == ":"
        op.details.scriptConfiguration == false
        op.details.configurationDescription ==~ /Compile classpath for source set 'main'.*/
        op.details.configurationVisible == false
        op.details.configurationTransitive == true

        op.result.resolvedDependenciesCount == 4
    }

    def "resolved detached configurations are exposed"() {
        setup:
        buildFile << """
        repositories {
            maven { url = '${mavenHttpRepo.uri}' }
        }

        task resolve(type: Copy) {
            from project.configurations.detachedConfiguration(dependencies.create('org.foo:dep:1.0'))
            into "build/resolved"
        }

        """
        def m1 = mavenHttpRepo.module('org.foo', 'dep').publish()


        m1.allowAll()

        when:
        run "resolve"

        then:
        def op = operations.first(ResolveConfigurationDependenciesBuildOperationType)
        op.details.configurationName == "detachedConfiguration1"
        op.details.projectPath == ":"
        op.details.scriptConfiguration == false
        op.details.buildPath == ":"
        op.details.configurationDescription == null
        op.details.configurationVisible == true
        op.details.configurationTransitive == true

        op.result.resolvedDependenciesCount == 1
    }

    def "resolved configurations in composite builds are exposed via build operation"() {
        setup:
        def m1 = mavenHttpRepo.module('org.foo', 'app-dep').publish()
        def m2 = mavenHttpRepo.module('org.foo', 'root-dep').publish()

        setupComposite()
        buildFile << """
            allprojects {
                apply plugin: "java"
                repositories {
                    maven { url = '${mavenHttpRepo.uri}' }
                }
            }
            dependencies {
                implementation 'org.foo:root-dep:1.0'
                implementation 'org.foo:my-composite-app:1.0'
            }

            task resolve(type: Copy) {
                from configurations.compileClasspath
                into "build/resolved"
            }
        """


        m1.allowAll()
        m2.allowAll()

        when:
        run "resolve"

        then: "configuration of composite are exposed"
        def resolveOperations = operations.all(ResolveConfigurationDependenciesBuildOperationType)
        resolveOperations.size() == 2
        resolveOperations[0].details.configurationName == "compileClasspath"
        resolveOperations[0].details.projectPath == ":"
        resolveOperations[0].details.buildPath == ":"
        resolveOperations[0].details.scriptConfiguration == false
        resolveOperations[0].details.configurationDescription ==~ /Compile classpath for source set 'main'.*/
        resolveOperations[0].details.configurationVisible == false
        resolveOperations[0].details.configurationTransitive == true
        resolveOperations[0].result.resolvedDependenciesCount == 2

        and: "classpath configuration is exposed"
        resolveOperations[1].details.configurationName == "compileClasspath"
        resolveOperations[1].details.projectPath == ":"
        resolveOperations[1].details.buildPath == ":my-composite-app"
        resolveOperations[1].details.scriptConfiguration == false
        resolveOperations[1].details.configurationDescription == "Compile classpath for source set 'main'."
        resolveOperations[1].details.configurationVisible == false
        resolveOperations[1].details.configurationTransitive == true
        resolveOperations[1].result.resolvedDependenciesCount == 1
    }

    def "resolved configurations of composite builds as build dependencies are exposed"() {
        setup:
        def m1 = mavenHttpRepo.module('org.foo', 'root-dep').publish()
        setupComposite()
        buildFile << """
            buildscript {
                repositories {
                    maven { url = '${mavenHttpRepo.uri}' }
                }
                dependencies {
                    classpath 'org.foo:root-dep:1.0'
                    classpath 'org.foo:my-composite-app:1.0'
                }
            }

            apply plugin: "java"
        """


        m1.allowAll()

        when:
        if (resetClasspathConfiguration) {
            succeeds("buildEnvironment")
        } else {
            succeeds("buildEnvironment", "-D${DefaultScriptHandler.DISABLE_RESET_CONFIGURATION_SYSTEM_PROPERTY}=true")
        }

        then:
        def resolveOperations = operations.all(ResolveConfigurationDependenciesBuildOperationType)
        def classpathOperations = resetClasspathConfiguration
            ? [resolveOperations[0], resolveOperations[2]]
            : [resolveOperations[0]]
        resolveOperations.size() == resetClasspathConfiguration ? 3 : 2
        classpathOperations.each {
            assert it.details.configurationName == "classpath"
            assert it.details.projectPath == null
            assert it.details.buildPath == ":"
            assert it.details.scriptConfiguration == true
            assert it.details.configurationDescription == null
            assert it.details.configurationVisible == true
            assert it.details.configurationTransitive == true
            assert it.result.resolvedDependenciesCount == 2
        }

        resolveOperations[1].details.configurationName == "compileClasspath"
        resolveOperations[1].details.projectPath == ":"
        resolveOperations[1].details.buildPath == ":my-composite-app"
        resolveOperations[1].details.scriptConfiguration == false
        resolveOperations[1].details.configurationDescription == "Compile classpath for source set 'main'."
        resolveOperations[1].details.configurationVisible == false
        resolveOperations[1].details.configurationTransitive == true
        resolveOperations[1].result.resolvedDependenciesCount == 1

        where:
        resetClasspathConfiguration << [true, false]
    }

    def "#scriptType script classpath configurations are exposed"() {
        setup:
        def m1 = mavenHttpRepo.module('org.foo', 'root-dep').publish()

        def initScript = file('init.gradle')
        initScript << ''
        executer.usingInitScript(initScript)

        file('scriptPlugin.gradle') << '''
        task foo
        '''

        buildFile << '''
        apply from: 'scriptPlugin.gradle'
        '''

        file(scriptFileName) << """
            $scriptBlock {
                repositories {
                    maven { url = '${mavenHttpRepo.uri}' }
                }
                dependencies {
                    classpath 'org.foo:root-dep:1.0'
                }
            }

        """

        m1.allowAll()
        when:
        run "foo"

        then:
        def resolveOperations = operations.all(ResolveConfigurationDependenciesBuildOperationType)
        resolveOperations.size() == 1
        resolveOperations[0].details.buildPath == ":"
        resolveOperations[0].details.configurationName == "classpath"
        resolveOperations[0].details.projectPath == null
        resolveOperations[0].details.scriptConfiguration == true
        resolveOperations[0].details.configurationDescription == null
        resolveOperations[0].details.configurationVisible == true
        resolveOperations[0].details.configurationTransitive == true
        resolveOperations[0].result.resolvedDependenciesCount == 1

        where:
        scriptType      | scriptBlock   | scriptFileName
        "project build" | 'buildscript' | getDefaultBuildFileName()
        "script plugin" | 'buildscript' | "scriptPlugin.gradle"
        "settings"      | 'buildscript' | 'settings.gradle'
        "init"          | 'initscript'  | 'init.gradle'
    }

    def "included build classpath configuration resolution result is exposed"() {
        setup:
        def m1 = mavenHttpRepo.module('org.foo', 'some-dep').publish()

        createDirs("projectB", "projectB/sub1")
        file("projectB/settings.gradle") << """
        rootProject.name = 'project-b'
        include "sub1"
        """

        file("projectB/build.gradle") << """
                buildscript {
                    repositories {
                        maven { url = '${mavenHttpRepo.uri}' }
                    }
                    dependencies {
                        classpath "org.foo:some-dep:1.0"
                    }
                }
                allprojects {
                    apply plugin: 'java'
                    group = "org.sample"
                    version = "1.0"
                }

        """

        settingsFile << """
            includeBuild 'projectB'
        """

        buildFile << """
            buildscript {
                dependencies {

                    classpath 'org.sample:sub1:1.0'
                }
            }
            task foo
        """

        m1.allowAll()
        executer.requireIsolatedDaemons()
        when:
        run "foo"

        then:
        def op = operations.first(ResolveConfigurationDependenciesBuildOperationType) {
            it.details.configurationName == 'classpath' && it.details.buildPath == ':projectB'
        }
        op.result.resolvedDependenciesCount == 1
    }

    private void setupComposite() {
        file("my-composite-app/src/main/java/App.java") << "public class App {}"
        file("my-composite-app/build.gradle") << """
            group = "org.foo"
            version = '1.0'

            apply plugin: "java"
            repositories {
                maven { url = '${mavenHttpRepo.uri}' }
            }

            dependencies {
                implementation 'org.foo:app-dep:1.0'
            }

            tasks.withType(JavaCompile) {
                options.annotationProcessorPath = files()
            }
        """
        file("my-composite-app/settings.gradle") << "rootProject.name = 'my-composite-app'"

        settingsFile << """
        rootProject.name='root'
        includeBuild 'my-composite-app'
        """
        mavenHttpRepo.module('org.foo', 'app-dep').publish().allowAll()
    }

    def "failed resolved configurations are exposed via build operation"() {
        given:
        MavenHttpModule a
        MavenHttpModule b
        MavenHttpModule leaf1
        MavenHttpModule leaf2
        mavenHttpRepo.with {
            a = module('org', 'a', '1.0').dependsOn('org', 'leaf', '1.0').publish()
            b = module('org', 'b', '1.0').dependsOn('org', 'leaf', '2.0').publish()
            leaf1 = module('org', 'leaf', '1.0').publish()
            leaf2 = module('org', 'leaf', '2.0').publish()
        }

        when:
        buildFile << """
            repositories {
                maven { url = '${mavenHttpRepo.uri}' }
            }

            configurations {
                compile {
                    resolutionStrategy.failOnVersionConflict()
                }
            }

            dependencies {
               compile 'org:a:1.0'
               compile 'org:b:1.0'
            }
"""
        failedResolve.prepare()

        a.pom.expectGet()
        b.pom.expectGet()
        leaf1.pom.expectGet()
        leaf2.pom.expectGet()

        then:
        fails "checkDeps"

        and:
        failedResolve.assertFailurePresent(failure)
        def op = operations.first(ResolveConfigurationDependenciesBuildOperationType)
        op.details.configurationName == "compile"
        op.failure == "org.gradle.api.internal.artifacts.ivyservice.TypedResolveException: Could not resolve all dependencies for configuration ':compile'."
        failure.assertHasCause("""Conflict found for the following module:
  - org:leaf between versions 2.0 and 1.0""")
        op.result != null
        op.result.resolvedDependenciesCount == 2
    }

    // This documents the current behavior, not necessarily the smartest one.
    // FTR This behaves the same in 4.7, 4.8 and 4.9
    def "non fatal errors incur no resolution failure"() {
        def mod = mavenHttpRepo.module('org', 'a', '1.0')
        mod.pomFile << "corrupt"

        when:
        buildFile << """
            repositories {
                maven { url = '${mavenHttpRepo.uri}' }
            }

            configurations {
                compile
            }

            dependencies {
               compile 'org:a:1.0'
            }
"""
        failedResolve.prepare()

        then:
        mod.allowAll()
        fails "checkDeps"

        and:
        failedResolve.assertFailurePresent(failure)
        def op = operations.first(ResolveConfigurationDependenciesBuildOperationType)
        op.details.configurationName == "compile"
        op.failure == null
        op.result.resolvedDependenciesCount == 1
    }

    @ToBeFixedForConfigurationCache(because = "Runtime classpath for CompileJava task is resolved even though the task will not run")
    def "resolved components contain their source repository name, even when taken from the cache"() {
        setup:
        def secondMavenHttpRepo = new MavenHttpRepository(server, '/repo-2', new MavenFileRepository(file('maven-repo-2')))

        // 'direct1' 'transitive1' and 'child-transitive1' are found in 'maven1'
        mavenHttpRepo.module('org.foo', 'transitive1').publish().allowAll()
        mavenHttpRepo.module('org.foo', 'direct1').publish().allowAll()
        mavenHttpRepo.module('org.foo', 'child-transitive1').publish().allowAll()

        // 'direct2' 'transitive2', and 'child-transitive2' are found in 'maven2' (unpublished in 'maven1')
        mavenHttpRepo.module('org.foo', 'direct2').allowAll()
        secondMavenHttpRepo.module('org.foo', 'direct2')
            .dependsOn('org.foo', 'transitive1', '1.0')
            .dependsOn('org.foo', 'transitive2', '1.0')
            .publish().allowAll()
        mavenHttpRepo.module('org.foo', 'transitive2').allowAll()
        secondMavenHttpRepo.module('org.foo', 'transitive2').publish().allowAll()
        mavenHttpRepo.module('org.foo', 'child-transitive2').allowAll()
        secondMavenHttpRepo.module('org.foo', 'child-transitive2').publish().allowAll()

        buildFile << """
            apply plugin: "java"
            repositories {
                maven {
                    name = 'maven1'
                    url = "${mavenHttpRepo.uri}"
                }
                maven {
                    name = 'maven2'
                    url = "${secondMavenHttpRepo.uri}"
                }
            }
            dependencies {
                implementation 'org.foo:direct1:1.0'
                implementation 'org.foo:direct2:1.0'
                implementation project(':child')
            }

            task resolve(type: Copy) {
                from configurations.runtimeClasspath
                into "build/resolved"
            }

            project(':child') {
                apply plugin: "java"
                dependencies {
                    implementation 'org.foo:child-transitive1:1.0'
                    implementation 'org.foo:child-transitive2:1.0'
                }
            }
        """
        createDirs("child")
        settingsFile << "include 'child'"

        def verifyExpectedOperation = {
            def ops = operations.all(ResolveConfigurationDependenciesBuildOperationType)
            assert ops.size() == 1
            def op = ops[0]
            def maven1Id = repoId('maven1', op.details)
            def maven2Id = repoId('maven2', op.details)
            assert op.result.resolvedDependenciesCount == 3
            def resolvedComponents = op.result.components
            assert resolvedComponents.size() == 8
            assert resolvedComponents.'root project :'.repoId == null
            assert resolvedComponents.'org.foo:direct1:1.0'.repoId == maven1Id
            assert resolvedComponents.'org.foo:direct2:1.0'.repoId == maven2Id
            assert resolvedComponents.'org.foo:transitive1:1.0'.repoId == maven1Id
            assert resolvedComponents.'org.foo:transitive2:1.0'.repoId == maven2Id
            assert resolvedComponents.'project :child'.repoId == null
            assert resolvedComponents.'org.foo:child-transitive1:1.0'.repoId == maven1Id
            assert resolvedComponents.'org.foo:child-transitive2:1.0'.repoId == maven2Id
            return true
        }

        when:
        succeeds 'resolve'

        then:
        verifyExpectedOperation()

        when:
        server.resetExpectations()
        succeeds 'resolve'

        then:
        verifyExpectedOperation()
    }

    def "resolved components contain their source repository name when resolution fails"() {
        setup:
        mavenHttpRepo.module('org.foo', 'transitive1').publish().allowAll()
        mavenHttpRepo.module('org.foo', 'direct1')
            .dependsOn('org.foo', 'transitive1', '1.0')
            .publish().allowAll()

        buildFile << """
            apply plugin: "java"
            repositories {
                maven {
                    name = 'maven1'
                    url = "${mavenHttpRepo.uri}"
                }
            }
            dependencies {
                implementation 'org.foo:direct1:1.0'
                implementation 'org.foo:missing-direct:1.0' // does not exist
                implementation project(':child')
            }

            project(':child') {
                apply plugin: "java"
                dependencies {
                    implementation 'org.foo:broken-transitive:1.0' // throws exception trying to resolve
                }
            }
        """
        failedResolve.prepare("runtimeClasspath")
        createDirs("child")
        settingsFile << "include 'child'"

        when:
        mavenHttpRepo.module('org.foo', 'missing-direct').allowAll()
        mavenHttpRepo.module('org.foo', 'broken-transitive').pom.expectGetBroken()

        and:
        fails ':checkDeps'

        then:
        failedResolve.assertFailurePresent(failure)
        def op = operations.first(ResolveConfigurationDependenciesBuildOperationType)
        def repoId = repoId('maven1', op.details)
        def resolvedComponents = op.result.components
        resolvedComponents.size() == 4
        resolvedComponents.'root project :'.repoId == null
        resolvedComponents.'project :child'.repoId == null
        resolvedComponents.'org.foo:direct1:1.0'.repoId == repoId
        resolvedComponents.'org.foo:transitive1:1.0'.repoId == repoId
    }

    @ToBeFixedForConfigurationCache(because = "Dependency resolution does not run for a from-cache build")
    def "resolved components contain their source repository id, even when they are structurally identical"() {
        setup:
        buildFile << """
            apply plugin: "java"
            repositories {
                maven {
                    name = 'withoutCreds'
                    url = "${mavenHttpRepo.uri}"
                }
                maven {
                    name = 'withCreds'
                    url = "${mavenHttpRepo.uri}"
                    credentials {
                        username = 'foo'
                        password = 'bar'
                    }
                }
            }
            dependencies {
                implementation 'org.foo:good:1.0'
            }

            task resolve(type: Copy) {
                from configurations.compileClasspath
                into "build/resolved"
            }
        """
        def module = mavenHttpRepo.module('org.foo', 'good').publish()
        server.authenticationScheme = AuthScheme.BASIC
        server.allowGetOrHead('/repo/org/foo/good/1.0/good-1.0.pom', 'foo', 'bar', module.pomFile)
        server.allowGetOrHead('/repo/org/foo/good/1.0/good-1.0.jar', 'foo', 'bar', module.artifactFile)

        when:
        succeeds 'resolve'

        then:
        def op = operations.first(ResolveConfigurationDependenciesBuildOperationType)
        op.details.repositories.size() == 2
        op.details.repositories*.id.unique(false).size() == 2
        op.details.repositories[0].name == 'withoutCreds'
        op.details.repositories[1].name == 'withCreds'
        def repo1Id = op.details.repositories[1].id
        def resolvedComponents = op.result.components
        resolvedComponents.size() == 2
        resolvedComponents.'org.foo:good:1.0'.repoId == repo1Id

        when:
        server.resetExpectations()
        succeeds 'resolve'

        then:
        // This demonstrates a bug in Gradle, where we ignore the requirement for credentials when retrieving from the cache
        def op2 = operations.first(ResolveConfigurationDependenciesBuildOperationType)
        op2.details.repositories.size() == 2
        op2.details.repositories*.id.unique(false).size() == 2
        op2.details.repositories[0].name == 'withoutCreds'
        op2.details.repositories[1].name == 'withCreds'
        def repo2Id = op2.details.repositories[0].id
        def resolvedComponents2 = op2.result.components
        resolvedComponents2.size() == 2
        resolvedComponents2.'org.foo:good:1.0'.repoId == repo2Id
    }

    def "resolved components contain their source repository id, even when the repository definitions are modified"() {
        mavenRepo.module('org.foo', 'good').publish()

        setup:
        buildFile << """
            apply plugin: "java"
            repositories {
                maven {
                    name = 'one'
                    url = "${mavenRepo.uri}"
                }
            }
            configurations {
                compileClasspath {
                    incoming.afterResolve {
                        project.repositories.clear()
                        project.repositories {
                            maven {
                                name = 'two'
                                url = "${mavenRepo.uri}"
                            }
                            mavenCentral()
                        }
                    }
                }
            }

            dependencies {
                implementation 'org.foo:good:1.0'
                testImplementation 'junit:junit:4.11'
            }

            task resolve1(type: Sync) {
                from configurations.compileClasspath
                into 'out1'
            }
            task resolve2(type: Sync) {
                from configurations.testCompileClasspath
                into 'out2'
                mustRunAfter(tasks.resolve1)
            }
        """
        file("src/main/java/Thing.java") << "public class Thing { }"
        file("src/test/java/ThingTest.java") << """
            import ${Test.name};
            public class ThingTest {
                @Test
                public void ok() { }
            }
        """

        when:
        succeeds 'resolve1', 'resolve2'

        then:
        def ops = operations.all(ResolveConfigurationDependenciesBuildOperationType)
        def op = ops[0]
        op.details.configurationName == 'compileClasspath'
        op.details.repositories.size() == 1
        def repo1Id = repoId('one', op.details)
        def resolvedComponents = op.result.components
        resolvedComponents.size() == 2
        resolvedComponents.'org.foo:good:1.0'.repoId == repo1Id
        def op2 = ops[1]
        op2.details.configurationName == 'testCompileClasspath'
        op2.details.repositories.size() == 2
        def repo2Id = repoId('two', op2.details)
        def resolvedComponents2 = op2.result.components
        resolvedComponents2.size() == 4
        resolvedComponents2.'org.foo:good:1.0'.repoId == repo2Id
    }

    def "resolved component op includes configuration requested attributes"() {
        setup:
        mavenHttpRepo.module('org.foo', 'stuff').publish().allowAll()

        createDirs("fixtures")
        settingsFile << "include 'fixtures'"
        buildFile << """
            plugins {
                id 'java-library'
            }
            ${mavenCentralRepository()}
            repositories { maven { url = '${mavenHttpRepo.uri}' } }
            testing.suites.test {
                useJUnit()
                dependencies {
                    implementation testFixtures(project(':fixtures'))
                }
            }
        """
        file("fixtures/build.gradle") << """
            plugins {
                id 'java-library'
                id 'java-test-fixtures'
            }
            repositories { maven { url = '${mavenHttpRepo.uri}' } }
            dependencies {
                testFixturesApi('org.foo:stuff:1.0')
            }
        """
        file("fixtures/src/testFixtures/java/SomeClass.java") << "class SomeClass {}"
        file("src/test/java/SomeTest.java") <<
            """
            public class SomeTest {
                @org.junit.Test
                public void test() { }
            }
            """

        when:
        succeeds ':test'

        then:
        operations.all(ResolveConfigurationDependenciesBuildOperationType, { it.details.configurationName.endsWith('Classpath') }).result.every {
            it.requestedAttributes.find { it.name == 'org.gradle.dependency.bundling' }.value == 'external'
            it.requestedAttributes.find { it.name == 'org.gradle.jvm.version' }.value
        }
        operations.all(ResolveConfigurationDependenciesBuildOperationType, { it.details.configurationName.endsWith('CompileClasspath') }).result.every {
            it.requestedAttributes.find { it.name == 'org.gradle.usage' }.value == 'java-api'
            it.requestedAttributes.find { it.name == 'org.gradle.libraryelements' }.value == 'classes'
        }
        operations.all(ResolveConfigurationDependenciesBuildOperationType, { it.details.configurationName.endsWith('RuntimeClasspath') }).result.every {
            it.requestedAttributes.find { it.name == 'org.gradle.usage' }.value == 'java-runtime'
            it.requestedAttributes.find { it.name == 'org.gradle.libraryelements' }.value == 'jar'
        }
    }

    private String repoId(String repoName, Map<String, ?> details) {
        return details.repositories.find { it.name == repoName }.id
    }
}
