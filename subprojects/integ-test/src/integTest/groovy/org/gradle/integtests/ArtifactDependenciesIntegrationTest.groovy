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
package org.gradle.integtests

import org.gradle.integtests.fixtures.ExecutionFailure
import org.gradle.integtests.fixtures.IvyRepository
import org.gradle.integtests.fixtures.MavenRepository
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.internal.AbstractIntegrationTest
import org.gradle.util.TestFile
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import spock.lang.Issue
import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.startsWith

class ArtifactDependenciesIntegrationTest extends AbstractIntegrationTest {
    @Rule
    public final TestResources testResources = new TestResources()

    @Before
    public void setup() {
        distribution.requireOwnUserHomeDir()
    }

    @Test
    public void canResolveDependenciesFromAFlatDir() {
        File buildFile = testFile("projectWithFlatDir.gradle");
        usingBuildFile(buildFile).run();
    }

    @Test
    public void canHaveConfigurationHierarchy() {
        File buildFile = testFile("projectWithConfigurationHierarchy.gradle");
        usingBuildFile(buildFile).run();
    }

    @Test
    public void dependencyReportWithConflicts() {
        File buildFile = testFile("projectWithConflicts.gradle");
        usingBuildFile(buildFile).run();
        usingBuildFile(buildFile).withDependencyList().run();
    }

    @Test
    public void canNestModules() throws IOException {
        File buildFile = testFile("projectWithNestedModules.gradle");
        usingBuildFile(buildFile).run();
    }

    @Test
    public void canHaveCycleInDependencyGraph() throws IOException {
        File buildFile = testFile("projectWithCyclesInDependencyGraph.gradle");
        usingBuildFile(buildFile).run();
    }

    @Test
    public void canUseDynamicVersions() throws IOException {
        File buildFile = testFile("projectWithDynamicVersions.gradle");
        usingBuildFile(buildFile).run();
    }

    @Test
    public void resolutionFailsWhenProjectHasNoRepositoriesEvenWhenArtifactIsCachedLocally() {
        testFile('settings.gradle') << 'include "a", "b"'
        testFile('build.gradle') << """
subprojects {
    configurations {
        compile
    }
    task listDeps << { configurations.compile.each { } }
}
project(':a') {
    repositories {
        mavenRepo urls: '${repo().rootDir.toURI()}'
    }
    dependencies {
        compile 'org.gradle.test:external1:1.0'
    }
}
project(':b') {
    dependencies {
        compile 'org.gradle.test:external1:1.0'
    }
}
"""
        repo().module('org.gradle.test', 'external1', '1.0').publishArtifact()

        inTestDirectory().withTasks('a:listDeps').run()
        def result = inTestDirectory().withTasks('b:listDeps').runWithFailure()
        result.assertThatCause(containsString('unresolved dependency: org.gradle.test#external1;1.0: not found'))
    }

    @Test
    @Issue("GRADLE-1342")
    public void resolutionDoesNotUseCachedArtifactFromDifferentRepository() {
        def repo1 = new MavenRepository(testFile('repo1'))
        repo1.module('org.gradle.test', 'external1', '1.0').publishArtifact()
        def repo2 = new MavenRepository(testFile('repo2'))

        testFile('settings.gradle') << 'include "a", "b"'
        testFile('build.gradle') << """
subprojects {
    configurations {
        compile
    }
    task listDeps << { configurations.compile.each { } }
}
project(':a') {
    repositories {
        mavenRepo urls: '${repo1.rootDir.toURI()}'
    }
    dependencies {
        compile 'org.gradle.test:external1:1.0'
    }
}
project(':b') {
    repositories {
        mavenRepo urls: '${repo2.rootDir.toURI()}'
    }
    dependencies {
        compile 'org.gradle.test:external1:1.0'
    }
}
"""

        inTestDirectory().withTasks('a:listDeps').run()
        def result = inTestDirectory().withTasks('b:listDeps').runWithFailure()
        result.assertThatCause(containsString('unresolved dependency: org.gradle.test#external1;1.0: not found'))
    }

    @Test
    @Issue("GRADLE-1567")
    public void resolutionDifferentiatesBetweenArtifactsThatDifferOnlyInClassifier() {
        def repo = repo()
        repo.module('org.gradle.test', 'external1', '1.0', 'classifier1').publishArtifact().text = "jar:classifier1 content"
        repo.module('org.gradle.test', 'external1', '1.0', 'classifier2').publishArtifact().text = "jar:classifier2 content"

        testFile('settings.gradle') << 'include "a", "b", "c"'
        testFile('build.gradle') << """
subprojects {
    repositories {
        mavenRepo urls: '${repo.rootDir.toURI()}'
    }
    configurations {
        compile
    }
}
project(':a') {
    dependencies {
        compile 'org.gradle.test:external1:1.0:classifier1'
    }
    task test(dependsOn: configurations.compile) << {
        assert configurations.compile.collect { it.text } == ['jar:classifier1 content']
    }
}
project(':b') {
    dependencies {
        compile 'org.gradle.test:external1:1.0:classifier2'
    }
    task test(dependsOn: configurations.compile) << {
        assert configurations.compile.collect { it.text } == ['jar:classifier2 content']
    }
}
"""

        inTestDirectory().withTasks('a:test').run()
        inTestDirectory().withTasks('b:test').run()
    }

