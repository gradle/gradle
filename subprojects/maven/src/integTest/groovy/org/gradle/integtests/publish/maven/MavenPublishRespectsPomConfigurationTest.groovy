/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests.publish.maven

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Ignore

class MavenPublishRespectsPomConfigurationTest extends AbstractIntegrationSpec {

    @Ignore
    def "project dependencies in pom respect renamed artifactId"() {
        setup:
        def root = file("root")
        root.file("settings.gradle") << """
    rootProject.name = "publish"
    include 'project1'
    include 'project2'
    """
        root.file("build.gradle") << """
    subprojects{
        apply plugin:'java'
        apply plugin:'maven'

        group "org.gradle.test"
        version = 0.1

        uploadArchives {
            repositories {
                mavenDeployer {
                    repository(url: uri("${mavenRepo.uri}"))
                }
            }
        }
    }"""

        def project1 = root.file("project1")
        project1.file("build.gradle") << """
        uploadArchives {
            repositories {
                mavenDeployer {
                    pom.project {
                        artifactId 'custom_project1'
                    }
                }
            }
        }
    """
        def project2 = root.file("project2")
        project2.file("build.gradle") << """
    dependencies{
        compile project(":project1")
    }
    """


        when:
        executer.inDirectory(root).withTasks('uploadArchives').run()

        then:
        noExceptionThrown()

        def project1Module = mavenRepo.module("org.gradle.test", "custom_project1", "0.1")
        project1Module.assertArtifactsPublished("custom_project1-0.1.pom", "custom_project1-0.1.jar")
        def project2Module = mavenRepo.module("org.gradle.test", "project2", "0.1")
        project2Module.assertArtifactsPublished("project2-0.1.pom", "project2-0.1.jar")
        project2Module.parsedPom.scopes.compile.assertDependsOn("org.gradle.test:custom_project1:0.1")
    }
}
