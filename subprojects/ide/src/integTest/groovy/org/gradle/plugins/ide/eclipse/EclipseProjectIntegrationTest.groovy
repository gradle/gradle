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

import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule
import org.junit.Test

class EclipseProjectIntegrationTest extends AbstractEclipseIntegrationTest {

    @Rule
    public final TestResources testResources = new TestResources()

    String content

    @Test
    void allowsConfiguringEclipseProject() {
        //when
        runEclipseTask """
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

    link name: 'linkToFolderFoo', type: 'aFolderFoo', location: '/test/folders/foo'
    link name: 'linkToUriFoo', type: 'aFooUri', locationUri: 'http://test/uri/foo'
  }
}
        """

        //then
        content = getFile([:], '.project').text
        println content

        def dotProject = parseProjectFile()
        assert dotProject.name.text() == 'someBetterName'
        assert dotProject.comment.text() == 'a test project'

        contains('some referenced project', 'some cool project')
        contains('test.java.nature', 'test.groovy.nature')
        contains('buildThisLovelyProject', 'argumentFoo', 'a foo argument', 'buildWithTheArguments')

        contains('linkToFolderFoo', 'aFolderFoo', '/test/folders/foo')
        contains('linkToUriFoo', 'aFooUri', 'http://test/uri/foo')
    }

    protected def contains(String ... contents) {
        contents.each { assert content.contains(it)}
    }
}