    @Test
    @Issue("GRADLE-739")
    public void singleConfigurationCanContainMultipleArtifactsThatOnlyDifferByClassifier() {
        def repo = repo()
        repo.module('org.gradle.test', 'external1', '1.0').publishArtifact()
        repo.module('org.gradle.test', 'external1', '1.0', 'baseClassifier').publishArtifactOnly()
        repo.module('org.gradle.test', 'external1', '1.0', 'extendedClassifier').publishArtifactOnly()
        repo.module('org.gradle.test', 'other', '1.0').publishArtifact()

        testFile('build.gradle') << """
repositories {
    mavenRepo urls: '${repo.rootDir.toURI()}'
}
configurations {
    base
    extendedWithClassifier.extendsFrom base
    extendedWithOther.extendsFrom base
    justDefault
    justClassifier
    rawBase
    rawExtended.extendsFrom rawBase
    cBase
    cExtended.extendsFrom cBase
}
dependencies {
    base 'org.gradle.test:external1:1.0'
    base 'org.gradle.test:external1:1.0:baseClassifier'
    extendedWithClassifier 'org.gradle.test:external1:1.0:extendedClassifier'
    extendedWithOther 'org.gradle.test:other:1.0'
    justDefault 'org.gradle.test:external1:1.0'
    justClassifier 'org.gradle.test:external1:1.0:baseClassifier'
    justClassifier 'org.gradle.test:external1:1.0:extendedClassifier'
    rawBase 'org.gradle.test:external1:1.0'
    rawExtended 'org.gradle.test:external1:1.0:extendedClassifier'
}

def checkDeps(config, expectedDependencies) {
    assert config.collect({ it.name }) as Set == expectedDependencies as Set
}

task test << {
    checkDeps configurations.base, ['external1-1.0.jar', 'external1-1.0-baseClassifier.jar']
    checkDeps configurations.extendedWithOther, ['external1-1.0.jar', 'external1-1.0-baseClassifier.jar', 'other-1.0.jar']
    checkDeps configurations.extendedWithClassifier, ['external1-1.0.jar', 'external1-1.0-baseClassifier.jar', 'external1-1.0-extendedClassifier.jar']
    checkDeps configurations.justDefault, ['external1-1.0.jar']
    checkDeps configurations.justClassifier, ['external1-1.0-baseClassifier.jar', 'external1-1.0-extendedClassifier.jar']
    checkDeps configurations.rawBase, ['external1-1.0.jar']
    checkDeps configurations.rawExtended, ['external1-1.0.jar', 'external1-1.0-extendedClassifier.jar']
}
"""
        inTestDirectory().withTasks('test').run()
    }

    @Test
    @Issue("GRADLE-739")
    public void canUseClassifiersCombinedWithArtifactWithNonStandardPackaging() {
        def repo = repo()
        repo.module('org.gradle.test', 'external1', '1.0', null, 'zip').publishArtifact()
        repo.module('org.gradle.test', 'external1', '1.0', 'baseClassifier').publishArtifactOnly()
        repo.module('org.gradle.test', 'external1', '1.0', 'extendedClassifier').publishArtifactOnly()
        repo.module('org.gradle.test', 'external1', '1.0', null, 'txt').publishArtifactOnly()
        repo.module('org.gradle.test', 'other', '1.0').publishArtifact()

        testFile('build.gradle') << """
repositories {
    mavenRepo urls: '${repo.rootDir.toURI()}'
}
configurations {
    base
    extended.extendsFrom base
    extendedWithClassifier.extendsFrom base
    extendedWithType.extendsFrom base
}
dependencies {
    base 'org.gradle.test:external1:1.0'
    base 'org.gradle.test:external1:1.0:baseClassifier'
    extended 'org.gradle.test:other:1.0'
    extendedWithClassifier 'org.gradle.test:external1:1.0:extendedClassifier'
    extendedWithType 'org.gradle.test:external1:1.0@txt'
}

def checkDeps(config, expectedDependencies) {
    assert config.collect({ it.name }) as Set == expectedDependencies as Set
}

task test << {
    checkDeps configurations.base, ['external1-1.0.zip', 'external1-1.0-baseClassifier.jar']
    checkDeps configurations.extended, ['external1-1.0.zip', 'external1-1.0-baseClassifier.jar', 'other-1.0.jar']
    checkDeps configurations.extendedWithClassifier, ['external1-1.0.zip', 'external1-1.0-baseClassifier.jar', 'external1-1.0-extendedClassifier.jar']
    checkDeps configurations.extendedWithType, ['external1-1.0.zip', 'external1-1.0-baseClassifier.jar', 'external1-1.0.txt']
}
"""
        inTestDirectory().withTasks('test').run()
    }

