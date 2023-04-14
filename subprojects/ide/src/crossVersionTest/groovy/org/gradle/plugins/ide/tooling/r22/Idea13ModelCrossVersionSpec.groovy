/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.plugins.ide.tooling.r22

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.idea.IdeaProject

class Idea13ModelCrossVersionSpec extends ToolingApiSpecification {

    def "provides generated sources dir information"() {
        file('build.gradle').text = """
            apply plugin: 'java'
            apply plugin: 'idea'

            idea {
              module {
                sourceDirs += file('foo')
                generatedSourceDirs += file('foo')
                generatedSourceDirs += file('foo2')
              }
            }
        """

        when:
        IdeaProject project = withConnection { connection -> connection.getModel(IdeaProject.class) }
        def contentRoot = project.children[0].contentRoots[0]

        then:
        def generatedSourceDirectories = contentRoot.sourceDirectories.findAll { it.generated }
        def generatedTestDirectories = contentRoot.testDirectories.findAll { it.generated }
        generatedSourceDirectories.collect { it.directory } == [file('foo')]
        generatedSourceDirectories.every { contentRoot.sourceDirectories.contains(it) }
        generatedTestDirectories.every { contentRoot.testDirectories.contains(it) }
    }
}
