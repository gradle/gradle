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

package org.gradle.plugins.ide.idea

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.plugins.ide.AbstractIdeIntegrationTest
import org.junit.Rule
import org.junit.Test
import spock.lang.Issue

class IdeaModuleIntegrationTest extends AbstractIdeIntegrationTest {
    @Rule
    public final TestResources testResources = new TestResources(testDirectoryProvider)

    @Test
    @ToBeFixedForConfigurationCache
    void enablesCustomizationsOnNewModel() {
        //given
        testResources.dir.create {
            additionalCustomSources {}
            additionalCustomResources {}
            additionalCustomGeneratedResources {}
            muchBetterOutputDir {}
            muchBetterTestOutputDir {}
            customImlFolder {}
            excludeMePlease {}
            customModuleContentRoot {}
            src { main { java {} } }
        }

        //when
        runTask 'idea', '''
apply plugin: "java"
apply plugin: "idea"

configurations {
  provided
  compileClasspath.extendsFrom(provided)
}

idea {
    pathVariables CUSTOM_VARIABLE: file('customModuleContentRoot').parentFile

    module {
        name = 'foo'
        contentRoot = file('customModuleContentRoot')

        sourceDirs += file('additionalCustomSources')
        resourceDirs += file('additionalCustomResources')
        resourceDirs += file('additionalCustomGeneratedResources')
        generatedSourceDirs += file('additionalCustomGeneratedResources')
        excludeDirs += file('excludeMePlease')

        scopes.PROVIDED.plus += [ configurations.compileClasspath ]
        downloadJavadoc = true
        downloadSources = false

        inheritOutputDirs = false
        outputDir = file('muchBetterOutputDir')
        testOutputDir = file('muchBetterTestOutputDir')

        jdkName = '1.6'

        iml {
            generateTo = file('customImlFolder')

            withXml {
                def node = it.asNode()
                node.appendNode('someInterestingConfiguration', 'hey!')
            }
        }
    }
}
'''

        //then
        def iml = parseImlFile('customImlFolder/foo')
        ['additionalCustomSources',
         'additionalCustomResources',
         'additionalCustomGeneratedResources',
         'src/main/java'].each { expectedSrcFolder ->
            assert iml.component.content.sourceFolder.find { it.@url.text().contains(expectedSrcFolder) }
        }
        ['additionalCustomResources', 'additionalCustomGeneratedResources'].each { expectedSrcFolder ->
            assert iml.component.content.sourceFolder.find { it.@url.text().contains(expectedSrcFolder) && it.@type.text() == 'java-resource'}
        }
        assert iml.component.content.sourceFolder.find { it.@url.text().contains('additionalCustomGeneratedResources') && it.@generated.text() == 'true'}

        ['customModuleContentRoot', 'CUSTOM_VARIABLE'].each {
            assert iml.component.content.@url.text().contains(it)
        }
        ['.gradle', 'build', 'excludeMePlease'].each { expectedExclusion ->
            assert iml.component.content.excludeFolder.find { it.@url.text().endsWith(expectedExclusion) }
        }
        assert iml.component.output.@url.text().endsWith('muchBetterOutputDir')
        assert iml.component."output-test".@url.text().endsWith('muchBetterTestOutputDir')
        assert iml.component.orderEntry.any { it.@type.text() == 'jdk' && it.@jdkName.text() == '1.6' }
        assert iml.someInterestingConfiguration.text() == 'hey!'
    }

    @Test
    @Issue("https://github.com/gradle/gradle/issues/6547")
    @ToBeFixedForConfigurationCache
    void "omit resource declaration if directory is also a source directory"() {
        //given
        testResources.dir.create {
            src {
                main {
                    java {}
                }

                test {
                    java {}
                }
            }
        }

        //when
        runIdeaTask '''
apply plugin: 'java'
apply plugin: 'idea'

sourceSets {
   main {
      resources {
         srcDir 'src/main/java'
      }
   }
   test {
      resources {
         srcDir 'src/test/java'
      }
   }
}
'''

        //then
        def iml = parseImlFile('root')
        def source = iml.component.content.sourceFolder.find { it.@url.text().contains('src/main/java') }
        def testSource = iml.component.content.sourceFolder.find { it.@url.text().contains('src/test/java') }

        assert source
        assert testSource
        assert source.@type.text() != 'java-resource'
        assert testSource.@type.text() != 'java-test-resource'
    }

