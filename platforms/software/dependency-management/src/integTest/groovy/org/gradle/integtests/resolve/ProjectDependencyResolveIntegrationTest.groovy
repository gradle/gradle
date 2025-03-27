/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.StableConfigurationCacheDeprecations
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.extensions.FluidDependenciesResolveTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import spock.lang.Issue

@FluidDependenciesResolveTest
class ProjectDependencyResolveIntegrationTest extends AbstractIntegrationSpec implements StableConfigurationCacheDeprecations {
    private ResolveTestFixture resolve = new ResolveTestFixture(buildFile, "compile")

    def setup() {
        resolve.addDefaultVariantDerivationStrategy()
        settingsFile << """
            rootProject.name = 'test'
        """
    }

    def "project dependency includes artifacts and transitive dependencies of default configuration in target project"() {
        given:
        mavenRepo.module("org.other", "externalA", "1.2").publish()
        mavenRepo.module("org.other", "externalB", "2.1").publish()

        and:
        settingsFile << """
            include 'a'
            include 'b'
            dependencyResolutionManagement {
                repositories { maven { url = '$mavenRepo.uri' } }
            }
        """

        and:
        file("a/build.gradle") << """
            configurations {
                api
                'default' { extendsFrom api }
            }
            dependencies {
                api "org.other:externalA:1.2"
                'default' "org.other:externalB:2.1"
            }
            task jar(type: Jar) {
                archiveBaseName = 'a'
                destinationDirectory = buildDir
            }
            artifacts { api jar }
        """

        file("b/build.gradle") << """
            group = 'org.gradle'
            version = '1.0'

            configurations {
                compile
            }
            dependencies {
                compile project(':a')
            }
        """

        resolve.prepare()

        expect:
        succeeds ":b:checkDeps"
        executedAndNotSkipped ":a:jar"
        resolve.expectGraph {
            root(":b", "org.gradle:b:1.0") {
                project(":a", "test:a:") {
                    module("org.other:externalA:1.2")
                    module("org.other:externalB:2.1")
                }
            }
        }
    }

    def "project dependency that specifies a target configuration includes artifacts and transitive dependencies of selected configuration"() {
        given:
        mavenRepo.module("org.other", "externalA", "1.2").publish()

        and:
        settingsFile << """
            include 'a'
            include 'b'
            dependencyResolutionManagement {
                repositories { maven { url = '$mavenRepo.uri' } }
            }
        """

        file("a/build.gradle") << """
            plugins {
                id("base")
            }
            configurations {
                api
                runtime { extendsFrom api }
            }
            dependencies {
                api("org.other:externalA:1.2") {
                    because 'also check dependency reasons'
                }
            }
            task jar(type: Jar) { archiveBaseName = 'a' }
            artifacts { api jar }
        """

        file("b/build.gradle") << """
            configurations {
                compile
            }
            dependencies {
                compile(project(path: ':a', configuration: 'runtime')) {
                    because 'can provide a dependency reason for project dependencies too'
                }
            }
        """


        resolve.prepare()

        when:
        succeeds ':b:checkDeps'

        then:
        executedAndNotSkipped ":a:jar"
        resolve.expectGraph {
            root(":b", "test:b:") {
                project(":a", 'test:a:') {
                    notRequested()
                    byReason('can provide a dependency reason for project dependencies too')
                    variant('runtime')
                    module('org.other:externalA:1.2') {
                        notRequested()
                        byReason('also check dependency reasons')
                        variant('runtime', ['org.gradle.status': 'release', 'org.gradle.category': 'library', 'org.gradle.usage': 'java-runtime', 'org.gradle.libraryelements': 'jar'])
                    }
                }
            }
        }
    }

    @Issue("GRADLE-2899")
    def "multiple project configurations can refer to different configurations of target project"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"

        and:
        file("a/build.gradle") << """
            plugins {
                id("base")
            }
            configurations {
                configA1
                configA2
            }
            task A1jar(type: Jar) {
                archiveBaseName = 'A1'
            }
            task A2jar(type: Jar) {
                archiveBaseName = 'A2'
            }
            artifacts {
                configA1 A1jar
                configA2 A2jar
            }
        """

