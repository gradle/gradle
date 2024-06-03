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
import org.gradle.integtests.tooling.fixture.WithOldConfigurationsSupport
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.tooling.model.eclipse.EclipseProject

class ToolingApiEclipseModelCustomLibrarySourceAndJavadocCrossVersionSpec extends ToolingApiSpecification implements WithOldConfigurationsSupport {

    def "Custom source and javadoc location"() {
        setup:
        def mavenRepo = new MavenFileRepository(file("maven-repo"))
        mavenRepo.module("org.example", "example-lib", "1.0").publish()

        File customSource = temporaryFolder.file('custom-source.jar')
        File customJavadoc = temporaryFolder.file('custom-javadoc.jar')
        String customSourcePath = customSource.absolutePath.replace('\\', '\\\\')
        String customJavadocPath = customJavadoc.absolutePath.replace('\\', '\\\\')

        buildFile << """
            apply plugin: 'java'
            apply plugin: 'eclipse'

           ${mavenCentralRepository()}

            dependencies {
                ${implementationConfiguration} 'org.example:example-lib:1.0'
            }

            eclipse {
                classpath {
                    file {
                        whenMerged { classpath ->
                            def lib = classpath.entries.find { it.path.contains('example-lib') }
                            lib.javadocPath = fileReference('$customJavadocPath')
                            lib.sourcePath  = fileReference('$customSourcePath')
                        }
                    }
                }
            }
        """

        when:
        def project = loadToolingModel(EclipseProject)
        def dependency = project.classpath[0]

        then:
        dependency.source == customSource
        dependency.javadoc == customJavadoc
    }
}
