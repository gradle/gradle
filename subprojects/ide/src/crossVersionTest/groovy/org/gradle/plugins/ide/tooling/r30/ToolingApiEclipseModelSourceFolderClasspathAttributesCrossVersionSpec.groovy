/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugins.ide.tooling.r30

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.tooling.model.eclipse.EclipseProject

@ToolingApiVersion('>=3.0')
@TargetGradleVersion(">=3.0")
class ToolingApiEclipseModelSourceFolderClasspathAttributesCrossVersionSpec extends ToolingApiSpecification {

    @TargetGradleVersion(">=2.6 <3.0")
    def "Older versions throw runtime exception when querying classpath attributes"() {
        settingsFile << 'rootProject.name = "root"'
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'eclipse'
           eclipse {
               classpath {
                   file {
                       whenMerged { classpath ->
                           classpath.entries.find { it.kind == 'src' && it.path == 'src/main/java' }.entryAttributes.customKey = 'customValue'
                       }
                   }
               }
           }
        """
        file('src/main/java').mkdirs()

        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        project.sourceDirectories.find {it.path == 'src/main/java' }.getClasspathAttributes()

        then:
        thrown UnsupportedMethodException
    }

    @TargetGradleVersion(">=3.0 <4.4")
    def "Source folder doesn't define classpath attributes"() {
        setup:
        settingsFile << 'rootProject.name = "root"'
        buildFile << "apply plugin: 'java'"
        file('src/main/java').mkdirs()

        when:
        EclipseProject project = loadToolingModel(EclipseProject)

        then:
        project.sourceDirectories.find {it.path == 'src/main/java' }.classpathAttributes.isEmpty()
    }

    @TargetGradleVersion(">=3.0 <4.4")
    def "Source folder defines one classpath attribute"() {
        settingsFile << 'rootProject.name = "root"'
        buildFile <<
            """apply plugin: 'java'
           apply plugin: 'eclipse'
           eclipse {
               classpath {
                   file {
                       whenMerged { classpath ->
                           classpath.entries.find { it.kind == 'src' && it.path == 'src/main/java' }.entryAttributes.customKey = 'customValue'
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
        project.sourceDirectories[0].classpathAttributes.size() == 1
        project.sourceDirectories[0].classpathAttributes[0].name == 'customKey'
        project.sourceDirectories[0].classpathAttributes[0].value == 'customValue'
    }

    @TargetGradleVersion(">=3.0 <4.4")
    def "Source folder defines multiple classpath attributes"() {
        settingsFile << 'rootProject.name = "root"'
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
        project.sourceDirectories[0].classpathAttributes.size() == 2
        project.sourceDirectories[0].classpathAttributes.find { it.name == 'key1' && it.value == 'value1'}
        project.sourceDirectories[0].classpathAttributes.find { it.name == 'key2' && it.value == 'value2'}
    }
}
