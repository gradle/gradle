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


import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.eclipse.EclipseClasspathContainer
import org.gradle.tooling.model.eclipse.EclipseProject

class ToolingApiEclipseModelClasspathContainerAccessRuleCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        settingsFile << 'rootProject.name = "root"'
    }

    def "Has no access rules"() {
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'eclipse'
           eclipse {
               classpath {
                   containers 'classpathContainerPath'
               }
           }
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)

        then:
        project.classpathContainers.find { it.path == 'classpathContainerPath' }.accessRules.isEmpty()
    }

    def "Has some access rules"() {
        buildFile <<
        """import org.gradle.plugins.ide.eclipse.model.AccessRule
           apply plugin: 'java'
           apply plugin: 'eclipse'
           eclipse {
               classpath {
                   containers 'classpathContainerPath'
                   file {
                       whenMerged { classpath ->
                           def container = classpath.entries.find { it.kind == 'con' && it.path == 'classpathContainerPath' }
                           container.accessRules.add(new AccessRule('0', 'accessibleFilesPattern'))
                           container.accessRules.add(new AccessRule('1', 'nonAccessibleFilesPattern'))
                       }
                   }
               }
           }
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        EclipseClasspathContainer container = project.classpathContainers.find { it.path == 'classpathContainerPath' }

        then:
        container.accessRules.size() == 2
        container.accessRules[0].kind == 0
        container.accessRules[0].pattern == 'accessibleFilesPattern'
        container.accessRules[1].kind == 1
        container.accessRules[1].pattern == 'nonAccessibleFilesPattern'
    }
}
