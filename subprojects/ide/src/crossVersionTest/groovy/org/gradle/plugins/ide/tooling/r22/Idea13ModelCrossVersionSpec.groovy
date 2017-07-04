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

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.idea.*

class Idea13ModelCrossVersionSpec extends ToolingApiSpecification {

    @TargetGradleVersion(">=2.2 <2.3")
    @ToolingApiVersion(">=2.2")
    def "provides partial generated sources dir information"() {

        file('build.gradle').text = """
apply plugin: 'java'
apply plugin: 'idea'

idea {
  module {
    sourceDirs += file('foo')
    testSourceDirs += file('foo2')
    generatedSourceDirs += file('foo')
    generatedSourceDirs += file('foo2')
  }
}
"""

        when:
        IdeaProject project = withConnection { connection -> connection.getModel(IdeaProject.class) }
        def contentRoot = project.children[0].contentRoots[0]

        then:
        contentRoot.sourceDirectories.findAll { it.generated }.collect { it.directory } == [file('foo')]
        contentRoot.testDirectories.findAll { it.generated }.collect { it.directory } == [file('foo2')]
        // 2.2 always returned empty `generatedSourceDirectories` and `generatedTestDirectories`
    }

    @TargetGradleVersion(">=2.3")
    @ToolingApiVersion(">=2.2")
    def "provides generated sources dir information"() {

        file('build.gradle').text = """
apply plugin: 'java'
apply plugin: 'idea'

idea {
  module {
    sourceDirs += file('foo')
    testSourceDirs += file('foo2')
    generatedSourceDirs += file('foo')
    generatedSourceDirs += file('foo2')
  }
}
"""

        when:
        IdeaProject project = withConnection { connection -> connection.getModel(IdeaProject.class) }
        def contentRoot = project.children[0].contentRoots[0]

        then:
        contentRoot.sourceDirectories.findAll { it.generated }.collect { it.directory } == [file('foo')]
        contentRoot.testDirectories.findAll { it.generated }.collect { it.directory } == [file('foo2')]
        contentRoot.generatedSourceDirectories.size() == 1
        contentRoot.generatedSourceDirectories.every { contentRoot.sourceDirectories.contains(it) }
        contentRoot.generatedTestDirectories.size() == 1
        contentRoot.getGeneratedTestDirectories().every { contentRoot.testDirectories.contains(it) }
    }
}
