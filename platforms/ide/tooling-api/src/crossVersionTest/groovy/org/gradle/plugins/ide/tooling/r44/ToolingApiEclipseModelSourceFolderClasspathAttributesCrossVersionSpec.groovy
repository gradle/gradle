/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.plugins.ide.tooling.r44

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.eclipse.EclipseProject

@ToolingApiVersion('>=4.4')
@TargetGradleVersion(">=4.4")
class ToolingApiEclipseModelSourceFolderClasspathAttributesCrossVersionSpec extends ToolingApiSpecification {

    def "Source folder contains source set information in classpath attributes"() {
        setup:
        buildFile << "apply plugin: 'java'"
        file('src/main/java').mkdirs()
        file('src/test/java').mkdirs()

        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        def mainDirAttributes = project.sourceDirectories.find { it.path == 'src/main/java' }.classpathAttributes
        def testDirAttributes = project.sourceDirectories.find { it.path == 'src/test/java' }.classpathAttributes

        then:
        mainDirAttributes.find { it.name == 'gradle_scope' && it.value == 'main' }
        mainDirAttributes.find { it.name == 'gradle_used_by_scope' && it.value == 'main,test' }
        testDirAttributes.find { it.name == 'gradle_scope' && it.value == 'test' }
        testDirAttributes.find { it.name == 'gradle_used_by_scope' && it.value == 'test' }
    }

    def "Source folder defines additional classpath attributes"() {
        buildFile <<
            """apply plugin: 'java'
           apply plugin: 'eclipse'
           eclipse {
               classpath {
                   file {
                       whenMerged { classpath ->
                           classpath.entries.find { it.kind == 'src' && it.path == 'src/main/java' }.entryAttributes.key1 = 'value1'
                           classpath.entries.find { it.kind == 'src' && it.path == 'src/main/java' }.entryAttributes.key2 = 'value2'
                       }
                   }
               }
           }
        """
        file('src/main/java').mkdirs()

        when:
        EclipseProject project = loadToolingModel(EclipseProject)

        then:
        project.sourceDirectories.size() == 1
        project.sourceDirectories[0].classpathAttributes.size() == 4
        project.sourceDirectories[0].classpathAttributes.find { it.name == 'gradle_scope' && it.value == 'main'}
        project.sourceDirectories[0].classpathAttributes.find { it.name == 'gradle_used_by_scope' && it.value == 'main,test'}
        project.sourceDirectories[0].classpathAttributes.find { it.name == 'key1' && it.value == 'value1'}
        project.sourceDirectories[0].classpathAttributes.find { it.name == 'key2' && it.value == 'value2'}
    }


    def "Source dir information can be modified in whenMerged block"() {
        buildFile <<
            """apply plugin: 'java'
           apply plugin: 'eclipse'
           eclipse {
               classpath {
                   file {
                       whenMerged { classpath ->
                           def entry = classpath.entries.find { it.kind == 'src' && it.path == 'src/main/java' }
                           entry.entryAttributes['gradle_scope'] = 'foo'
                           entry.entryAttributes['gradle_used_by_scope'] = 'foo,bar'
                       }
                   }
               }
           }
        """
        file('src/main/java').mkdirs()

        when:
        EclipseProject project = loadToolingModel(EclipseProject)

        then:
        project.sourceDirectories.size() == 1
        project.sourceDirectories[0].classpathAttributes.size() == 2
        project.sourceDirectories[0].classpathAttributes.find { it.name == 'gradle_scope' && it.value == 'foo'}
        project.sourceDirectories[0].classpathAttributes.find { it.name == 'gradle_used_by_scope' && it.value == 'foo,bar'}
    }
}
