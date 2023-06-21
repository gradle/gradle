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

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.extensions.FluidDependenciesResolveTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.junit.Rule
import spock.lang.Issue

import static org.hamcrest.CoreMatchers.containsString

/**
 * This test contains some of the original coverage for dependency resolution.
 * These tests should be migrated to live with the rest of the coverage over time.
 */
@FluidDependenciesResolveTest
class ArtifactDependenciesIntegrationTest extends AbstractDependencyResolutionTest {
    public final resolve = new ResolveTestFixture(buildFile)
    @Rule
    public final TestResources testResources = new TestResources(testDirectoryProvider)

    void canHaveConfigurationHierarchy() {
        given:
        resolve.prepare {
            config("compile")
            config("runtime")
        }

        when:
        run("checkCompile")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("test:projectA:1.2") {
                    configuration("api")
                    module("test:projectB:1.5") {
                        artifact(name: "projectB-api")
                    }
                }
            }
        }

        when:
        run("checkRuntime")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("test:projectA:1.2") {
                    configuration("api")
                    configuration("default")
                    module("test:projectB:1.5") {
                        configuration("extraRuntime")
                        artifact()
                        artifact(name: "projectB-api")
                        artifact(name: "projectB-extraRuntime")
                    }
                    module("test:projectB:1.5")
                }
                module("test:projectA:1.2")
                module("test:projectB:1.5")
            }
        }
    }

    void dependencyReportWithConflicts() {
        given:
        resolve.prepare {
            config("evictedTransitive")
            config("evictedDirect")
            config("multiProject")
        }

        when:
        run(":checkEvictedTransitive")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("test:projectA:1.2") {
                    edge("test:projectB:1.5", "test:projectB:2.1.5")
                }
                module("test:projectB:2.1.5") {
                    byConflictResolution("between versions 2.1.5 and 1.5")
                }
            }
        }

        when:
        run(":checkEvictedDirect")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("test:projectA:2.0") {
                    module("test:projectB:2.1.5") {
                        byConflictResolution("between versions 2.1.5 and 1.5")
                    }
                }
                edge("test:projectB:1.5", "test:projectB:2.1.5")
            }
        }

        when:
        run(":checkMultiProject")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("test:projectA:1.2", "test:projectA:2.0") {
                    byConflictResolution("between versions 2.0 and 1.2")
                    module("test:projectB:2.1.5")
                }
                project(":subproject", "test:subproject:") {
                    noArtifacts()
                    module("test:projectA:2.0")
                }
            }
        }
    }

    void canHaveCycleInDependencyGraph() {
        given:
        resolve.prepare("compile")

        when:
        run(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("test:projectA:1.2") {
                    module("test:projectB:1.5") {
                        module("test:projectA:1.2")
                    }
                }
            }
        }
    }

    void canUseDynamicVersions() {
        given:
        resolve.prepare("compile")

        when:
        run(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("test:projectA:1.+", "test:projectA:1.2") {
                    edge("test:projectB:latest.release", "test:projectB:1.5")
                }
            }
        }
    }

    void resolutionFailsWhenProjectHasNoRepositoriesEvenWhenArtifactIsCachedLocally() {
        expect:
        file('settings.gradle') << 'include "a", "b"'
        file('build.gradle') << """
subprojects {
    configurations {
        compile
    }
    task listDeps {
        def files = configurations.compile
        doLast { files.files }
    }
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

        succeeds('a:listDeps')
        fails('b:listDeps')
        failure.assertHasCause('Cannot resolve external dependency org.gradle.test:external1:1.0 because no repositories are defined.')
    }

    void resolutionFailsForMissingArtifact() {
        given:
        file('build.gradle') << """
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
task listJar {
    def files = configurations.compile
    doLast { files.each { } }
}
task listMissingExt {
    def files = configurations.missingExt
    doLast { files.each { } }
}
task listMissingClassifier {
    def files = configurations.missingClassifier
    doLast { files.each { } }
}
"""

        def module = repo.module('org.gradle.test', 'lib', '1.0')
        module.publish()

        succeeds('listJar')

        fails('listMissingExt')

        failure.assertHasCause("""Could not find lib-1.0.zip (org.gradle.test:lib:1.0).
Searched in the following locations:
    ${module.artifactFile(type: 'zip').toURL()}""")

        when:
        fails('listMissingClassifier')

        then:
        failure.assertHasCause("""Could not find lib-1.0-classifier1.jar (org.gradle.test:lib:1.0).
Searched in the following locations:
    ${module.artifactFile(classifier: 'classifier1').toURL()}""")
    }

    @Issue("GRADLE-1342")
    void resolutionDoesNotUseCachedArtifactFromDifferentRepository() {
        expect:
        def repo1 = maven('repo1')
        repo1.module('org.gradle.test', 'external1', '1.0').publish()
        def repo2 = maven('repo2')

        file('settings.gradle') << 'include "a", "b"'
        file('build.gradle') << """
subprojects {
    configurations {
        compile
    }
    task listDeps {
        def files = configurations.compile
        doLast { files.each { } }
    }
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

        succeeds('a:listDeps')
        fails('b:listDeps')
        failure.assertThatCause(containsString('Could not find org.gradle.test:external1:1.0.'))
    }

    void artifactFilesPreserveFixedOrder() {
        expect:
        repo.module('org', 'leaf1').publish()
        repo.module('org', 'leaf2').publish()
        repo.module('org', 'leaf3').publish()
        repo.module('org', 'leaf4').publish()

        repo.module('org', 'middle1').dependsOnModules("leaf1", "leaf2").publish()
        repo.module('org', 'middle2').dependsOnModules("leaf3", "leaf4").publish()

        repo.module('org', 'top').dependsOnModules("middle1", "middle2").publish()

        file('build.gradle') << """
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
                def files = configurations.compile
                doLast {
                    assert files.collect { it.name } == ['middle2-1.0.jar', 'middle1-1.0.jar', 'leaf3-1.0.jar', 'leaf4-1.0.jar', 'leaf1-1.0.jar', 'leaf2-1.0.jar']
                }
            }
        """

        succeeds("test")
    }

    void exposesMetaDataAboutResolvedArtifactsInAFixedOrder() {
        expect:
        def module = repo.module('org.gradle.test', 'lib', '1.0')
        module.artifact(type: 'zip')
        module.artifact(classifier: 'classifier')
        module.publish()
        repo.module('org.gradle.test', 'dist', '1.0').hasType('zip').publish()

        file('build.gradle') << """
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

abstract class CheckArtifacts extends DefaultTask {
    @Internal
    FileCollection files

    @Internal
    ArtifactCollection artifacts

    @TaskAction
    void test() {
        assert this.files.collect { it.name } == ['lib-1.0.jar', 'lib-1.0-classifier.jar', 'lib-1.0.zip', 'dist-1.0.zip']
        def artifacts = this.artifacts.artifacts.collect { it.id.name }

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

tasks.register("test", CheckArtifacts) {
    files = configurations.compile.incoming.files
    artifacts = configurations.compile.incoming.artifacts
}
"""

        succeeds('test')
    }

    @Issue("GRADLE-1567")
    void resolutionDifferentiatesBetweenArtifactsThatDifferOnlyInClassifier() {
        expect:
        def lib = repo.module('org.gradle.test', 'external1', '1.0')
        lib.artifact(classifier: 'classifier1')
        lib.artifact(classifier: 'classifier2')
        lib.publish()

        file('settings.gradle') << """
            rootProject.name = "test"
            include "a", "b", "c"
        """
        file('build.gradle') << """
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
        resolve.prepare("compile")

        when:
        succeeds("a:checkDeps")

        then:
        resolve.expectGraph {
            root(":a", "test:a:") {
                module("org.gradle.test:external1:1.0") {
                    artifact(classifier: "classifier1")
                }
            }
        }

        when:
        succeeds("b:checkDeps")

        then:
        resolve.expectGraph {
            root(":b", "test:b:") {
                module("org.gradle.test:external1:1.0") {
                    artifact(classifier: "classifier2")
                }
            }
        }
    }

    @Issue("GRADLE-739")
    void singleConfigurationCanContainMultipleArtifactsThatOnlyDifferByClassifier() {
        expect:
        def module = repo.module('org.gradle.test', 'external1', '1.0')
        module.artifact(classifier: 'baseClassifier')
        module.artifact(classifier: 'extendedClassifier')
        module.publish()
        repo.module('org.gradle.test', 'other', '1.0').publish()

        file('build.gradle') << """
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

task test {
    def base = configurations.base
    def extendedWithOther = configurations.extendedWithOther
    def extendedWithClassifier = configurations.extendedWithClassifier
    def justDefault = configurations.justDefault
    def justClassifier = configurations.justClassifier
    def rawBase = configurations.rawBase
    def rawExtended = configurations.rawExtended
    doLast {
        assert base*.name == ['external1-1.0.jar', 'external1-1.0-baseClassifier.jar']
        assert extendedWithOther*.name == ['external1-1.0.jar', 'external1-1.0-baseClassifier.jar', 'other-1.0.jar']
        assert extendedWithClassifier*.name == ['external1-1.0.jar', 'external1-1.0-baseClassifier.jar', 'external1-1.0-extendedClassifier.jar']
        assert justDefault*.name == ['external1-1.0.jar']
        assert justClassifier*.name == ['external1-1.0-baseClassifier.jar', 'external1-1.0-extendedClassifier.jar']
        assert rawBase*.name == ['external1-1.0.jar']
        assert rawExtended*.name == ['external1-1.0.jar', 'external1-1.0-extendedClassifier.jar']
    }
}
"""
        succeeds('test')
    }

    @Issue("GRADLE-739")
    void canUseClassifiersCombinedWithArtifactWithNonStandardPackaging() {
        expect:
        def module = repo.module('org.gradle.test', 'external1', '1.0')
        module.artifact(type: 'txt')
        module.artifact(classifier: 'baseClassifier', type: 'jar')
        module.artifact(classifier: 'extendedClassifier', type: 'jar')
        module.hasType('zip')
        module.publish()
        repo.module('org.gradle.test', 'other', '1.0').publish()

        file('build.gradle') << """
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

task test {
    def base = configurations.base
    def extended = configurations.extended
    def extendedWithClassifier = configurations.extendedWithClassifier
    def extendedWithType = configurations.extendedWithType
    doLast {
        assert base*.name == ['external1-1.0.zip', 'external1-1.0-baseClassifier.jar']
        assert extended*.name == ['external1-1.0.zip', 'external1-1.0-baseClassifier.jar', 'other-1.0.jar']
        assert extendedWithClassifier*.name == ['external1-1.0.zip', 'external1-1.0-baseClassifier.jar', 'external1-1.0-extendedClassifier.jar']
        assert extendedWithType*.name == ['external1-1.0.zip', 'external1-1.0-baseClassifier.jar', 'external1-1.0.txt']
    }
}
"""
        succeeds('test')
    }

    @Issue("GRADLE-739")
    void configurationCanContainMultipleArtifactsThatOnlyDifferByType() {
        expect:
        def module = repo.module('org.gradle.test', 'external1', '1.0')
        module.artifact(type: 'zip')
        module.artifact(classifier: 'classifier')
        module.artifact(classifier: 'classifier', type: 'bin')
        module.publish()

        file('build.gradle') << """
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

task test {
    def base = configurations.base
    def extended = configurations.extended
    def extended2 = configurations.extended2
    doLast {
        assert base*.name == ['external1-1.0.jar', 'external1-1.0.zip']
        assert extended*.name == ['external1-1.0.jar', 'external1-1.0.zip', 'external1-1.0-classifier.jar']
        assert extended2*.name == ['external1-1.0.jar', 'external1-1.0.zip', 'external1-1.0-classifier.bin']
    }
}
"""
        succeeds('test')
    }

    void nonTransitiveDependenciesAreNotRetrieved() {
        expect:
        repo.module('org.gradle.test', 'one', '1.0').publish()
        repo.module('org.gradle.test', 'two', '1.0').publish()
        def lib = repo.module('org.gradle.test', 'external1', '1.0')
        lib.dependsOn('org.gradle.test', 'one', '1.0')
        lib.artifact(classifier: 'classifier')
        lib.publish()

        file('settings.gradle') << "rootProject.name = 'test'"
        file('build.gradle') << """
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
"""
        resolve.prepare {
            config("transitive")
            config("nonTransitive")
            config("extendedNonTransitive")
            config("extendedBoth")
            config("mergedNonTransitive")
        }

        when:
        succeeds("checkTransitive")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.gradle.test:external1:1.0") {
                    module("org.gradle.test:one:1.0")
                }
            }
        }

        when:
        succeeds("checkNonTransitive")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.gradle.test:external1:1.0")
            }
        }

        when:
        succeeds("checkExtendedNonTransitive")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.gradle.test:external1:1.0")
                module("org.gradle.test:two:1.0")
            }
        }

        when:
        succeeds("checkExtendedBoth")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.gradle.test:external1:1.0") {
                    module("org.gradle.test:one:1.0")
                }
            }
        }

        when:
        succeeds("checkMergedNonTransitive")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.gradle.test:external1:1.0") {
                    artifact()
                    artifact(classifier: "classifier")
                }
            }
        }
    }

    void "configuration transitive = false overrides dependency transitive flag"() {
        expect:
        repo.module('org.gradle.test', 'one', '1.0').publish()
        def module = repo.module('org.gradle.test', 'external1', '1.0')
        module.dependsOn('org.gradle.test', 'one', '1.0')
        module.publish()

        file('build.gradle') << """
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
    def files = configurations.override
    doLast {
        assert files.collect { it.name } == ['external1-1.0.jar']
    }
}
"""

        succeeds('test')
    }

    /*
     * Originally, we were aliasing dependency descriptors that were identical. This caused alias errors when we subsequently modified one of these descriptors.
     */

    void addingClassifierToDuplicateDependencyDoesNotAffectOriginal() {
        expect:
        def module = repo.module('org.gradle.test', 'external1', '1.0')
        module.artifact(classifier: 'withClassifier')
        module.publish()

        file('build.gradle') << """
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
    def a = configurations.a
    def b = configurations.b
    doLast {
        assert a*.name == ['external1-1.0.jar']
        assert b*.name == ['external1-1.0.jar', 'external1-1.0-withClassifier.jar']
    }
}
"""
        succeeds('test')
    }

    void projectCanDependOnItself() {
        given:
        file("settings.gradle") << "rootProject.name = 'test'"
        file("build.gradle") << '''
            group = 'org.test'
            version = '1.2'
            configurations { compile; create('default') }
            dependencies { compile project(':') }
            task jar1(type: Jar) { destinationDirectory = buildDir; archiveBaseName = '1' }
            task jar2(type: Jar) { destinationDirectory = buildDir; archiveBaseName = '2' }
            artifacts { compile jar1; 'default' jar2 }
'''
        resolve.prepare("compile")

        when:
        succeeds("checkDeps")

        then:
        resolve.expectGraph {
            root(":", "org.test:test:1.2") {
                project(":", "org.test:test:1.2")
                artifact(name: '2', fileName: '2.jar')
            }
        }
    }

    def getRepo() {
        return maven(file('repo'))
    }
}
