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

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.FluidDependenciesResolveRunner
import org.gradle.integtests.fixtures.TestResources
import org.gradle.test.fixtures.file.TestFile
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import spock.lang.Issue

import static org.hamcrest.CoreMatchers.containsString

/**
 * This test contains some of the original coverage for dependency resolution.
 * These tests should be migrated to live with the rest of the coverage over time.
 */
@RunWith(FluidDependenciesResolveRunner)
class ArtifactDependenciesIntegrationTest extends AbstractIntegrationTest {
    @Rule
    public final TestResources testResources = new TestResources(testDirectoryProvider)

    @Before
    public void setup() {
        executer.requireOwnGradleUserHomeDir()
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
    task listDeps { doLast { configurations.compile.each { } } }
}
project(':a') {
    repositories {
        maven { url '${repo.uri}' }
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
        repo.module('org.gradle.test', 'external1', '1.0').publish()

        inTestDirectory().withTasks('a:listDeps').run()
        def result = inTestDirectory().withTasks('b:listDeps').runWithFailure()
        result.assertHasCause('Cannot resolve external dependency org.gradle.test:external1:1.0 because no repositories are defined.')
    }

    @Test
    public void resolutionFailsForMissingArtifact() {
        testFile('build.gradle') << """
repositories {
    maven { url '${repo.uri}' }
}
configurations {
    compile; missingExt; missingClassifier
}
dependencies {
    compile "org.gradle.test:lib:1.0"
    missingExt "org.gradle.test:lib:1.0@zip"
    missingClassifier "org.gradle.test:lib:1.0:classifier1"
}
task listJar { doLast { configurations.compile.each { } } }
task listMissingExt { doLast { configurations.missingExt.each { } } }
task listMissingClassifier { doLast { configurations.missingClassifier.each { } } }
"""

        def module = repo.module('org.gradle.test', 'lib', '1.0')
        module.publish()

        inTestDirectory().withTasks('listJar').run()

        def result = inTestDirectory().withTasks('listMissingExt').runWithFailure()
        result.assertHasCause("""Could not find lib.zip (org.gradle.test:lib:1.0).
Searched in the following locations:
    ${module.artifactFile(type: 'zip').toURL()}""")

        result = inTestDirectory().withTasks('listMissingClassifier').runWithFailure()
        result.assertHasCause("""Could not find lib-classifier1.jar (org.gradle.test:lib:1.0).
Searched in the following locations:
    ${module.artifactFile(classifier: 'classifier1').toURL()}""")
    }

    @Test
    @Issue("GRADLE-1342")
    public void resolutionDoesNotUseCachedArtifactFromDifferentRepository() {
        def repo1 = maven('repo1')
        repo1.module('org.gradle.test', 'external1', '1.0').publish()
        def repo2 = maven('repo2')

        testFile('settings.gradle') << 'include "a", "b"'
        testFile('build.gradle') << """
subprojects {
    configurations {
        compile
    }
    task listDeps { doLast { configurations.compile.each { } } }
}
project(':a') {
    repositories {
        maven { url '${repo1.uri}' }
    }
    dependencies {
        compile 'org.gradle.test:external1:1.0'
    }
}
project(':b') {
    repositories {
        maven { url '${repo2.uri}' }
    }
    dependencies {
        compile 'org.gradle.test:external1:1.0'
    }
}
"""

        inTestDirectory().withTasks('a:listDeps').run()
        def result = inTestDirectory().withTasks('b:listDeps').runWithFailure()
        result.assertThatCause(containsString('Could not find org.gradle.test:external1:1.0.'))
    }

    @Test
    public void artifactFilesPreserveFixedOrder() {
        repo.module('org', 'leaf1').publish()
        repo.module('org', 'leaf2').publish()
        repo.module('org', 'leaf3').publish()
        repo.module('org', 'leaf4').publish()

        repo.module('org', 'middle1').dependsOnModules("leaf1", "leaf2").publish()
        repo.module('org', 'middle2').dependsOnModules("leaf3", "leaf4").publish()

        repo.module('org', 'top').dependsOnModules("middle1", "middle2").publish()

        testFile('build.gradle') << """
            repositories {
                maven { url '${repo.uri}' }
            }
            configurations {
                compile
            }
            dependencies {
                compile "org:middle2:1.0", "org:middle1:1.0"
            }
            task test {
                doLast {
                    assert configurations.compile.files.collect { it.name } == ['middle2-1.0.jar', 'middle1-1.0.jar', 'leaf3-1.0.jar', 'leaf4-1.0.jar', 'leaf1-1.0.jar', 'leaf2-1.0.jar']
                }
            }
        """

        executer.withTasks("test").run()
    }

    @Test
    public void exposesMetaDataAboutResolvedArtifactsInAFixedOrder() {
        def module = repo.module('org.gradle.test', 'lib', '1.0')
        module.artifact(type: 'zip')
        module.artifact(classifier: 'classifier')
        module.publish()
        repo.module('org.gradle.test', 'dist', '1.0').hasType('zip').publish()

        testFile('build.gradle') << """
repositories {
    maven { url '${repo.uri}' }
}
configurations {
    compile
}
dependencies {
    compile "org.gradle.test:lib:1.0"
    compile "org.gradle.test:lib:1.0:classifier"
    compile "org.gradle.test:lib:1.0@zip"
    compile "org.gradle.test:dist:1.0"
}
task test {
    doLast {
        assert configurations.compile.files.collect { it.name } == ['lib-1.0.jar', 'lib-1.0-classifier.jar', 'lib-1.0.zip', 'dist-1.0.zip']
        def artifacts = configurations.compile.resolvedConfiguration.resolvedArtifacts as List
        assert artifacts.size() == 4
        assert artifacts[0].name == 'lib'
        assert artifacts[0].type == 'jar'
        assert artifacts[0].extension == 'jar'
        assert artifacts[0].classifier == null
        assert artifacts[1].name == 'lib'
        assert artifacts[1].type == 'jar'
        assert artifacts[1].extension == 'jar'
        assert artifacts[1].classifier == 'classifier'
        assert artifacts[2].name == 'lib'
        assert artifacts[2].type == 'zip'
        assert artifacts[2].extension == 'zip'
        assert artifacts[2].classifier == null
        assert artifacts[3].name == 'dist'
        assert artifacts[3].type == 'zip'
        assert artifacts[3].extension == 'zip'
        assert artifacts[3].classifier == null
    }
}
"""

        inTestDirectory().withTasks('test').run()
    }

    @Test
    @Issue("GRADLE-1567")
    public void resolutionDifferentiatesBetweenArtifactsThatDifferOnlyInClassifier() {
        def module = repo.module('org.gradle.test', 'external1', '1.0')
        module.artifact(classifier: 'classifier1')
        module.artifact(classifier: 'classifier2')
        module.publish()

        testFile('settings.gradle') << 'include "a", "b", "c"'
        testFile('build.gradle') << """
subprojects {
    repositories {
        maven { url '${repo.uri}' }
    }
    configurations {
        compile
    }
}
project(':a') {
    dependencies {
        compile 'org.gradle.test:external1:1.0:classifier1'
    }
    task test(dependsOn: configurations.compile) {
        doLast {
            assert configurations.compile.collect { it.name } == ['external1-1.0-classifier1.jar']
            assert configurations.compile.resolvedConfiguration.resolvedArtifacts.collect { "\${it.name}-\${it.classifier}" } == ['external1-classifier1']
        }
    }
}
project(':b') {
    dependencies {
        compile 'org.gradle.test:external1:1.0:classifier2'
    }
    task test(dependsOn: configurations.compile) {
        doLast {
            assert configurations.compile.collect { it.name } == ['external1-1.0-classifier2.jar']
            assert configurations.compile.resolvedConfiguration.resolvedArtifacts.collect { "\${it.name}-\${it.classifier}" } == ['external1-classifier2']
        }
    }
}
"""

        inTestDirectory().withTasks('a:test').run()
        inTestDirectory().withTasks('b:test').run()
    }

    @Test
    @Issue("GRADLE-739")
    public void singleConfigurationCanContainMultipleArtifactsThatOnlyDifferByClassifier() {
        def module = repo.module('org.gradle.test', 'external1', '1.0')
        module.artifact(classifier: 'baseClassifier')
        module.artifact(classifier: 'extendedClassifier')
        module.publish()
        repo.module('org.gradle.test', 'other', '1.0').publish()

        testFile('build.gradle') << """
repositories {
    maven { url '${repo.uri}' }
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

task test {
    doLast {
        checkDeps configurations.base, ['external1-1.0.jar', 'external1-1.0-baseClassifier.jar']
        checkDeps configurations.extendedWithOther, ['external1-1.0.jar', 'external1-1.0-baseClassifier.jar', 'other-1.0.jar']
        checkDeps configurations.extendedWithClassifier, ['external1-1.0.jar', 'external1-1.0-baseClassifier.jar', 'external1-1.0-extendedClassifier.jar']
        checkDeps configurations.justDefault, ['external1-1.0.jar']
        checkDeps configurations.justClassifier, ['external1-1.0-baseClassifier.jar', 'external1-1.0-extendedClassifier.jar']
        checkDeps configurations.rawBase, ['external1-1.0.jar']
        checkDeps configurations.rawExtended, ['external1-1.0.jar', 'external1-1.0-extendedClassifier.jar']
    }
}
"""
        inTestDirectory().withTasks('test').run()
    }

    @Test
    @Issue("GRADLE-739")
    public void canUseClassifiersCombinedWithArtifactWithNonStandardPackaging() {
        def module = repo.module('org.gradle.test', 'external1', '1.0')
        module.artifact(type: 'txt')
        module.artifact(classifier: 'baseClassifier', type: 'jar')
        module.artifact(classifier: 'extendedClassifier', type: 'jar')
        module.hasType('zip')
        module.publish()
        repo.module('org.gradle.test', 'other', '1.0').publish()

        testFile('build.gradle') << """
repositories {
    maven { url '${repo.uri}' }
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

task test {
    doLast {
        checkDeps configurations.base, ['external1-1.0.zip', 'external1-1.0-baseClassifier.jar']
        checkDeps configurations.extended, ['external1-1.0.zip', 'external1-1.0-baseClassifier.jar', 'other-1.0.jar']
        checkDeps configurations.extendedWithClassifier, ['external1-1.0.zip', 'external1-1.0-baseClassifier.jar', 'external1-1.0-extendedClassifier.jar']
        checkDeps configurations.extendedWithType, ['external1-1.0.zip', 'external1-1.0-baseClassifier.jar', 'external1-1.0.txt']
    }
}
"""
        inTestDirectory().withTasks('test').run()
    }

    @Test
    @Issue("GRADLE-739")
    public void configurationCanContainMultipleArtifactsThatOnlyDifferByType() {
        def module = repo.module('org.gradle.test', 'external1', '1.0')
        module.artifact(type: 'zip')
        module.artifact(classifier: 'classifier')
        module.artifact(classifier: 'classifier', type: 'bin')
        module.publish()

        testFile('build.gradle') << """
repositories {
    maven { url '${repo.uri}' }
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

task test {
    doLast {
        checkDeps configurations.base, ['external1-1.0.jar', 'external1-1.0.zip']
        checkDeps configurations.extended, ['external1-1.0.jar', 'external1-1.0.zip', 'external1-1.0-classifier.jar']
        checkDeps configurations.extended2, ['external1-1.0.jar', 'external1-1.0.zip', 'external1-1.0-classifier.bin']
    }
}
"""
        inTestDirectory().withTasks('test').run()
    }

    @Test
    public void nonTransitiveDependenciesAreNotRetrieved() {
        repo.module('org.gradle.test', 'one', '1.0').publish()
        repo.module('org.gradle.test', 'two', '1.0').publish()
        def module = repo.module('org.gradle.test', 'external1', '1.0')
        module.dependsOn('org.gradle.test', 'one', '1.0')
        module.artifact(classifier: 'classifier')
        module.publish()

        testFile('build.gradle') << """
repositories {
    maven { url '${repo.uri}' }
}
configurations {
    transitive
    nonTransitive
    extendedNonTransitive.extendsFrom nonTransitive
    extendedBoth.extendsFrom transitive, nonTransitive
    mergedNonTransitive
}
dependencies {
    transitive 'org.gradle.test:external1:1.0'
    nonTransitive 'org.gradle.test:external1:1.0', { transitive = false }
    extendedNonTransitive 'org.gradle.test:two:1.0'
    mergedNonTransitive 'org.gradle.test:external1:1.0', {transitive = false }
    mergedNonTransitive 'org.gradle.test:external1:1.0:classifier', { transitive = false }
}

def checkDeps(config, expectedDependencies) {
    assert config.collect({ it.name }) as Set == expectedDependencies as Set
}

task test {
    doLast {
        checkDeps configurations.transitive, ['external1-1.0.jar', 'one-1.0.jar']
        checkDeps configurations.nonTransitive, ['external1-1.0.jar']
        checkDeps configurations.extendedNonTransitive, ['external1-1.0.jar', 'two-1.0.jar']
        checkDeps configurations.extendedBoth, ['external1-1.0.jar', 'one-1.0.jar']
        checkDeps configurations.mergedNonTransitive, ['external1-1.0.jar', 'external1-1.0-classifier.jar']
    }
}
"""
        inTestDirectory().withTasks('test').run()
    }

    @Test
    public void "configuration transitive = false overrides dependency transitive flag"() {
        repo.module('org.gradle.test', 'one', '1.0').publish()
        def module = repo.module('org.gradle.test', 'external1', '1.0')
        module.dependsOn('org.gradle.test', 'one', '1.0')
        module.publish()

        testFile('build.gradle') << """
repositories {
    maven { url '${repo.uri}' }
}
configurations {
    override { transitive = false }
}
dependencies {
    override 'org.gradle.test:external1:1.0'
}

task test {
    doLast {
        assert configurations.override.collect { it.name } == ['external1-1.0.jar']
    }
}
"""

        inTestDirectory().withTasks('test').run()
    }

    /*
     * Originally, we were aliasing dependency descriptors that were identical. This caused alias errors when we subsequently modified one of these descriptors.
     */

    @Test
    public void addingClassifierToDuplicateDependencyDoesNotAffectOriginal() {
        def module = repo.module('org.gradle.test', 'external1', '1.0')
        module.artifact(classifier: 'withClassifier')
        module.publish()

        testFile('build.gradle') << """
repositories {
    maven { url '${repo.uri}' }
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

task test {
    doLast {
        checkDeps configurations.a, ['external1-1.0.jar']
        checkDeps configurations.b, ['external1-1.0-withClassifier.jar', 'external1-1.0.jar']
    }
}
"""
        inTestDirectory().withTasks('test').run()
    }

    @Test
    public void projectCanDependOnItself() {
        TestFile buildFile = testFile("build.gradle");
        buildFile << '''
            configurations { compile; create('default') }
            dependencies { compile project(':') }
            task jar1(type: Jar) { destinationDir = buildDir; baseName = '1' }
            task jar2(type: Jar) { destinationDir = buildDir; baseName = '2' }
            artifacts { compile jar1; 'default' jar2 }
            task listJars {
                doLast {
                    assert configurations.compile.collect { it.name } == ['2.jar']
                }
            }
'''

        inTestDirectory().withTasks("listJars").run()
    }

    def getRepo() {
        return maven(testFile('repo'))
    }
}

