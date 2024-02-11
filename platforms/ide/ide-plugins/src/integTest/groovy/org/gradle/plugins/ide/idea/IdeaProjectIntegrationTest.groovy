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
import org.gradle.plugins.ide.AbstractIdeIntegrationTest
import org.junit.Rule
import org.junit.Test

class IdeaProjectIntegrationTest extends AbstractIdeIntegrationTest {
    @Rule
    public final TestResources testResources = new TestResources(testDirectoryProvider)

    @Test
    @ToBeFixedForConfigurationCache
    void "allows configuring the VCS"() {
        //when
        runTask('idea', '''
apply plugin: "java"
apply plugin: "idea"

idea.project {
    vcs = 'Git'
}
''')

        //then
        def ipr = getFile([:], 'root.ipr').text

        assert ipr.contains('<mapping directory="" vcs="Git"/>')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void enablesCustomizationsOnNewModel() {
        //when
        createDirs("someProjectThatWillBeExcluded", "api")
        def result = runTask ':idea', 'include "someProjectThatWillBeExcluded", "api"', '''
allprojects {
    apply plugin: "java"
    apply plugin: "idea"
}

idea {
    project {
        jdkName = '1.3'
        wildcards += '!?*.ruby'

        //let's remove one of the subprojects from generation:
        modules -= project(':someProjectThatWillBeExcluded').idea.module

        outputFile = new File(outputFile.parentFile, 'someBetterName.ipr')

        ipr {
            withXml {
                def node = it.asNode()
                node.appendNode('someInterestingConfiguration', 'hey buddy!')
            }
        }
    }
}
'''
        result.assertTasksExecuted(":ideaModule", ":ideaProject", ":ideaWorkspace",
            ":api:ideaModule",
            ":idea"
        )

        //then
        def ipr = getFile([:], 'someBetterName.ipr').text
        assert ipr.contains('project-jdk-name="1.3"')
        assert ipr.contains('!?*.ruby')
        assert !ipr.contains('someProjectThatWillBeExcluded')
        assert ipr.contains('hey buddy!')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void configuresHooks() {
        def ipr = file('root.ipr')
        ipr.text = '''<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="CompilerConfiguration">
    <option name="DEFAULT_COMPILER" value="Javac"/>
    <annotationProcessing enabled="false" useClasspath="true"/>
    <wildcardResourcePatterns>
      <entry name="!?*.groovy"/>
      <entry name="!?*.java"/>
      <entry name="!?*.fooBar"/>
    </wildcardResourcePatterns>
  </component>
  <component name="ProjectModuleManager">
    <modules>
      <module fileurl="file://$PROJECT_DIR$/root.iml" filepath="$PROJECT_DIR$/root.iml"/>
    </modules>
  </component>
  <component name="ProjectRootManager" version="2" languageLevel="JDK_1_5" assert-keyword="true" jdk-15="true" project-jdk-type="JavaSDK" assert-jdk-15="true" project-jdk-name="1.5">
    <output url="file://$PROJECT_DIR$/out"/>
  </component>
  <component name="VcsDirectoryMappings">
    <mapping directory="" vcs="Git" />
  </component>
</project>
'''

        //when
        runTask 'idea', '''
apply plugin: "java"
apply plugin: "idea"

def hooks = []

idea {
    project {
        ipr {
            beforeMerged {
                assert it.wildcards.contains('!?*.fooBar')
                it.wildcards << '!?*.fooBarTwo'
                hooks << 'before'
            }
            whenMerged {
                assert it.wildcards.contains('!?*.fooBarTwo')
                hooks << 'when'
            }
        }
    }
}

ideaProject.doLast {
    assert hooks == ['before', 'when']
}
'''
        //then no exception thrown
    }
}