    @Test
    @ToBeFixedForConfigurationCache
    void plusMinusConfigurationsWorkFineForSelfResolvingFileDependencies() {
        //when
        runTask 'idea', '''
apply plugin: "java"
apply plugin: "idea"

configurations {
  bar
  foo
  foo.extendsFrom(bar)
  baz
}

dependencies {
  bar files('bar.jar')
  foo files('foo.jar', 'foo2.jar', 'foo3.jar')
  baz files('foo3.jar')
}

idea {
    module {
        scopes.COMPILE.plus << configurations.foo
        scopes.COMPILE.minus += [configurations.bar, configurations.baz]
    }
}
'''
        def content = getFile([:], 'root.iml').text

        //then
        assert content.contains('foo.jar')
        assert content.contains('foo2.jar')

        assert !content.contains('bar.jar')
        assert !content.contains('foo3.jar')
    }

    @Issue("GRADLE-3101")
    @Test
    @ToBeFixedForConfigurationCache
    void scopesCustomizedUsingPlusEqualOperator() {
        //when
        runTask 'idea', '''
apply plugin: "java"
apply plugin: "idea"

configurations {
  bar
}

idea {
    module {
        scopes.COMPILE.plus += [ configurations.bar ]
    }
}

dependencies {
  bar files('bar.jar')
}
'''
        def content = getFile([:], 'root.iml').text

        //then
        assert content.contains('bar.jar')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void allowsReconfiguringBeforeOrAfterMerging() {
        //given
        def existingIml = file('root.iml')
        existingIml << '''<?xml version="1.0" encoding="UTF-8"?>
<module relativePaths="true" type="JAVA_MODULE" version="4">
  <component name="NewModuleRootManager" inherit-compiler-output="true">
    <exclude-output/>
    <orderEntry type="inheritedJdk"/>
    <content url="file://$MODULE_DIR$/">
      <excludeFolder url="file://$MODULE_DIR$/folderThatWasExcludedEarlier"/>
    </content>
    <orderEntry type="sourceFolder" forTests="false"/>
  </component>
  <component name="ModuleRootManager"/>
</module>'''

        //when
        runTask(['idea'], '''
apply plugin: "java"
apply plugin: "idea"

idea {
    module {
        excludeDirs = [project.file('folderThatIsExcludedNow')] as Set
        iml {
            beforeMerged { it.excludeFolders.clear() }
            whenMerged   { it.jdkName = '1.33'   }
        }
    }
}
''')
        //then
        def iml = getFile([:], 'root.iml').text
        assert iml.contains('folderThatIsExcludedNow')
        assert !iml.contains('folderThatWasExcludedEarlier')
        assert iml.contains('1.33')
    }

    @Issue("GRADLE-1504")
    @Test
    @ToBeFixedForConfigurationCache
    void shouldNotPutSourceSetsOutputDirOnClasspath() {
        //when
        runTask 'idea', '''
apply plugin: "java"
apply plugin: "idea"

task generate {
    doLast {
        file('build/generated/main/foo.resource').with {
            parentFile.mkdirs()
            text = "resource"
        }
        file('build/ws/test/service.xml').with {
            parentFile.mkdirs()
            text = "<xml/>"
        }
    }
}

tasks.idea.dependsOn generate

sourceSets.main.output.dir "$buildDir/generated/main"
sourceSets.test.output.dir "$buildDir/ws/test"
'''
        //then
        def dependencies = parseIml("root.iml").dependencies
        assert dependencies.libraries.size() == 2
        dependencies.assertHasLibrary('RUNTIME', 'generated/main')
        dependencies.assertHasLibrary('TEST', 'ws/test')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void theBuiltByTaskBeExecuted() {
        //when
        def result = runIdeaTask('''
apply plugin: "java"
apply plugin: "idea"

sourceSets.main.output.dir "$buildDir/generated/main", builtBy: 'generateForMain'
sourceSets.test.output.dir "$buildDir/generated/test", builtBy: 'generateForTest'

task generateForMain
task generateForTest
''')
        //then
        result.assertTasksExecuted(':generateForMain', ':generateForTest', ':ideaModule', ':ideaProject', ':ideaWorkspace', ':idea')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void enablesTogglingJavadocAndSourcesOff() {
        //given
        def repoDir = file("repo")
        def module = maven(repoDir).module("coolGroup", "niceArtifact")
        module.artifact(classifier: 'sources')
        module.artifact(classifier: 'javadoc')
        module.publish()

        //when
        runIdeaTask """
apply plugin: 'java'
apply plugin: 'idea'

repositories {
    maven { url "${repoDir.toURI()}" }
}

dependencies {
    implementation 'coolGroup:niceArtifact:1.0'
}

idea.module {
    downloadSources = false
    downloadJavadoc = false
}
"""
        def content = getFile([:], 'root.iml').text

        //then
        assert !content.contains('niceArtifact-1.0-sources.jar')
        assert !content.contains('niceArtifact-1.0-javadoc.jar')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void "respects external dependencies order"() {
        //given
        def repoDir = file("repo")
        maven(repoDir).module("org.gradle", "artifact1").publish()
        maven(repoDir).module("org.gradle", "artifact2").publish()

        //when
        runIdeaTask """
apply plugin: 'java'
apply plugin: 'idea'

repositories {
    maven { url "${repoDir.toURI()}" }
}

dependencies {
    implementation 'org.gradle:artifact1:1.0'
    implementation 'org.gradle:artifact2:1.0'
}
"""
        def content = getFile([:], 'root.iml').text

        //then
        def a1 = content.indexOf("artifact1")
        def a2 = content.indexOf("artifact2")

        assert [a1, a2] == [a1, a2].sort()
    }

    @Test
    @ToBeFixedForConfigurationCache
    void "respects local dependencies order"() {
        //given
        file('artifact1.jar').createNewFile()
        file('artifact2.jar').createNewFile()

        //when
        runIdeaTask """
apply plugin: 'java'
apply plugin: 'idea'

dependencies {
    implementation files('artifact1.jar')
    implementation files('artifact2.jar')
}
"""
        def content = getFile([:], 'root.iml').text

        //then
        def a1 = content.indexOf("artifact1")
        def a2 = content.indexOf("artifact2")

        assert [a1, a2] == [a1, a2].sort()
    }

    @Test
    @ToBeFixedForConfigurationCache
    void "works with artifacts without group and version"() {
        //given
        testFile('repo/hibernate-core.jar').createFile()

        //when
        runIdeaTask """
apply plugin: 'java'
apply plugin: 'idea'

repositories {
  flatDir dirs: 'repo'
}

dependencies {
    implementation ':hibernate-core:'
}
"""
        def content = getFile([:], 'root.iml').text

        //then
        content.contains 'hibernate-core.jar'
    }

    @Test
    @ToBeFixedForConfigurationCache
    void doesNotBreakWhenSomeDependenciesCannotBeResolved() {
        //given
        def repoDir = file("repo")
        maven(repoDir).module("groupOne", "artifactTwo").publish()

        createDirs("someApiProject", "impl")
        file("settings.gradle") << "include 'someApiProject', 'impl'\n"
        file('someDependency.jar').createFile()

        //when
        runIdeaTask """
subprojects {
    apply plugin: 'java'
    apply plugin: 'idea'
}

project(':impl') {
    repositories {
        maven { url "${repoDir.toURI()}" }
    }

    dependencies {
        implementation 'groupOne:artifactTwo:1.0'
        implementation project(':someApiProject')
        implementation 'i.dont:Exist:1.0'
        implementation files('someDependency.jar')
    }
}
"""
        def content = getFile([print : true], 'impl/impl.iml').text

        //then
        assert content.count("someDependency.jar") == 1
        assert content.count("artifactTwo-1.0.jar") == 1
        assert content.count("someApiProject") == 1
        assert content.count("unresolved dependency - i.dont Exist 1.0") == 1
    }

    @Issue("GRADLE-2017")
    @Test
    @ToBeFixedForConfigurationCache
    void "create external dependency in more scopes when needed"() {
        //given
        def repoDir = file("repo")
        maven(repoDir).module("org.gradle", "api-artifact").publish()
        maven(repoDir).module("org.gradle", "impl-artifact").publish()

        //when
        runIdeaTask """
apply plugin: 'java'
apply plugin: 'idea'

repositories {
    maven { url "${repoDir.toURI()}" }
}

dependencies {
    implementation 'org.gradle:api-artifact:1.0'
    testImplementation 'org.gradle:impl-artifact:1.0'
    runtimeOnly 'org.gradle:impl-artifact:1.0'
}
"""
        //then
        def dependencies = parseIml("root.iml").dependencies
        assert dependencies.libraries.size() == 3
        dependencies.assertHasLibrary('COMPILE', 'api-artifact-1.0.jar')
        dependencies.assertHasLibrary('RUNTIME', 'impl-artifact-1.0.jar')
        dependencies.assertHasLibrary('TEST', 'impl-artifact-1.0.jar')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void "custom configuration is added to all specified scopes considering IDEA scope inclusion"() {
        //given
        def repoDir = file("repo")
        maven(repoDir).module("org.gradle", "api-artifact").publish()
        maven(repoDir).module("foo", "bar").publish()

        //when
        runIdeaTask """
apply plugin: 'java'
apply plugin: 'idea'

repositories {
    maven { url "${repoDir.toURI()}" }
}

configurations {
  myCustom
}

dependencies {
    myCustom 'foo:bar:1.0'
}

idea {
  module {
    scopes.PROVIDED.plus << configurations.myCustom
    scopes.COMPILE.plus << configurations.myCustom
  }
}
"""
        //then
        def dependencies = parseIml("root.iml").dependencies
        assert dependencies.libraries.size() == 1
        dependencies.assertHasLibrary('COMPILE', 'bar-1.0.jar')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void "custom configuration can be added to TEST and RUNTIME"() {
        //given
        def repoDir = file("repo")
        maven(repoDir).module("org.gradle", "api-artifact").publish()
        maven(repoDir).module("foo", "bar").publish()

        //when
        runIdeaTask """
apply plugin: 'java'
apply plugin: 'idea'

repositories {
    maven { url "${repoDir.toURI()}" }
}

configurations {
  myCustom
}

dependencies {
    myCustom 'foo:bar:1.0'
    implementation 'org.gradle:api-artifact:1.0'
}

idea {
  module {
    scopes.TEST.plus += [configurations.myCustom]
    scopes.RUNTIME.plus += [configurations.myCustom]
  }
}
"""
        //then
        def dependencies = parseIml("root.iml").dependencies
        assert dependencies.libraries.size() == 3
        dependencies.assertHasLibrary('COMPILE', 'api-artifact-1.0.jar')
        dependencies.assertHasLibrary(['RUNTIME','TEST'], 'bar-1.0.jar')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void "no libraries generated without java plugin"() {
        //given
        def repoDir = file("repo")
        maven(repoDir).module("org.gradle", "api-artifact").publish()
        maven(repoDir).module("foo", "bar").publish()

        //when
        runIdeaTask """
apply plugin: 'idea'

repositories {
    maven { url "${repoDir.toURI()}" }
}

configurations {
  compile
}

dependencies {
    compile 'org.gradle:api-artifact:1.0'
}
"""
        //then
        def dependencies = parseIml("root.iml").dependencies
        assert dependencies.libraries.isEmpty()
    }

    @Test
    @Issue("GRADLE-1945")
    @ToBeFixedForConfigurationCache
    void unresolvedDependenciesAreLogged() {
        //given
        def module = mavenRepo.module('myGroup', 'existing-artifact', '1.0')
        module.publish()

        //when
        ExecutionResult result = runIdeaTask """
apply plugin: 'java'
apply plugin: 'idea'

repositories {
    maven { url "${mavenRepo.uri}" }
}

configurations {
    myPlusConfig
    myMinusConfig
}

dependencies {
    myPlusConfig group: 'myGroup', name: 'missing-extra-artifact', version: '1.0'
    myPlusConfig group: 'myGroup', name: 'filtered-artifact', version: '1.0'
    myMinusConfig group: 'myGroup', name: 'filtered-artifact', version: '1.0'
    runtimeOnly group: 'myGroup', name: 'missing-artifact', version: '1.0'
    implementation group: 'myGroup', name: 'existing-artifact', version: '1.0'

    idea {
        module {
            scopes.COMPILE.plus += [ configurations.myPlusConfig ]
            scopes.COMPILE.minus += [ configurations.myMinusConfig ]
        }
    }
}
"""
        String expected = """Could not resolve: myGroup:missing-extra-artifact:1.0
Could not resolve: myGroup:missing-artifact:1.0
"""
        result.groupedOutput.task(":ideaModule").output == expected
    }

    @Test
    @ToBeFixedForConfigurationCache
    void "compile only dependencies handled correctly"() {
        // given
        def shared = mavenRepo.module('org.gradle.test', 'shared', '1.0').publish()
        mavenRepo.module('org.gradle.test', 'compile', '1.0').dependsOn(shared).publish()
        mavenRepo.module('org.gradle.test', 'compileOnly', '1.0').dependsOn(shared).publish()
        mavenRepo.module('org.gradle.test', 'testCompileOnly', '1.0').dependsOn(shared).publish()

        // when
        runIdeaTask """
apply plugin: 'java'
apply plugin: 'idea'

repositories {
    maven { url "${mavenRepo.uri}" }
}

dependencies {
    implementation 'org.gradle.test:compile:1.0'
    compileOnly 'org.gradle.test:compileOnly:1.0'
    testCompileOnly 'org.gradle.test:testCompileOnly:1.0'
}
"""

        // then
        def dependencies = parseIml("root.iml").dependencies
        assert dependencies.libraries.size() == 4
        dependencies.assertHasLibrary('COMPILE', 'shared-1.0.jar')
        dependencies.assertHasLibrary('COMPILE', 'compile-1.0.jar')
        dependencies.assertHasLibrary('PROVIDED', 'compileOnly-1.0.jar')
        dependencies.assertHasLibrary('TEST', 'testCompileOnly-1.0.jar')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void "test compile only dependencies mapped to IDEA scopes"() {
        // given
        def shared = mavenRepo.module('org.gradle.test', 'shared', '1.0').publish()
        mavenRepo.module('org.gradle.test', 'compile', '1.0').dependsOn(shared).publish()
        mavenRepo.module('org.gradle.test', 'compileOnly', '1.0').dependsOn(shared).publish()

        // when
        runIdeaTask """
            apply plugin: 'java'
            apply plugin: 'idea'

            repositories {
                maven { url "${mavenRepo.uri}" }
            }

            dependencies {
                testImplementation 'org.gradle.test:compile:1.0'
                testCompileOnly 'org.gradle.test:compileOnly:1.0'
            }
        """.stripIndent()

        // then
        def dependencies = parseIml("root.iml").dependencies
        assert dependencies.libraries.size() == 3
        dependencies.assertHasLibrary('TEST', 'shared-1.0.jar')
        dependencies.assertHasLibrary('TEST', 'compile-1.0.jar')
        dependencies.assertHasLibrary('TEST', 'compileOnly-1.0.jar')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void "conflicting versions of the same library requested for compile and compile-only mapped to IDEA scopes"() {
        // given
        mavenRepo.module('org.gradle.test', 'bothCompileAndCompileOnly', '1.0').publish()
        mavenRepo.module('org.gradle.test', 'bothCompileAndCompileOnly', '2.0').publish()

        // when
        runIdeaTask """
            apply plugin: 'java'
            apply plugin: 'idea'

            repositories {
                maven { url "${mavenRepo.uri}" }
            }

            dependencies {
                implementation 'org.gradle.test:bothCompileAndCompileOnly:1.0'
                compileOnly 'org.gradle.test:bothCompileAndCompileOnly:2.0'
            }
        """.stripIndent()

        // then
        def dependencies = parseIml("root.iml").dependencies
        assert dependencies.libraries.size() == 3
        dependencies.assertHasLibrary('RUNTIME', 'bothCompileAndCompileOnly-1.0.jar')
        dependencies.assertHasLibrary('TEST', 'bothCompileAndCompileOnly-1.0.jar')
        dependencies.assertHasLibrary('PROVIDED', 'bothCompileAndCompileOnly-2.0.jar')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void "conflicting versions of the same library requested for runtime and compile-only mapped to IDEA scopes"() {
        // given
        mavenRepo.module('org.gradle.test', 'bothCompileAndCompileOnly', '1.0').publish()
        mavenRepo.module('org.gradle.test', 'bothCompileAndCompileOnly', '2.0').publish()

        // when
        runIdeaTask """
            apply plugin: 'java'
            apply plugin: 'idea'

            repositories {
                maven { url "${mavenRepo.uri}" }
            }

            dependencies {
                compileOnly 'org.gradle.test:bothCompileAndCompileOnly:2.0'
                runtimeOnly 'org.gradle.test:bothCompileAndCompileOnly:1.0'
            }
        """.stripIndent()

        // then
        def dependencies = parseIml("root.iml").dependencies
        assert dependencies.libraries.size() == 3
        dependencies.assertHasLibrary('PROVIDED', 'bothCompileAndCompileOnly-2.0.jar')
        dependencies.assertHasLibrary('RUNTIME', 'bothCompileAndCompileOnly-1.0.jar')
        dependencies.assertHasLibrary('TEST', 'bothCompileAndCompileOnly-1.0.jar')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void "conflicting versions of the same library requested for test-compile and test-compile-only mapped to IDEA scopes"() {
        // given
        mavenRepo.module('org.gradle.test', 'bothCompileAndCompileOnly', '1.0').publish()
        mavenRepo.module('org.gradle.test', 'bothCompileAndCompileOnly', '2.0').publish()

        // when
        runIdeaTask """
            apply plugin: 'java'
            apply plugin: 'idea'

            repositories {
                maven { url "${mavenRepo.uri}" }
            }

            dependencies {
                testImplementation 'org.gradle.test:bothCompileAndCompileOnly:1.0'
                testCompileOnly 'org.gradle.test:bothCompileAndCompileOnly:2.0'
            }
        """.stripIndent()

        // then
        def dependencies = parseIml("root.iml").dependencies
        assert dependencies.libraries.size() == 2
        dependencies.assertHasLibrary('TEST', 'bothCompileAndCompileOnly-1.0.jar')
        dependencies.assertHasLibrary('TEST', 'bothCompileAndCompileOnly-2.0.jar')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void "providedCompile dependencies are added to PROVIDED only"() {
        // given
        mavenRepo.module('org.gradle.test', 'foo', '1.0').publish()

        // when
        runIdeaTask """
            apply plugin: 'war'
            apply plugin: 'idea'

            repositories {
                maven { url "${mavenRepo.uri}" }
            }

            dependencies {
                providedCompile 'org.gradle.test:foo:1.0'
            }
        """.stripIndent()

        // then
        def dependencies = parseIml("root.iml").dependencies
        assert dependencies.libraries.size() == 1
        dependencies.assertHasLibrary('PROVIDED', 'foo-1.0.jar')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void "providedRuntime dependencies are added to PROVIDED only"() {
        // given
        mavenRepo.module('org.gradle.test', 'foo', '1.0').publish()

        // when
        runIdeaTask """
            apply plugin: 'war'
            apply plugin: 'idea'

            repositories {
                maven { url "${mavenRepo.uri}" }
            }

            dependencies {
                providedRuntime 'org.gradle.test:foo:1.0'
            }
        """.stripIndent()

        // then
        def dependencies = parseIml("root.iml").dependencies
        assert dependencies.libraries.size() == 1
        dependencies.assertHasLibrary('PROVIDED', 'foo-1.0.jar')
    }

}
