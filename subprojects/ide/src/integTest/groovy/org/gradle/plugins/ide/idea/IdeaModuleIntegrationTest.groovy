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

import org.gradle.integtests.fixtures.TestResources
import org.gradle.plugins.ide.AbstractIdeIntegrationTest
import org.junit.Rule
import org.junit.Test
import spock.lang.Issue

class IdeaModuleIntegrationTest extends AbstractIdeIntegrationTest {
    @Rule
    public final TestResources testResources = new TestResources(testDirectoryProvider)

    @Test
    void enablesCustomizationsOnNewModel() {
        //given
        testResources.dir.create {
            additionalCustomSources {}
            additionalCustomTestSources {}
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
  provided.extendsFrom(compile)
}

idea {
    pathVariables CUSTOM_VARIABLE: file('customModuleContentRoot').parentFile

    module {
        name = 'foo'
        contentRoot = file('customModuleContentRoot')

        sourceDirs += file('additionalCustomSources')
        testSourceDirs += file('additionalCustomTestSources')
        excludeDirs += file('excludeMePlease')

        scopes.PROVIDED.plus += configurations.compile
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
        ['additionalCustomSources', 'additionalCustomTestSources', 'src/main/java'].each { expectedSrcFolder ->
            assert iml.component.content.sourceFolder.find { it.@url.text().contains(expectedSrcFolder) }
        }
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
        scopes.COMPILE.plus += configurations.foo
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

    @Test
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
    void shouldNotPutSourceSetsOutputDirOnClasspath() {
        testFile('build/generated/main/foo.resource').createFile()
        testFile('build/ws/test/service.xml').createFile()

        //when
        runTask 'idea', '''
apply plugin: "java"
apply plugin: "idea"

sourceSets.main.output.dir "$buildDir/generated/main"
sourceSets.test.output.dir "$buildDir/ws/test"
'''
        def iml = parseFile(print: true, 'root.iml')

        //then
        assert iml.component.orderEntry.@scope.collect { it.text() == ['RUNTIME', 'TEST'] }

        def classesDirs = iml.component.orderEntry.library.CLASSES.root.@url.collect { it.text() }
        assert classesDirs.any { it.contains ('generated/main') }
        assert classesDirs.any { it.contains ('ws/test') }
    }

    @Test
    void theBuiltByTaskBeExecuted() {
        //when
        def result = runIdeaTask('''
apply plugin: "java"
apply plugin: "idea"

sourceSets.main.output.dir "$buildDir/generated/main", builtBy: 'generateForMain'
sourceSets.test.output.dir "$buildDir/generated/test", builtBy: 'generateForTest'

task generateForMain << {}
task generateForTest << {}
''')
        //then
        result.assertTasksExecuted(':generateForMain', ':generateForTest', ':ideaModule', ':ideaProject', ':ideaWorkspace', ':idea')
    }

    @Test
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
    compile 'coolGroup:niceArtifact:1.0'
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
    compile 'org.gradle:artifact1:1.0'
    compile 'org.gradle:artifact2:1.0'
}
"""
        def content = getFile([:], 'root.iml').text

        //then
        def a1 = content.indexOf("artifact1")
        def a2 = content.indexOf("artifact2")

        assert [a1, a2] == [a1, a2].sort()
    }

    @Test
    void "respects local dependencies order"() {
        //given
        file('artifact1.jar').createNewFile()
        file('artifact2.jar').createNewFile()

        //when
        runIdeaTask """
apply plugin: 'java'
apply plugin: 'idea'

dependencies {
    compile files('artifact1.jar')
    compile files('artifact2.jar')
}
"""
        def content = getFile([:], 'root.iml').text

        //then
        def a1 = content.indexOf("artifact1")
        def a2 = content.indexOf("artifact2")

        assert [a1, a2] == [a1, a2].sort()
    }

    @Test
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
    compile ':hibernate-core:'
}
"""
        def content = getFile([:], 'root.iml').text

        //then
        content.contains 'hibernate-core.jar'
    }

    @Test
    void doesNotBreakWhenSomeDependenciesCannotBeResolved() {
        //given
        def repoDir = file("repo")
        maven(repoDir).module("groupOne", "artifactTwo").publish()

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
        compile 'groupOne:artifactTwo:1.0'
        compile project(':someApiProject')
        compile 'i.dont:Exist:1.0'
        compile files('someDependency.jar')
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
}