    @Test
    @Issue("GRADLE-739")
    public void configurationCanContainMultipleArtifactsThatOnlyDifferByType() {
        def repo = repo()
        repo.module('org.gradle.test', 'external1', '1.0').publishArtifact()
        repo.module('org.gradle.test', 'external1', '1.0', null, 'zip').publishArtifactOnly()
        repo.module('org.gradle.test', 'external1', '1.0', 'classifier').publishArtifactOnly()
        repo.module('org.gradle.test', 'external1', '1.0', 'classifier', 'bin').publishArtifactOnly()

        testFile('build.gradle') << """
repositories {
    mavenRepo urls: '${repo.rootDir.toURI()}'
}
configurations {
    base
    extended.extendsFrom base
    extended2.extendsFrom base
}
dependencies {
    base 'org.gradle.test:external1:1.0'
    base 'org.gradle.test:external1:1.0@zip'
    extended 'org.gradle.test:external1:1.0:classifier'
    extended2 'org.gradle.test:external1:1.0:classifier@bin'
}

def checkDeps(config, expectedDependencies) {
    assert config.collect({ it.name }) as Set == expectedDependencies as Set
}

task test << {
    checkDeps configurations.base, ['external1-1.0.jar', 'external1-1.0.zip']
    checkDeps configurations.extended, ['external1-1.0.jar', 'external1-1.0.zip', 'external1-1.0-classifier.jar']
    checkDeps configurations.extended2, ['external1-1.0.jar', 'external1-1.0.zip', 'external1-1.0-classifier.bin']
}
"""
        inTestDirectory().withTasks('test').run()
    }


    @Test
    public void excludedDependenciesAreNotRetrieved() {
        def repo = repo()
        repo.module('org.gradle.test', 'one', '1.0').publishArtifact()
        repo.module('org.gradle.test', 'two', '1.0').publishArtifact()
        repo.module('org.gradle.test', 'external1', '1.0').dependsOn('org.gradle.test', 'one', '1.0').publishArtifact()
        repo.module('org.gradle.test', 'external1', '1.0', 'classifier').publishArtifactOnly()

        testFile('build.gradle') << """
repositories {
    mavenRepo urls: '${repo.rootDir.toURI()}'
}
configurations {
    reference
    base
    extended.extendsFrom base
    extendedWithClassifier.extendsFrom base
}
dependencies {
    reference 'org.gradle.test:external1:1.0'
    base 'org.gradle.test:external1:1.0', {
        exclude module: 'one'
    }
    extended 'org.gradle.test:two:1.0'
    extendedWithClassifier 'org.gradle.test:external1:1.0:classifier'
}

def checkDeps(config, expectedDependencies) {
    assert config.collect({ it.name }) as Set == expectedDependencies as Set
}

task test << {
    checkDeps configurations.reference, ['external1-1.0.jar', 'one-1.0.jar']
    checkDeps configurations.base, ['external1-1.0.jar']
    checkDeps configurations.extended, ['external1-1.0.jar', 'two-1.0.jar']
    checkDeps configurations.extendedWithClassifier, ['external1-1.0.jar', 'external1-1.0-classifier.jar']
}
"""
        inTestDirectory().withTasks('test').run()
    }

    /*
     * Originally, we were aliasing dependency descriptors that were identical. This caused alias errors when we subsequently modified one of these descriptors.
     */
    @Test
    public void addingClassifierToDuplicateDependencyDoesNotAffectOriginal() {
        def repo = repo()
        repo.module('org.gradle.test', 'external1', '1.0').publishArtifact()
        repo.module('org.gradle.test', 'external1', '1.0', 'withClassifier').publishArtifact()

        testFile('build.gradle') << """
repositories {
    mavenRepo urls: '${repo.rootDir.toURI()}'
}
configurations {
    a
    b
}
dependencies {
    a 'org.gradle.test:external1:1.0'
    b 'org.gradle.test:external1:1.0', 'org.gradle.test:external1:1.0:withClassifier'
}

def checkDeps(config, expectedDependencies) {
    assert config.collect({ it.name }) as Set == expectedDependencies as Set
}

task test << {
    checkDeps configurations.a, ['external1-1.0.jar']
    checkDeps configurations.b, ['external1-1.0-withClassifier.jar', 'external1-1.0.jar']
}
"""
        inTestDirectory().withTasks('test').run()
    }

