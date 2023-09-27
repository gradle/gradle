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
class ToolingApiEclipseModelSourceDirectoryAccessRuleCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        settingsFile << 'rootProject.name = "root"'
    }

    @TargetGradleVersion(">=2.6 <3.0")
    def "Older versions throw runtime exception when querying access rules"() {
        buildFile << "apply plugin: 'java'"
        file('src/main/java').mkdirs()

        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        project.sourceDirectories.find {it.path == 'src/main/java' }.getAccessRules()

        then:
        thrown UnsupportedMethodException
    }

    def "Has no access rules"() {
        buildFile << "apply plugin: 'java'"
        file('src/main/java').mkdirs()

        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        EclipseSourceDirectory sourceDirectory = project.sourceDirectories.find { it.path == 'src/main/java' }

        then:
        sourceDirectory.accessRules.isEmpty()
    }

    def "Has some access rules defined"() {
        buildFile <<
        """import org.gradle.plugins.ide.eclipse.model.AccessRule
           apply plugin: 'java'
           apply plugin: 'eclipse'
           eclipse {
               classpath {
                   file {
                       whenMerged { classpath ->
                           def sourceDir = classpath.entries.find { it.kind == 'src' && it.path == 'src/main/java' }
                           sourceDir.accessRules.add(new AccessRule('0', 'accessibleFilesPattern'))
                           sourceDir.accessRules.add(new AccessRule('1', 'nonAccessibleFilesPattern'))
                       }
                   }
               }
           }
        """
        file('src/main/java').mkdirs()

        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        EclipseSourceDirectory sourceDirectory = project.sourceDirectories.find { it.path == 'src/main/java' }

        then:
        sourceDirectory.accessRules.size() == 2
        sourceDirectory.accessRules[0].kind == 0
        sourceDirectory.accessRules[0].pattern == 'accessibleFilesPattern'
        sourceDirectory.accessRules[1].kind == 1
        sourceDirectory.accessRules[1].pattern == 'nonAccessibleFilesPattern'

    }
}
