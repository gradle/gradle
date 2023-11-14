/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.extensions.FluidDependenciesResolveTest
import spock.lang.Issue

@FluidDependenciesResolveTest
class ResolvedConfigurationIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def setup() {
        buildFile << """
            allprojects {
                apply plugin: "java"
            }
            repositories {
                maven { url '${mavenHttpRepo.uri}' }
            }
        """
    }

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "resolves strictly for dependency resolve failures when #expression is used"() {
        createDirs("child")
        settingsFile << "include 'child'"
        def m1 = mavenHttpRepo.module('org.foo', 'hiphop').publish()
        def m2 = mavenHttpRepo.module('org.foo', 'unknown')
        def m3 = mavenHttpRepo.module('org.foo', 'broken')
        def m4 = mavenHttpRepo.module('org.foo', 'rock').dependsOn(m3).publish()

        buildFile << """
            dependencies {
                implementation 'org.foo:hiphop:1.0'
                implementation 'org.foo:unknown:1.0' //does not exist
                implementation project(":child")

                implementation 'org.foo:rock:1.0' //contains unresolved transitive dependency
            }

            task validate {
                doLast {
                    def compileClasspath = configurations.compileClasspath.resolvedConfiguration
                    assert compileClasspath.hasError()
                    println "evaluating:"
                    compileClasspath.${expression}
                }
            }
        """

        m1.allowAll()
        m2.allowAll()
        m3.pom.expectGetBroken()
        m4.allowAll()

        expect:
        //TODO: fix dependency resolution results usage in this test and remove this flag
        executer.withBuildJvmOpts("-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true")

        fails "validate"
        outputContains("evaluating:") // ensure the failure happens when querying the resolved configuration
        failure.assertHasCause("Could not find org.foo:unknown:1.0.")
        failure.assertHasCause("Could not resolve org.foo:broken:1.0.")

        where:
        expression                                 | _
        "firstLevelModuleDependencies"             | _
        "getFirstLevelModuleDependencies { true }" | _
        "files { true }"                           | _
        "files"                                    | _
        "resolvedArtifacts"                        | _
    }

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "resolves strictly for artifact resolve failures when #expression is used"() {
        createDirs("child")
        settingsFile << "include 'child'"
        def m1 = mavenHttpRepo.module('org.foo', 'hiphop').publish()
        def m2 = mavenHttpRepo.module('org.foo', 'unknown').publish()
        def m3 = mavenHttpRepo.module('org.foo', 'broken').publish()
        def m4 = mavenHttpRepo.module('org.foo', 'rock').dependsOn(m3).publish()

        buildFile << """
            dependencies {
                implementation 'org.foo:hiphop:1.0'
                implementation 'org.foo:unknown:1.0' //does not exist
                implementation project(":child")

                implementation 'org.foo:rock:1.0' //contains unresolved transitive dependency
            }

            task validate {
                doLast {
                    def compile = configurations.compileClasspath.resolvedConfiguration

                    assert !compile.hasError() // all dependencies resolved ok
                    assert compile.lenientConfiguration.unresolvedModuleDependencies.empty
                    assert compile.resolvedArtifacts.size() == 5 // Does not filter broken or missing files

                    println "evaluating:"
                    compile.${expression}
                }
            }
        """

        m1.allowAll()
        m2.pom.expectGet()
        m2.artifact.expectGetMissing()
        m3.pom.expectGet()
        m3.artifact.expectGetBroken()
        m4.allowAll()

        expect:
        //TODO: fix dependency resolution results usage in this test and remove this flag
        executer.withBuildJvmOpts("-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true")

        fails "validate"
        outputContains("evaluating:") // ensure the failure happens when querying the resolved configuration
        failure.assertHasCause("Could not find unknown-1.0.jar (org.foo:unknown:1.0).")
        failure.assertHasCause("Could not download broken-1.0.jar (org.foo:broken:1.0)")

        where:
        expression                                 | _
        "files { true }"                           | _
        "files"                                    | _
    }

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "resolves leniently for dependency resolve failures"() {
        createDirs("child")
        settingsFile << "include 'child'"
        def m1 = mavenHttpRepo.module('org.foo', 'hiphop').publish()
        def m2 = mavenHttpRepo.module('org.foo', 'unknown')
        def m3 = mavenHttpRepo.module('org.foo', 'broken')
        def m4 = mavenHttpRepo.module('org.foo', 'rock').dependsOn(m3).publish()

        buildFile << """
            dependencies {
                implementation 'org.foo:hiphop:1.0'
                implementation 'org.foo:unknown:1.0' //does not exist
                implementation project(":child")

                implementation 'org.foo:rock:1.0' //contains unresolved transitive dependency
            }

            task validate {
                doLast {
                    LenientConfiguration compile = configurations.compileClasspath.resolvedConfiguration.lenientConfiguration

                    def resolved = compile.firstLevelModuleDependencies

                    assert resolved.size() == 3
                    assert resolved.collect { it.moduleName } == ['hiphop', 'child', 'rock']

                    resolved = compile.getFirstLevelModuleDependencies { true }
                    assert resolved.collect { it.moduleName } == ['hiphop', 'child', 'rock']

                    def files = compile.files

                    assert files.size() == 3
                    assert files.collect { it.name } == ['hiphop-1.0.jar', 'child.jar', 'rock-1.0.jar']

                    files = compile.getFiles { true }

                    assert files.collect { it.name } == ['hiphop-1.0.jar', 'child.jar', 'rock-1.0.jar']

                    def artifacts = compile.artifacts

                    assert artifacts.size() == 3
                    assert artifacts.collect { it.file.name } == ['hiphop-1.0.jar', 'child.jar', 'rock-1.0.jar']

                    artifacts = compile.getArtifacts { true }

                    assert artifacts.collect { it.file.name } == ['hiphop-1.0.jar', 'child.jar', 'rock-1.0.jar']

                    def unresolved = compile.unresolvedModuleDependencies
                    assert unresolved.size() == 2
                    assert unresolved.find { it.selector.group == 'org.foo' && it.selector.name == 'unknown' && it.selector.version == '1.0' }
                    assert unresolved.find { it.selector.name == 'broken' }
                }
            }
        """

        m1.allowAll()
        m2.allowAll()
        m3.pom.expectGetUnauthorized()
        m4.allowAll()

        expect:
        //TODO: fix dependency resolution results usage in this test and remove this flag
        executer.withBuildJvmOpts("-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true")
        succeeds "validate"
    }

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "doesn't reintegrate evicted edges into graph"() {
        def a1 = mavenHttpRepo.module('org.foo', 'a')
        def b1 = mavenHttpRepo.module('org.foo', 'b')
        def b2 = mavenHttpRepo.module('org.foo', 'b', '2.0')
        def c1 = mavenHttpRepo.module('org.foo', 'c')
        def d1 = mavenHttpRepo.module('org.foo', 'd')
        def e1 = mavenHttpRepo.module('org.foo', 'e')
        def f1 = mavenHttpRepo.module('org.foo', 'f')

        a1.dependsOn(c1).publish()
        c1.publish()

        d1.dependsOn(b1).publish()
        b1.dependsOn(f1).publish()
        f1.publish()
        e1.dependsOn(b2).publish()
        b2.publish()

        buildFile << """
            dependencies {
                implementation 'org.foo:a:1.0'
                implementation 'org.foo:b:1.0'
                implementation 'org.foo:d:1.0'
                implementation 'org.foo:e:1.0'

                modules.module('org.foo:c') { replacedBy('org.foo:f') }
            }

            task validate {
                doLast {
                    LenientConfiguration compile = configurations.compileClasspath.resolvedConfiguration.lenientConfiguration

                    def resolved = compile.firstLevelModuleDependencies

                    assert resolved.size() == 4
                    assert resolved.collect { it.moduleName } == ['a', 'd', 'e', 'b']

                    resolved = compile.getFirstLevelModuleDependencies { true }
                    assert resolved.collect { it.moduleName } == ['a', 'd', 'e', 'b']

                    def files = compile.files.collect { it.name }

                    assert files.size() == 5
                    assert files == ['a-1.0.jar', 'd-1.0.jar', 'e-1.0.jar', 'b-2.0.jar', 'f-1.0.jar']

                    files = compile.getFiles { true }.collect { it.name }

                    assert files == ['a-1.0.jar', 'f-1.0.jar', 'd-1.0.jar', 'b-2.0.jar', 'e-1.0.jar']

                    def artifacts = compile.artifacts.collect { it.file.name }

                    assert artifacts.size() == 5
                    assert artifacts == ['a-1.0.jar', 'd-1.0.jar', 'e-1.0.jar', 'b-2.0.jar', 'f-1.0.jar']

                    artifacts = compile.getArtifacts { true }.collect { it.file.name }

                    assert artifacts == ['a-1.0.jar', 'f-1.0.jar', 'd-1.0.jar', 'b-2.0.jar', 'e-1.0.jar']

                    def unresolved = compile.unresolvedModuleDependencies
                    assert unresolved.size() == 0
                }
            }
        """

        a1.allowAll()
        b1.allowAll()
        b2.allowAll()
        c1.allowAll()
        d1.allowAll()
        e1.allowAll()
        f1.allowAll()

        expect:
        //TODO: fix dependency resolution results usage in this test and remove this flag
        executer.withBuildJvmOpts("-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true")
        succeeds "validate"
    }

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "lenient for both dependency and artifact resolve and download failures"() {
        createDirs("child")
        settingsFile << "include 'child'"
        def m1 = mavenHttpRepo.module('org.foo', 'hiphop').publish()
        def m2 = mavenHttpRepo.module('org.foo', 'unknown')
        def m3 = mavenHttpRepo.module('org.foo', 'broken')
        def m4 = mavenHttpRepo.module('org.foo', 'rock').dependsOn(m3).publish()

        buildFile << """
            dependencies {
                implementation 'org.foo:hiphop:1.0'
                implementation 'org.foo:unknown:1.0' //does not exist
                implementation project(":child")

                implementation 'org.foo:rock:1.0' //contains unresolved transitive dependency, plus missing jar
            }

            task validate {
                doLast {
                    LenientConfiguration compile = configurations.compileClasspath.resolvedConfiguration.lenientConfiguration

                    def resolved = compile.firstLevelModuleDependencies

                    assert resolved.size() == 3
                    assert resolved.collect { it.moduleName } == ['hiphop', 'child', 'rock']

                    resolved = compile.getFirstLevelModuleDependencies { true }
                    assert resolved.collect { it.moduleName } == ['hiphop', 'child', 'rock']

                    def files = compile.files

                    assert files.size() == 2
                    assert files.collect { it.name } == ['hiphop-1.0.jar', 'child.jar']

                    files = compile.getFiles { true }

                    assert files.collect { it.name } == ['hiphop-1.0.jar', 'child.jar']

                    def artifacts = compile.artifacts

                    assert artifacts.size() == 2
                    assert artifacts.collect { it.file.name } == ['hiphop-1.0.jar', 'child.jar']

                    artifacts = compile.getArtifacts { true }

                    assert artifacts.collect { it.file.name } == ['hiphop-1.0.jar', 'child.jar']

                    def unresolved = compile.unresolvedModuleDependencies
                    assert unresolved.size() == 2
                    assert unresolved.find { it.selector.group == 'org.foo' && it.selector.name == 'unknown' && it.selector.version == '1.0' }
                    assert unresolved.find { it.selector.name == 'broken' }
                }
            }
        """

        m1.allowAll()
        m2.allowAll()
        m3.pom.expectGetUnauthorized()
        m4.pom.expectGet()
        m4.artifact.expectGetUnauthorized()

        expect:
        //TODO: fix dependency resolution results usage in this test and remove this flag
        executer.withBuildJvmOpts("-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true")
        succeeds "validate"
    }

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "resolves leniently from mixed confs"() {
        def m1 = mavenHttpRepo.module('org.foo', 'hiphop').publish()
        def m2 = mavenHttpRepo.module('org.foo', 'unknown')

        buildFile << """
            configurations {
                someConf
            }

            dependencies {
                implementation 'org.foo:hiphop:1.0'
                someConf 'org.foo:unknown:1.0' //does not exist
            }

            task validate {
                doLast {
                    LenientConfiguration compile = configurations.compileClasspath.resolvedConfiguration.lenientConfiguration

                    def unresolved = compile.getUnresolvedModuleDependencies()
                    def resolved = compile.getFirstLevelModuleDependencies(Specs.SATISFIES_ALL)

                    assert resolved.size() == 1
                    assert resolved.find { it.moduleName == 'hiphop' }
                    assert unresolved.size() == 0

                    LenientConfiguration someConf = configurations.someConf.resolvedConfiguration.lenientConfiguration

                    unresolved = someConf.getUnresolvedModuleDependencies()
                    resolved = someConf.getFirstLevelModuleDependencies(Specs.SATISFIES_ALL)

                    assert resolved.size() == 0
                    assert unresolved.size() == 1
                    assert unresolved.find { it.selector.name == 'unknown' }
                }
            }
        """

        m1.allowAll()
        m2.allowAll()

        expect:
        //TODO: fix dependency resolution results usage in this test and remove this flag
        executer.withBuildJvmOpts("-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true")
        succeeds "validate"
    }

    @Issue("gradle/gradle#3401")
    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "first-level dependencies should include modules selected by agreement between selectors"() {
        def foo1 = mavenHttpRepo.module('org', 'foo', '1').publish()
        def foo2 = mavenHttpRepo.module('org', 'foo', '2').publish()
        def foo3 = mavenHttpRepo.module('org', 'foo', '3').publish()
        def bar1 = mavenHttpRepo.module('org', 'bar', '1')
            .dependsOn(foo2).publish()

        buildFile << """
            dependencies {
                implementation 'org:foo:[1,3]'
                implementation 'org:bar:1'
            }

            task validate {
                doLast {
                    LenientConfiguration compile = configurations.compileClasspath.resolvedConfiguration.lenientConfiguration

                    def resolved = compile.firstLevelModuleDependencies

                    assert resolved.collect { "\${it.moduleName}:\${it.moduleVersion}" } == ['bar:1', 'foo:2']

                    resolved = compile.getFirstLevelModuleDependencies { true }
                    assert resolved.collect { "\${it.moduleName}:\${it.moduleVersion}" } == ['bar:1', 'foo:2']

                    def files = compile.files

                    assert files.size() == 2
                    assert files.collect { it.name } == ['bar-1.jar', 'foo-2.jar']

                    files = compile.getFiles { true }

                    assert files.collect { it.name } == ['bar-1.jar', 'foo-2.jar']

                    def artifacts = compile.artifacts

                    assert artifacts.size() == 2
                    assert artifacts.collect { it.file.name } == ['bar-1.jar', 'foo-2.jar']

                    artifacts = compile.getArtifacts { true }

                    assert artifacts.collect { it.file.name } == ['bar-1.jar', 'foo-2.jar']

                }
            }
        """

        foo1.rootMetaData.allowGetOrHead()
        foo1.allowAll()
        foo2.allowAll()
        foo3.allowAll()
        bar1.allowAll()

        expect:
        //TODO: fix dependency resolution results usage in this test and remove this flag
        executer.withBuildJvmOpts("-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true")
        succeeds "validate"
    }

    @ToBeFixedForConfigurationCache(because = "ResolvedConfiguration is CC incompatible")
    def "classifier and extension do not need to match file name"() {
        given:
        buildFile.text = """
            configurations {
                consumable("con") {
                    outgoing.artifact(file("foo.txt")) {
                        classifier = "HELLO"
                        extension = "123"
                    }
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.LIBRARY))
                    }
                }
                dependencyScope("implementation")
                resolvable("res") {
                    extendsFrom(implementation)
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.LIBRARY))
                    }
                }
            }

            dependencies {
                implementation project(":")
            }

            task resolve {
                doLast {
                    def artifact = configurations.res.resolvedConfiguration.resolvedArtifacts.first()

                    // This is not necessarily desired behavior.
                    // It is very confusing that these values disagree
                    assert artifact.file.name == "foo.txt"
                    assert artifact.classifier == "HELLO"
                    assert artifact.extension == "123"
                }
            }
        """

        expect:
        succeeds("resolve")
    }
}
