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
import org.gradle.integtests.tooling.fixture.WithOldConfigurationsSupport
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.tooling.model.eclipse.EclipseExternalDependency
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.EclipseProjectDependency

@ToolingApiVersion('>=3.0')
@TargetGradleVersion(">=3.0")
class ToolingApiEclipseModelDependencyAccessRuleCrossVersionSpec extends ToolingApiSpecification implements WithOldConfigurationsSupport {

    def setup() {
        def mavenRepo = new MavenFileRepository(file("maven-repo"))
        mavenRepo.module("org.example", "example-lib", "1.0").publish()

        createDirs("sub")
        settingsFile <<
        """rootProject.name = 'root'
           include 'sub'
        """

        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'eclipse'

           repositories {
               maven { url '${mavenRepo.uri}' }
           }

           dependencies {
               ${implementationConfiguration} project(':sub')
               ${implementationConfiguration} 'org.example:example-lib:1.0'
           }

           project(':sub') {
               apply plugin: 'java'
           }
        """
    }

    @TargetGradleVersion(">=2.6 <3.0")
    def "Older versions throw runtime exception when querying access rules"() {
        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        EclipseProjectDependency projectDependency = project.projectDependencies.find { it.path.contains 'sub' }
        EclipseExternalDependency externalDependency = project.classpath.find { it.file.path.contains 'example-lib' }
        projectDependency.getAccessRules()

        then:
        thrown UnsupportedMethodException

        when:
        externalDependency.getAccessRules()

        then:
        thrown UnsupportedMethodException
    }

    def "Has no access rules"() {
        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        EclipseProjectDependency projectDependency = project.projectDependencies.find { it.path.contains 'sub' }
        EclipseExternalDependency externalDependency = project.classpath.find { it.file.path.contains 'example-lib' }

        then:
        projectDependency.accessRules.isEmpty()
        externalDependency.accessRules.isEmpty()

    }

    def "Has some access rules"() {
        setup:
        buildFile <<
        """import org.gradle.plugins.ide.eclipse.model.AccessRule
           eclipse {
               classpath {
                   containers 'classpathContainerPath'
                   file {
                       whenMerged { classpath ->
                           def projectDependency = classpath.entries.find { it.path == '/sub' }
                           projectDependency.accessRules.add(new AccessRule('0', 'sub-accessibleFilesPattern'))
                           projectDependency.accessRules.add(new AccessRule('1', 'sub-nonAccessibleFilesPattern'))

                           def externalDependency = classpath.entries.find { it.path.contains 'example-lib' }
                           externalDependency.accessRules.add(new AccessRule('0', 'lib-accessibleFilesPattern'))
                           externalDependency.accessRules.add(new AccessRule('1', 'lib-nonAccessibleFilesPattern'))
                       }
                   }
               }
           }
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        EclipseProjectDependency projectDependency = project.projectDependencies.find { it.path.contains 'sub' }
        EclipseExternalDependency externalDependency = project.classpath.find { it.file.path.contains 'example-lib' }

        then:
        projectDependency.accessRules.size() == 2
        projectDependency.accessRules[0].kind == 0
        projectDependency.accessRules[0].pattern == 'sub-accessibleFilesPattern'
        projectDependency.accessRules[1].kind == 1
        projectDependency.accessRules[1].pattern == 'sub-nonAccessibleFilesPattern'

        externalDependency.accessRules.size() == 2
        externalDependency.accessRules[0].kind == 0
        externalDependency.accessRules[0].pattern == 'lib-accessibleFilesPattern'
        externalDependency.accessRules[1].kind == 1
        externalDependency.accessRules[1].pattern == 'lib-nonAccessibleFilesPattern'
    }
}
