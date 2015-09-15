/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.eclipse

import spock.lang.Unroll

class EclipseProjectIntegrationTest extends AbstractEclipseIntegrationSpec {

    def setup(){
        settingsFile.text = "rootProject.name = 'root'"
    }

    void allowsConfiguringEclipseProject() {
        given:
        buildScript """
apply plugin: 'java'
apply plugin: 'eclipse'

eclipse {
  project {
    name = 'someBetterName'
    comment = 'a test project'

    referencedProjects = ['some referenced project'] as Set
    referencedProjects 'some cool project'

    natures = ['test.groovy.nature']
    natures 'test.java.nature'

    buildCommand 'buildThisLovelyProject'
    buildCommand argumentFoo: 'a foo argument', 'buildWithTheArguments'

    linkedResource name: 'linkToFolderFoo', type: 'aFolderFoo', location: '/test/folders/foo'
    linkedResource name: 'linkToUriFoo', type: 'aFooUri', locationUri: 'http://test/uri/foo'

    file {
      withXml { it.asNode().appendNode('motto', 'Stay happy!') }
    }
  }

  jdt {
    sourceCompatibility = 1.4
    targetCompatibility = 1.3
  }
}
        """
        when:
        run("eclipse")
        then:

        project.projectName == 'someBetterName'
        project.comment == 'a test project'
        project.assertHasReferencedProjects('some referenced project', 'some cool project')

        project.assertHasNatures('test.groovy.nature', 'test.java.nature')
        project.assertHasBuilders('org.eclipse.jdt.core.javabuilder','buildThisLovelyProject','buildWithTheArguments')
        project.assertHasBuilder('buildWithTheArguments', [argumentFoo:'a foo argument'])
        project.assertHasLinkedResource('linkToFolderFoo', 'aFolderFoo', '/test/folders/foo')
        project.assertHasLinkedResource('linkToUriFoo', 'aFooUri', 'http://test/uri/foo')

        file('.project').text.contains('<motto>Stay happy!</motto>')

        def jdt = parseJdtFile()
        assert jdt.contains('targetPlatform=1.3')
        assert jdt.contains('source=1.4')
    }

    void enablesBeforeAndWhenHooksForProject() {
        given:
        def projectFile = file('.project')
        projectFile << '''<?xml version="1.0" encoding="UTF-8"?>
<projectDescription>
	<name>root</name>
	<comment/>
	<projects/>
	<natures>
		<nature>org.eclipse.jdt.core.javanature</nature>
		<nature>some.nature.one</nature>
	</natures>
	<buildSpec>
		<buildCommand>
			<name>org.eclipse.jdt.core.javabuilder</name>
			<arguments/>
		</buildCommand>
	</buildSpec>
	<linkedResources/>
</projectDescription>'''

        and:
        buildScript """
apply plugin: 'java'
apply plugin: 'eclipse'

eclipse {
  project {
    file {
      beforeMerged {
        assert it.natures.contains('some.nature.one')
        it.natures << 'some.nature.two'
      }
      whenMerged {
        assert it.natures.contains('some.nature.one')
        assert it.natures.contains('some.nature.two')

        it.natures << 'some.nature.three'
      }
    }
  }
}
        """
        when:
        run "eclipse"

        then:
        project.assertHasNatures('org.eclipse.jdt.core.javanature', 'some.nature.one', 'some.nature.two', 'some.nature.three')
    }

    void enablesBeforeAndWhenAndWithPropertiesHooksForJdt() {
        given:
        def jdtFile = file('.settings/org.eclipse.jdt.core.prefs')
        jdtFile << '''
org.eclipse.jdt.core.compiler.codegen.targetPlatform=1.3
'''

        and:
        buildScript """
apply plugin: 'java'
apply plugin: 'eclipse'

ext.hooks = []

eclipse {

  jdt {
    file {
      beforeMerged {
        hooks << 'beforeMerged'
      }
      whenMerged {
        hooks << 'whenMerged'
        assert '1.1' != it.targetCompatibility.toString()
        it.targetCompatibility = JavaVersion.toVersion('1.1')
      }
      withProperties {
        hooks << 'withProperties'
        it.dummy = 'testValue'
      }
    }
  }
}

eclipseJdt.doLast() {
  assert hooks == ['beforeMerged', 'whenMerged', 'withProperties']
}
        """
        when:
        run "eclipse"
        def jdt = parseJdtFile()
        then:
        //then
        assert jdt.contains('targetPlatform=1.1')
        assert jdt.contains('dummy=testValue')
    }

    @Unroll
    void "setting project name within #hook is deprecated"(){
        given:

        buildScript """
apply plugin: 'java'
apply plugin: 'eclipse'

eclipse {
  project {
    file {
      $hook { project ->
        project.name = "custom-name"
      }
    }
  }
}
"""
        when:
        executer.withDeprecationChecksDisabled()
        run "eclipse"

        then:
        output.contains("Configuring eclipse project name in 'beforeMerged' or 'whenMerged' hook has been deprecated and is scheduled to be removed in Gradle")
        where:
        hook << ["whenMerged", "beforeMerged"]
    }

    String parseJdtFile() {
        file('.settings/org.eclipse.jdt.core.prefs').text
    }
}
