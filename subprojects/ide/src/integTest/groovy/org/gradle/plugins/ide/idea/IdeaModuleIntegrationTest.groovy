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
    public final TestResources testResources = new TestResources()

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

        javaVersion = '1.6'

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
    void plusMinusConfigurationsAreCorrectlyApplied() {
        file('foo.jar', 'bar.jar')
        //when
        runTask 'idea', '''
apply plugin: "java"
apply plugin: "idea"

configurations {
  bar
  foo
  foo.extendsFrom(bar)
}

dependencies {
  bar files('bar.jar')
  foo files('foo.jar')
}

idea {
    module {
        scopes.COMPILE.plus += configurations.foo
        scopes.COMPILE.minus += configurations.bar
    }
}
'''
        def content = getFile([:], 'root.iml').text

        //then
        assert content.contains('foo.jar')
        assert !content.contains('bar.jar')
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
            whenMerged   { it.javaVersion = '1.33'   }
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
    void "should put sourceSet's output dir on classpath"() {
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
    void "the 'buildBy' task be executed"() {
        //when
        def result = runIdeaTask('''
apply plugin: "java"
apply plugin: "idea"

sourceSets.main.output.dir "$buildDir/generated/main", buildBy: 'generateForMain'
sourceSets.test.output.dir "$buildDir/generated/test", buildBy: 'generateForTest'

task generateForMain << {}
task generateForTest << {}
''')
        //then
        result.assertTasksExecuted(':generateForMain', ':generateForTest', ':ideaModule', ':ideaProject', ':ideaWorkspace', ':idea')
    }
}
