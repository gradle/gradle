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

package org.gradle.plugins.ide.tooling.r65

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.maven.MavenFileModule
import org.gradle.test.fixtures.maven.MavenFileRepository
import spock.lang.Ignore
import spock.lang.Issue

@Issue('https://github.com/gradle/gradle/issues/13137')
@Ignore
@ToolingApiVersion('>=2.9')
@TargetGradleVersion(">=2.9")
class ToolingApiEclipseModelLifeCycleCrossVersionSpec extends ToolingApiSpecification {

    def "Model builder respects dependency modifications declared done in the projectsEvaluated hook"() {
        setup:
        MavenFileRepository mavenRepo = new MavenFileRepository(file('maven-repo'))
        MavenFileModule libApi = mavenRepo.module('org.example', 'lib-api', '1.0').publish()
        mavenRepo.module('org.example', 'lib-impl', '1.0').dependsOn(libApi).publish()
        String localMaven = "maven { url '${mavenRepo.uri}' }"

        settingsFile << 'rootProject.name = "root"'
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'eclipse'

            repositories {
                $localMaven
            }

            dependencies {
                implementation 'org.example:lib-impl:1.0'
            }

            gradle.projectsEvaluated {
                allprojects {
                    configurations.all {
                        dependencies.all { dep ->
                            if (!(dep instanceof ProjectDependency) && dep.hasProperty('transitive')) {
                                dep.transitive = false
                            }
                        }
                    }
                }
            }
        """

        when:
        def project = withConnection { connection ->
            CustomBuildAction action = new CustomBuildAction()
            CustomResultHandler handler = new CustomResultHandler()
            connection.action().projectsLoaded(new CustomBuildAction(), new CustomResultHandler()).buildFinished(action, handler).build().run()
            handler.result
        }

        then:
        project.classpath.size() == 1
    }
}