    @Test
    public void reportsUnknownDependencyError() {
        File buildFile = testFile("projectWithUnknownDependency.gradle");
        ExecutionFailure failure = usingBuildFile(buildFile).runWithFailure();
        failure.assertHasFileName("Build file '" + buildFile.getPath() + "'");
        failure.assertHasDescription("Execution failed for task ':listJars'");
        failure.assertThatCause(startsWith("Could not resolve all dependencies for configuration ':compile'"));
        failure.assertThatCause(containsString("unresolved dependency: test#unknownProjectA;1.2: not found"));
        failure.assertThatCause(containsString("unresolved dependency: test#unknownProjectB;2.1.5: not found"));
    }

    @Test
    public void reportsProjectDependsOnSelfError() {
        TestFile buildFile = testFile("build.gradle");
        buildFile << '''
            configurations { compile }
            dependencies { compile project(':') }
            defaultTasks 'listJars'
            task listJars << { configurations.compile.each { println it } }
'''
        ExecutionFailure failure = usingBuildFile(buildFile).runWithFailure();
        failure.assertHasFileName("Build file '" + buildFile.getPath() + "'");
        failure.assertHasDescription("Execution failed for task ':listJars'");
        failure.assertThatCause(startsWith("Could not resolve all dependencies for configuration ':compile'"));
        failure.assertThatCause(containsString("a module is not authorized to depend on itself"));
    }

    @Test
    public void canSpecifyProducerTasksForFileDependency() {
        testFile("settings.gradle").write("include 'sub'");
        testFile("build.gradle") << '''
            configurations { compile }
            dependencies { compile project(path: ':sub', configuration: 'compile') }
            task test(dependsOn: configurations.compile) << {
                assert file('sub/sub.jar').isFile()
            }
'''
        testFile("sub/build.gradle") << '''
            configurations { compile }
            dependencies { compile files('sub.jar') { builtBy 'jar' } }
            task jar << { file('sub.jar').text = 'content' }
'''

        inTestDirectory().withTasks("test").run().assertTasksExecuted(":sub:jar", ":test");
    }

    @Test
    public void resolvedProjectArtifactsContainProjectVersionInTheirNames() {
        testFile('settings.gradle').write("include 'a', 'b'");
        testFile('a/build.gradle') << '''
            apply plugin: 'base'
            configurations { compile }
            task aJar(type: Jar) { }
            artifacts { compile aJar }
'''
        testFile('b/build.gradle') << '''
            apply plugin: 'base'
            version = 'early'
            configurations { compile }
            task bJar(type: Jar) { }
            gradle.taskGraph.whenReady { project.version = 'late' }
            artifacts { compile bJar }
'''
        testFile('build.gradle') << '''
            configurations { compile }
            dependencies { compile project(path: ':a', configuration: 'compile'), project(path: ':b', configuration: 'compile') }
            task test(dependsOn: configurations.compile) << {
                assert configurations.compile.collect { it.name } == ['a.jar', 'b-late.jar']
            }
'''
        inTestDirectory().withTasks('test').run()
    }

    @Test
    public void canUseArtifactSelectorForProjectDependencies() {
        testFile('settings.gradle').write("include 'a', 'b'");
        testFile('a/build.gradle') << '''
            apply plugin: 'base'
            configurations { 'default' {} }
            task aJar(type: Jar) { }
            artifacts { 'default' aJar }
'''
        testFile('b/build.gradle') << '''
            configurations { compile }
            dependencies { compile(project(':a')) { artifact { name = 'a'; type = 'jar' } } }
            task test {
                inputs.files configurations.compile
                doFirst {
                    assert [project(':a').tasks.aJar.archivePath] as Set == configurations.compile.files
                }
            }
'''
        inTestDirectory().withTasks('test').run()
    }

    @Test
    public void canHaveCycleInProjectDependencies() {
        inTestDirectory().withTasks('listJars').run();
    }

    @Test
    public void canHaveNonTransitiveProjectDependencies() {
        repo().module("group", "externalA", 1.5).publishArtifact()

        testFile('settings.gradle') << "include 'a', 'b'"

        testFile('build.gradle') << '''
allprojects {
    apply plugin: 'java'
    repositories { mavenRepo urls: rootProject.uri('repo') }
}
project(':a') {
    dependencies {
        compile 'group:externalA:1.5'
        compile files('libs/externalB.jar')
    }
}
project(':b') {
    configurations.compile.transitive = false
    dependencies {
        compile project(':a') { transitive = false }
    }
    task listJars << {
        assert configurations.compile.collect { it.name } == ['a.jar']
    }
}
'''

        inTestDirectory().withTasks('listJars').run()
    }

    MavenRepository repo() {
        return new MavenRepository(testFile('repo'))
    }

    IvyRepository ivyRepo() {
        return new IvyRepository(testFile('ivy-repo'))
    }
}