        file("b/build.gradle") << """
            configurations {
                configB1
                configB2
            }
            dependencies {
                configB1 project(path:':a', configuration:'configA1')
                configB2 project(path:':a', configuration:'configA2')
            }
        """

        resolve.prepare {
            config("configB1")
            config("configB2")
        }

        when:
        run ":b:checkConfigB1"

        then:
        executedAndNotSkipped ":a:A1jar"
        resolve.expectGraph {
            root(":b", "test:b:") {
                project(":a", "test:a:") {
                    configuration("configA1")
                    artifact(name: "A1")
                }
            }
        }

        when:
        run ":b:checkConfigB2"

        then:
        executedAndNotSkipped ":a:A2jar"
        resolve.expectGraph {
            root(":b", "test:b:") {
                project(":a", "test:a:") {
                    configuration("configA2")
                    artifact(name: "A2")
                }
            }
        }
    }

    def "resolved project artifacts reflect project properties changed after task graph is resolved"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"

        and:
        file('a/build.gradle') << '''
            plugins {
                id("base")
            }
            configurations { compile }
            dependencies { compile project(path: ':b', configuration: 'compile') }
            task aJar(type: Jar) { }
            gradle.taskGraph.whenReady { project.version = 'late' }
            artifacts { compile aJar }
        '''

        file('b/build.gradle') << '''
            plugins {
                id("base")
            }
            version = 'early'
            configurations { compile }
            task bJar(type: Jar) { }
            gradle.taskGraph.whenReady { project.version = 'transitive-late' }
            artifacts { compile bJar }
        '''

        file('build.gradle') << '''
            configurations {
                compile
                testCompile { extendsFrom compile }
            }
            dependencies { compile project(path: ':a', configuration: 'compile') }
        '''

        resolve.prepare("testCompile")

        when:
        run ":checkDeps"

        then:
        executedAndNotSkipped ":a:aJar", ":b:bJar"
        resolve.expectDefaultConfiguration("compile")
        resolve.expectGraph {
            root(":", ":test:") {
                project(":a", "test:a:") {
                    artifact(fileName: "a-late.jar") // only the file name is affected (this is the current behaviour, not necessarily the desired behaviour)
                    project(":b", "test:b:early") {
                        artifact(fileName: "b-transitive-late.jar")
                    }
                }
            }
        }
    }

    @UnsupportedWithConfigurationCache(because = "configure task changes jar task")
    def "resolved project artifact can be changed by configuration task"() {
        given:
        file('settings.gradle') << "include 'a'"

        and:
        file('a/build.gradle') << '''
            plugins {
                id("base")
            }
            configurations { compile }
            task configureJar {
                doLast {
                    tasks.aJar.archiveExtension = "txt"
                    tasks.aJar.archiveClassifier = "modified"
                }
            }
            task aJar(type: Jar) {
                dependsOn configureJar
            }
            artifacts { compile aJar }
        '''

        file('build.gradle') << '''
            configurations {
                compile
                testCompile { extendsFrom compile }
            }
            dependencies { compile project(path: ':a', configuration: 'compile') }
            task test(dependsOn: [configurations.compile, configurations.testCompile]) {
                doLast {
                    assert configurations.compile.collect { it.name } == ['a-modified.txt']
                    assert configurations.testCompile.collect { it.name } == ['a-modified.txt']
                }
            }
        '''

        expect:
        succeeds ":test"
        executedAndNotSkipped ":a:configureJar", ":a:aJar"
    }

    def "project dependency that references an artifact includes the matching artifact only plus the transitive dependencies of referenced configuration"() {
        given:
        mavenRepo.module("group", "externalA", "1.5").publish()

        and:
        settingsFile << """
            include 'a'
            include 'b'
            dependencyResolutionManagement {
                repositories { maven { url = '$mavenRepo.uri' } }
            }
        """

        file("a/build.gradle") << """
            plugins {
                id("base")
            }

            configurations {
                deps
                'default' { extendsFrom deps }
            }
            dependencies { deps 'group:externalA:1.5' }
            task xJar(type: Jar) { archiveBaseName='x' }
            task yJar(type: Jar) { archiveBaseName='y' }
            artifacts { 'default' xJar, yJar }
        """

        file("b/build.gradle") << """
            plugins {
                id("base")
            }

            configurations { compile }
            dependencies { compile(project(':a')) { artifact { name = 'y'; type = 'jar' } } }
        """

        resolve.prepare("compile")

        when:
        run 'b:checkDeps'

        then:
        executedAndNotSkipped ":a:yJar"
        resolve.expectGraph {
            root(":b", "test:b:") {
                project(":a", "test:a:") {
                    artifact(name: "y", type: "jar")
                    module("group:externalA:1.5")
                }
            }
        }
    }

    def "reports project dependency that refers to an unknown artifact"() {
        given:
        file('settings.gradle') << """
            include 'a'
            include 'b'
        """

        and:
        file("a/build.gradle") << """
            group = 'test'
            configurations { 'default' {} }
        """

        file("b/build.gradle") << """
            group = 'test'
            configurations { compile }
            dependencies { compile(project(':a')) { artifact { name = 'b'; type = 'jar' } } }
            task test {
                inputs.files configurations.compile
                outputs.upToDateWhen { false }
                doFirst {
                    configurations.compile.files.collect { it.name }
                }
            }
        """

        expect:
        fails ':b:test'

        and:
        failure.assertHasCause("Could not resolve all dependencies for configuration ':b:compile'.")
        failure.assertHasCause("Could not find b.jar (project :a).")
    }

    def "non-transitive project dependency includes only the artifacts of the target configuration"() {
        given:
        mavenRepo.module("group", "externalA", "1.5").publish()

        and:
        settingsFile << """
            include 'a'
            include 'b'
            dependencyResolutionManagement {
                repositories { maven { url = '$mavenRepo.uri' } }
            }
        """

        file("a/build.gradle") << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation 'group:externalA:1.5'
                implementation files('libs/externalB.jar')
            }
        """

        file("b/build.gradle") << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation project(':a'), { transitive = false }
            }
        """

        resolve.prepare("runtimeClasspath")

        when:
        run ":b:checkDeps"

        then:
        executedAndNotSkipped ":a:jar"
        resolve.expectGraph {
            root(":b", "test:b:") {
                project(":a", "test:a:") {
                    configuration("runtimeElements")
                }
            }
        }
    }

    def "can have cycle in project dependencies"() {
        given:
        file('settings.gradle') << "include 'a', 'b', 'c'"

        and:
        def common = """
            plugins {
                id("base")
            }
            configurations {
                first
                other
                'default' {
                    extendsFrom first
                }
            }
            task jar(type: Jar)
            artifacts {
                'default' jar
            }
        """

        file("a/build.gradle") << """
            $common
            dependencies {
                first project(':b')
                other project(':b')
            }
        """
        file("b/build.gradle") << """
            $common
            dependencies {
                first project(':c')
            }
        """
        file("c/build.gradle") << """
            $common
            dependencies {
                first project(':a')
            }
        """

        when:
        resolve.prepare("first")
        run ":a:checkDeps"

        then:
        executedAndNotSkipped ":b:jar", ":c:jar"
        resolve.expectGraph {
            root(":a", "test:a:") {
                project(":b", "test:b:") {
                    project(":c", "test:c:") {
                        project(":a", "test:a:")
                    }
                }
            }
        }
    }

    // TODO #9591: This does not reflect desired behavior. The recursive copy is a detached configuration, which
    // effectively replaces the root component, preventing the consumable configuration from being selected.
    @Issue('GRADLE-3280')
    def "cannot resolve recursive copy of configuration with cyclic project dependencies"() {
        given:
        settingsFile << "include 'a', 'b', 'c'"
        def common = """
            plugins {
                id("base")
            }
            task jar(type: Jar)
            configurations {
                dependencyScope('implementation')
                resolvable("res") {
                    extendsFrom(implementation)
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "foo"))
                    }
                }
                consumable("cons") {
                    extendsFrom(implementation)
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "foo"))
                    }
                    outgoing.artifact(jar)
                }
            }
        """

        file("a/build.gradle") << """
            $common
            dependencies {
                implementation(project(":b"))
            }
            task assertCanResolve {
                def files = project.configurations.res.incoming.files
                doLast {
                    println(files*.name)
                }
            }
            task assertCanResolveRecursiveCopy {
                def files = project.configurations.res.copyRecursive().incoming.files
                doLast {
                    println(files*.name)
                }
            }
        """

        file("b/build.gradle") << """
            $common
            dependencies {
                implementation(project(':c'))
            }
        """

        file("c/build.gradle") << """
            $common
            dependencies {
                implementation(project(':a'))
            }
        """

        expect:
        succeeds(":a:assertCanResolve")

        when:
        executer.expectDocumentedDeprecationWarning("The resCopy configuration has been deprecated for consumption. This will fail with an error in Gradle 9.0. For more information, please refer to https://docs.gradle.org/current/userguide/declaring_dependencies.html#sec:deprecated-configurations in the Gradle documentation.")
        fails(":a:assertCanResolveRecursiveCopy")

        then:
        failure.assertHasCause("Cannot select root node 'resCopy' as a variant. Configurations should not act as both a resolution root and a variant simultaneously. Be sure to mark configurations meant for resolution as canBeConsumed=false or use the 'resolvable(String)' configuration factory method to create them.")
    }

    // this test is largely covered by other tests, but does ensure that there is nothing special about
    // project dependencies that are "built" by built in plugins like the Java plugin's created jars
    def "can use zip files as project dependencies"() {
        given:
        file("settings.gradle") << "include 'a'; include 'b'"
        file("a/some.txt") << "foo"
        file("a/build.gradle") << """
            group = "g"
            version = 1.0

            apply plugin: 'base'
            task zip(type: Zip) {
                from "some.txt"
            }

            artifacts {
                delegate.default zip
            }
        """
        file("b/build.gradle") << """
            configurations { conf }
            dependencies {
                conf project(":a")
            }

            task copyZip(type: Copy) {
                from configurations.conf
                into "\$buildDir/copied"
            }
        """

        when:
        succeeds ":b:copyZip"

        then:
        executedAndNotSkipped ":a:zip", ":b:copyZip"

        and:
        file("b/build/copied/a-1.0.zip").exists()
    }

    @ToBeFixedForConfigurationCache(because = "Task.getProject() during execution")
    def "resolving configuration with project dependency marks dependency's configuration as observed"() {
        settingsFile << """
            include 'api'
            include 'impl'
        """

        file("api/build.gradle") << """
            configurations {
                conf
            }
            configurations.create("default").extendsFrom(configurations.conf)
        """

        file("impl/build.gradle") << """
            configurations {
                conf
            }
            configurations.create("default").extendsFrom(configurations.conf)

            dependencies {
                conf project(":api")
            }

            task check {
                doLast {
                    assert configurations.conf.state == Configuration.State.UNRESOLVED
                    assert project(":api").configurations.conf.state == Configuration.State.UNRESOLVED

                    configurations.conf.resolve()

                    assert configurations.conf.state == Configuration.State.RESOLVED
                    assert project(":api").configurations.conf.state == Configuration.State.UNRESOLVED

                    // Attempt to change the configuration, to demonstrate that is has been observed
                    project(":api").configurations.conf.dependencies.add(null)
                }
            }

        """

        when:
        expectTaskGetProjectDeprecations(3)
        fails("impl:check")

        then:
        failure.assertHasCause "Cannot change dependencies of dependency configuration ':api:conf' after it has been included in dependency resolution"
    }

    @Issue(["GRADLE-3330", "GRADLE-3362"])
    def "project dependency can resolve multiple artifacts from target project that are differentiated by archiveFileName only"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"

        and:
        file("a/build.gradle") << """
            plugins {
                id("base")
            }
            configurations {
                configOne
                configTwo
            }
            task A1jar(type: Jar) {
                archiveFileName = 'A1.jar'
            }
            task A2jar(type: Jar) {
                archiveFileName = 'A2.jar'
            }
            task A3jar(type: Jar) {
                archiveFileName = 'A3.jar'
            }
            artifacts {
                configOne A1jar
                configTwo A2jar
                configTwo A3jar
            }
        """

        file("b/build.gradle") << """
            configurations {
                configB
            }
            dependencies {
                configB project(path:':a', configuration:'configOne')
                configB project(path:':a', configuration:'configTwo')
            }
        """

        resolve.prepare("configB")

        when:
        succeeds ":b:checkDeps"

        then:
        executedAndNotSkipped ":a:A1jar", ":a:A2jar", ":a:A3jar"
        resolve.expectGraph {
            root(":b", "test:b:") {
                project(":a", "test:a:") {
                    configuration("configOne")
                    configuration("configTwo")
                    artifact(fileName: "A1.jar")
                    artifact(fileName: "A2.jar")
                    artifact(fileName: "A3.jar")
                }
                project(":a", "test:a:")
            }
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/25579")
    def "can lazily compute dependencies from results of another resolution which resolves current project"() {
        buildFile << """
            configurations {
                a
                b
                create("default")
            }

            dependencies {
                a project
            }

            configurations.b.dependencies.addAllLater provider(() -> {
                configurations.a.files
                []
            })

            configurations.a.files
        """

        expect:
        succeeds(":help")
    }

    @Issue("https://github.com/gradle/gradle/issues/25579")
    def "can lazily compute dependencies from results of another resolution which circularly depends on current project"() {
        buildFile << """
            configurations {
                a
                b
                create("default")
            }

            dependencies {
                a project(":other")
            }

            configurations.b.dependencies.addAllLater provider(() -> {
                configurations.a.files
                []
            })
        """

        settingsFile << "include 'other'"
        file("other/build.gradle") << """
            configurations {
                a
                b
                create("default")
            }

            dependencies {
                a project(":")
            }

            configurations.b.dependencies.addAllLater provider(() -> {
                configurations.a.files
                []
            })

            configurations.a.files
        """

        expect:
        succeeds(":help")
    }

    def "suggests outgoingVariants command when targetConfiguration not found in local project"() {
        given:
        settingsFile << """
            includeBuild 'included'
        """

        file("included/build.gradle.kts") << """
            group = "org"
            configurations {
                consumable("other")
            }
        """

        buildFile << """
            configurations {
                dependencyScope("deps")
                resolvable("resolver") {
                    extendsFrom(deps)
                }
            }

            dependencies {
                deps(${declaredDependency}) {
                    targetConfiguration = "absent"
                }
            }

            task resolve {
                def files = configurations.resolver.incoming.files
                doLast {
                    files.forEach { println(it) }
                }
            }
        """

        when:
        fails("resolve")

        then:
        failure.assertHasCause("A dependency was declared on configuration 'absent' of '${projectDescription}' but no variant with that configuration name exists.")
        failure.assertHasResolution("To determine which configurations are available in the target ${projectDescription}, run ${expectedCommand}.")

        expect:
        succeeds(expectedCommand)

        where:
        declaredDependency   | projectDescription  | expectedCommand
        "project(':')"       | "root project :"         | ":outgoingVariants"
        "'org:included:1.0'" | "project :included" | ":included:outgoingVariants"
    }

    def "getDependencyProject is deprecated"() {
        buildFile << """
            configurations {
                dependencyScope("foo")
            }

            dependencies {
                foo(project)
            }

            configurations.foo.dependencies.iterator().next().getDependencyProject()
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The ProjectDependency.getDependencyProject() method has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecate_get_dependency_project")
        succeeds("help")
    }
}
