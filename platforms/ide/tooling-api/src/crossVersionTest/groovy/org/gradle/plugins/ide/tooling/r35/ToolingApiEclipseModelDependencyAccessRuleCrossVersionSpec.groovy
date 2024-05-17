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

package org.gradle.plugins.ide.tooling.r35

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.tooling.model.eclipse.EclipseClasspathContainer
import org.gradle.tooling.model.eclipse.EclipseExternalDependency
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.EclipseProjectDependency
import org.gradle.tooling.model.eclipse.EclipseSourceDirectory

@TargetGradleVersion(">=3.5")
class ToolingApiEclipseModelDependencyAccessRuleCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        def mavenRepo = new MavenFileRepository(file("maven-repo"))
        mavenRepo.module("org.example", "example-lib", "1.0").publish()
        file('src/main/java').mkdirs()

        createDirs("sub")
        settingsFile << """
            rootProject.name = 'root'
            include 'sub'
        """

        buildFile << """
            allprojects {
                apply plugin: 'java'
                apply plugin: 'eclipse'
            }

            repositories {
               maven { url '${mavenRepo.uri}' }
            }

            dependencies {
               implementation project(':sub')
               implementation 'org.example:example-lib:1.0'
            }

            eclipse {
                classpath {
                    containers 'classpathContainerPath'
                }
            }
        """
    }

    def "access rules can be declared with string literals and with ids for external dependencies"() {
        setup:
        buildFile << """
            import org.gradle.plugins.ide.eclipse.model.AccessRule
            eclipse.classpath.file.whenMerged {
                def dependency = entries.find { it.path.contains 'example-lib' }
                dependency.accessRules.add(new AccessRule('0', 'id-accessible'))
                dependency.accessRules.add(new AccessRule('1', 'id-nonaccessible'))
                dependency.accessRules.add(new AccessRule('2', 'id-discouraged'))
                dependency.accessRules.add(new AccessRule('accessible', 'literal-accessible'))
                dependency.accessRules.add(new AccessRule('nonaccessible', 'literal-nonaccessible'))
                dependency.accessRules.add(new AccessRule('discouraged', 'literal-discouraged'))
            }
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        EclipseExternalDependency dependency = project.classpath.find { it.file.name.contains 'example-lib' }

        then:
        dependency.accessRules.size() == 6
        dependency.accessRules[0].kind == 0
        dependency.accessRules[0].pattern == 'id-accessible'
        dependency.accessRules[1].kind == 1
        dependency.accessRules[1].pattern == 'id-nonaccessible'
        dependency.accessRules[2].kind == 2
        dependency.accessRules[2].pattern == 'id-discouraged'
        dependency.accessRules[3].kind == 0
        dependency.accessRules[3].pattern == 'literal-accessible'
        dependency.accessRules[4].kind == 1
        dependency.accessRules[4].pattern == 'literal-nonaccessible'
        dependency.accessRules[5].kind == 2
        dependency.accessRules[5].pattern == 'literal-discouraged'
    }

    def "access rules can be declared with string literals and with ids for project dependencies"() {
        setup:
        buildFile << """
            import org.gradle.plugins.ide.eclipse.model.AccessRule
            eclipse.classpath.file.whenMerged {
                def dependency = entries.find { it.path.contains '/sub' }
                dependency.accessRules.add(new AccessRule('0', 'id-accessible'))
                dependency.accessRules.add(new AccessRule('1', 'id-nonaccessible'))
                dependency.accessRules.add(new AccessRule('2', 'id-discouraged'))
                dependency.accessRules.add(new AccessRule('accessible', 'literal-accessible'))
                dependency.accessRules.add(new AccessRule('nonaccessible', 'literal-nonaccessible'))
                dependency.accessRules.add(new AccessRule('discouraged', 'literal-discouraged'))
            }
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        EclipseProjectDependency dependency = project.projectDependencies.find { it.path.contains 'sub' }

        then:
        dependency.accessRules.size() == 6
        dependency.accessRules[0].kind == 0
        dependency.accessRules[0].pattern == 'id-accessible'
        dependency.accessRules[1].kind == 1
        dependency.accessRules[1].pattern == 'id-nonaccessible'
        dependency.accessRules[2].kind == 2
        dependency.accessRules[2].pattern == 'id-discouraged'
        dependency.accessRules[3].kind == 0
        dependency.accessRules[3].pattern == 'literal-accessible'
        dependency.accessRules[4].kind == 1
        dependency.accessRules[4].pattern == 'literal-nonaccessible'
        dependency.accessRules[5].kind == 2
        dependency.accessRules[5].pattern == 'literal-discouraged'
    }

    def "access rules can be declared with string literals and with ids for classpath containers"() {
        setup:
        buildFile << """
            import org.gradle.plugins.ide.eclipse.model.AccessRule
            eclipse.classpath.file.whenMerged {
                def container = entries.find { it.path == 'classpathContainerPath' }
                container.accessRules.add(new AccessRule('0', 'id-accessible'))
                container.accessRules.add(new AccessRule('1', 'id-nonaccessible'))
                container.accessRules.add(new AccessRule('2', 'id-discouraged'))
                container.accessRules.add(new AccessRule('accessible', 'literal-accessible'))
                container.accessRules.add(new AccessRule('nonaccessible', 'literal-nonaccessible'))
                container.accessRules.add(new AccessRule('discouraged', 'literal-discouraged'))
            }
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        EclipseClasspathContainer container = project.classpathContainers.find { it.path == 'classpathContainerPath' }

        then:
        container.accessRules.size() == 6
        container.accessRules[0].kind == 0
        container.accessRules[0].pattern == 'id-accessible'
        container.accessRules[1].kind == 1
        container.accessRules[1].pattern == 'id-nonaccessible'
        container.accessRules[2].kind == 2
        container.accessRules[2].pattern == 'id-discouraged'
        container.accessRules[3].kind == 0
        container.accessRules[3].pattern == 'literal-accessible'
        container.accessRules[4].kind == 1
        container.accessRules[4].pattern == 'literal-nonaccessible'
        container.accessRules[5].kind == 2
        container.accessRules[5].pattern == 'literal-discouraged'
    }

    def "access rules can be declared with string literals and with ids for source directories"() {
        setup:
        buildFile << """
            import org.gradle.plugins.ide.eclipse.model.AccessRule
            eclipse.classpath.file.whenMerged {
                def sourceDir = entries.find { it.path == 'src/main/java' }
                sourceDir.accessRules.add(new AccessRule('0', 'id-accessible'))
                sourceDir.accessRules.add(new AccessRule('1', 'id-nonaccessible'))
                sourceDir.accessRules.add(new AccessRule('2', 'id-discouraged'))
                sourceDir.accessRules.add(new AccessRule('accessible', 'literal-accessible'))
                sourceDir.accessRules.add(new AccessRule('nonaccessible', 'literal-nonaccessible'))
                sourceDir.accessRules.add(new AccessRule('discouraged', 'literal-discouraged'))
            }
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        EclipseSourceDirectory sourceDir = project.sourceDirectories.find { it.path == 'src/main/java' }

        then:
        sourceDir.accessRules.size() == 6
        sourceDir.accessRules[0].kind == 0
        sourceDir.accessRules[0].pattern == 'id-accessible'
        sourceDir.accessRules[1].kind == 1
        sourceDir.accessRules[1].pattern == 'id-nonaccessible'
        sourceDir.accessRules[2].kind == 2
        sourceDir.accessRules[2].pattern == 'id-discouraged'
        sourceDir.accessRules[3].kind == 0
        sourceDir.accessRules[3].pattern == 'literal-accessible'
        sourceDir.accessRules[4].kind == 1
        sourceDir.accessRules[4].pattern == 'literal-nonaccessible'
        sourceDir.accessRules[5].kind == 2
        sourceDir.accessRules[5].pattern == 'literal-discouraged'
    }
}
