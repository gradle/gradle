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

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.ExecutionFailure
import org.gradle.integtests.fixtures.TestResources
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
        result.assertThatCause(containsString('Could not find group:org.gradle.test, module:external1, version:1.0.'))
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
task listJar << { configurations.compile.each { } }
task listMissingExt << { configurations.missingExt.each { } }
task listMissingClassifier << { configurations.missingClassifier.each { } }
"""
        repo.module('org.gradle.test', 'lib', '1.0').publish()

        inTestDirectory().withTasks('listJar').run()

        def result = inTestDirectory().withTasks('listMissingExt').runWithFailure()
        result.assertThatCause(containsString("Artifact 'org.gradle.test:lib:1.0@zip' not found"))

        result = inTestDirectory().withTasks('listMissingClassifier').runWithFailure()
        result.assertThatCause(containsString("Artifact 'org.gradle.test:lib:1.0:classifier1@jar' not found"))
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
    task listDeps << { configurations.compile.each { } }
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
        result.assertThatCause(containsString('Could not find group:org.gradle.test, module:external1, version:1.0.'))
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
task test << {
    assert configurations.compile.files.collect { it.name } == ['lib-1.0.jar', 'lib-1.0.zip', 'lib-1.0-classifier.jar', 'dist-1.0.zip']
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
"""

        executer.withDeprecationChecksDisabled()
        def result = inTestDirectory().withTasks('test').run()
        assert result.output.contains('Relying on packaging to define the extension of the main artifact has been deprecated')
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
    task test(dependsOn: configurations.compile) << {
        assert configurations.compile.collect { it.name } == ['external1-1.0-classifier1.jar']
        assert configurations.compile.resolvedConfiguration.resolvedArtifacts.collect { "\${it.name}-\${it.classifier}" } == ['external1-classifier1']
    }
}
project(':b') {
    dependencies {
        compile 'org.gradle.test:external1:1.0:classifier2'
    }
    task test(dependsOn: configurations.compile) << {
        assert configurations.compile.collect { it.name } == ['external1-1.0-classifier2.jar']
        assert configurations.compile.resolvedConfiguration.resolvedArtifacts.collect { "\${it.name}-\${it.classifier}" } == ['external1-classifier2']
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

task test << {
    checkDeps configurations.base, ['external1-1.0.zip', 'external1-1.0-baseClassifier.jar']
    checkDeps configurations.extended, ['external1-1.0.zip', 'external1-1.0-baseClassifier.jar', 'other-1.0.jar']
    checkDeps configurations.extendedWithClassifier, ['external1-1.0.zip', 'external1-1.0-baseClassifier.jar', 'external1-1.0-extendedClassifier.jar']
    checkDeps configurations.extendedWithType, ['external1-1.0.zip', 'external1-1.0-baseClassifier.jar', 'external1-1.0.txt']
}
"""
        executer.withDeprecationChecksDisabled()
        def result = inTestDirectory().withTasks('test').run()
        assert result.output.contains('Relying on packaging to define the extension of the main artifact has been deprecated')
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

task test << {
    checkDeps configurations.base, ['external1-1.0.jar', 'external1-1.0.zip']
    checkDeps configurations.extended, ['external1-1.0.jar', 'external1-1.0.zip', 'external1-1.0-classifier.jar']
    checkDeps configurations.extended2, ['external1-1.0.jar', 'external1-1.0.zip', 'external1-1.0-classifier.bin']
}
"""
        inTestDirectory().withTasks('test').run()
    }

    @Test
    public void "dependencies that are excluded by a dependency are not retrieved"() {
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
    reference
    excluded
    extendedExcluded.extendsFrom excluded
    excludedWithClassifier
}
dependencies {
    reference 'org.gradle.test:external1:1.0'
    excluded 'org.gradle.test:external1:1.0', { exclude module: 'one' }
    extendedExcluded 'org.gradle.test:two:1.0'
    excludedWithClassifier 'org.gradle.test:external1:1.0', { exclude module: 'one' }
    excludedWithClassifier 'org.gradle.test:external1:1.0:classifier', { exclude module: 'one' }
}

def checkDeps(config, expectedDependencies) {
    assert config*.name as Set == expectedDependencies as Set
}

task test << {
    checkDeps configurations.reference, ['external1-1.0.jar', 'one-1.0.jar']
    checkDeps configurations.excluded, ['external1-1.0.jar']
    checkDeps configurations.extendedExcluded, ['external1-1.0.jar', 'two-1.0.jar']
    checkDeps configurations.excludedWithClassifier, ['external1-1.0.jar', 'external1-1.0-classifier.jar']
}
"""
        inTestDirectory().withTasks('test').run()
    }

    @Test
    public void "dependencies that are globally excluded are not retrieved"() {
        repo.module('org.gradle.test', 'direct', '1.0').publish()
        repo.module('org.gradle.test', 'transitive', '1.0').publish()
        def module = repo.module('org.gradle.test', 'external', '1.0')
        module.dependsOn('org.gradle.test', 'transitive', '1.0')
        module.publish()

        testFile('build.gradle') << """
repositories {
    maven { url '${repo.uri}' }
}
configurations {
    excluded {
        exclude module: 'direct'
        exclude module: 'transitive'
    }
    extendedExcluded.extendsFrom excluded
}
dependencies {
    excluded 'org.gradle.test:external:1.0'
    excluded 'org.gradle.test:direct:1.0'
}

def checkDeps(config, expectedDependencies) {
    assert config*.name as Set == expectedDependencies as Set
}

task test << {
    checkDeps configurations.excluded, ['external-1.0.jar']
    checkDeps configurations.extendedExcluded, ['external-1.0.jar']
}
"""
        inTestDirectory().withTasks('test').run()
    }

    @Test
    public void "does not attempt to resolve an excluded dependency"() {
        def module = repo.module('org.gradle.test', 'external', '1.0')
        module.dependsOn('org.gradle.test', 'unknown1', '1.0')
        module.dependsOn('org.gradle.test', 'unknown2', '1.0')
        module.publish()

        testFile('build.gradle') << """
repositories {
    maven { url '${repo.uri}' }
}
configurations {
    excluded {
        exclude module: 'unknown2'
    }
}
dependencies {
    excluded 'org.gradle.test:external:1.0', { exclude module: 'unknown1' }
    excluded 'org.gradle.test:unknown2:1.0'
}

def checkDeps(config, expectedDependencies) {
    assert config*.name as Set == expectedDependencies as Set
}

task test << {
    checkDeps configurations.excluded, ['external-1.0.jar']
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

task test << {
    checkDeps configurations.transitive, ['external1-1.0.jar', 'one-1.0.jar']
    checkDeps configurations.nonTransitive, ['external1-1.0.jar']
    checkDeps configurations.extendedNonTransitive, ['external1-1.0.jar', 'two-1.0.jar']
    checkDeps configurations.extendedBoth, ['external1-1.0.jar', 'one-1.0.jar']
    checkDeps configurations.mergedNonTransitive, ['external1-1.0.jar', 'external1-1.0-classifier.jar']
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

task test << {
    assert configurations.override.collect { it.name } == ['external1-1.0.jar']
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
        failure.assertThatCause(containsString("Could not find group:test, module:unknownProjectA, version:1.2."));
        failure.assertThatCause(containsString("Could not find group:test, module:unknownProjectB, version:2.1.5."));
    }

    @Test
    public void projectCanDependOnItself() {
        TestFile buildFile = testFile("build.gradle");
        buildFile << '''
            configurations { compile; add('default') }
            dependencies { compile project(':') }
            task jar1(type: Jar) { destinationDir = buildDir; baseName = '1' }
            task jar2(type: Jar) { destinationDir = buildDir; baseName = '2' }
            artifacts { compile jar1; 'default' jar2 }
            task listJars << {
                assert configurations.compile.collect { it.name } == ['2.jar']
            }
'''

        inTestDirectory().withTasks("listJars").run()
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

    def getRepo() {
        return maven(testFile('repo'))
    }
}

