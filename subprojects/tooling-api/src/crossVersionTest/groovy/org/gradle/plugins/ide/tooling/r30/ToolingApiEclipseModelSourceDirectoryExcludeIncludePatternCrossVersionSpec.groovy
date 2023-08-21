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
import org.gradle.tooling.model.eclipse.EclipseSourceDirectory

@ToolingApiVersion('>=3.0')
@TargetGradleVersion(">=3.0")
class ToolingApiEclipseModelSourceDirectoryExcludeIncludePatternCrossVersionSpec extends ToolingApiSpecification {

    @TargetGradleVersion(">=2.6 <3.0")
    def "Old versions throw runtime exception when querying exclude and include patterns"() {
        setup:
        settingsFile << 'rootProject.name = "root"'
        buildFile <<
            """apply plugin: 'java'
           sourceSets {
               main {
                   java {
                       exclude 'excludePattern'
                       include 'includePattern'
                   }
               }
           }
        """
        file('src/main/java').mkdirs()
        EclipseProject project = loadToolingModel(EclipseProject)
        EclipseSourceDirectory sourceDirectory = project.sourceDirectories.find { it.path == 'src/main/java' }

        when:
        sourceDirectory.excludes

        then:
        thrown UnsupportedMethodException

        when:
        sourceDirectory.includes

        then:
        thrown UnsupportedMethodException
    }

    def "Source folder has no exclude and include patterns defined"() {
        setup:
        settingsFile << 'rootProject.name = "root"'
        buildFile << "apply plugin: 'java'"
        file('src/main/java').mkdirs()

        when:
        EclipseProject project = loadToolingModel(EclipseProject)

        then:
        project.sourceDirectories.size() == 1
        project.sourceDirectories[0].excludes.isEmpty()
        project.sourceDirectories[0].includes.isEmpty()
    }

    def "Source folder has exclude and include patterns defined"() {
        setup:
        def excludePatterns = excludes.collect { "exclude '$it'" }.join('\n')
        def includePatterns = includes.collect { "include '$it'" }.join('\n')
        settingsFile << 'rootProject.name = "root"'
        buildFile <<
        """apply plugin: 'java'
           sourceSets {
               main {
                   java {
                       $excludePatterns
                       $includePatterns
                   }
               }
           }
        """
        file('src/main/java').mkdirs()

        when:
        EclipseProject project = loadToolingModel(EclipseProject)

        then:
        project.sourceDirectories.size() == 1
        project.sourceDirectories[0].excludes == excludes
        project.sourceDirectories[0].includes == includes

        where:
        excludes     | includes
        ['e']        | []
        []           | ['i']
        ['e']        | ['i']
        ['e1', 'e2'] | ['i1', 'i2']
    }
}
