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

package org.gradle.integtests.tooling.r30

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.maven.MavenFileModule
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.tooling.model.eclipse.EclipseExternalDependency
import org.gradle.tooling.model.eclipse.EclipseProject

@ToolingApiVersion('>=3.0')
@TargetGradleVersion('>=3.0')
class ToolingApiEclipseModelWtpClasspathCrossVersionSpec extends ToolingApiSpecification {

    String localMaven

    def setup() {
        MavenFileRepository mavenRepo = new MavenFileRepository(file("maven-repo"))
        MavenFileModule exampleApi = mavenRepo.module("org.example", "example-api", "1.0")
        MavenFileModule exampleLib = mavenRepo.module("org.example", "example-lib", "1.0")
        exampleLib.dependsOn(exampleApi)
        exampleApi.publish()
        exampleLib.publish()
        localMaven = "maven { url '${mavenRepo.uri}' }"
    }

    def "wtp minusConfigurations are removed from the classpath"() {
        given:
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'war'
           apply plugin: 'eclipse-wtp'
           repositories { $localMaven }
           dependencies {
               providedRuntime 'org.example:example-api:1.0'
               compile 'org.example:example-lib:1.0'
           }
        """

        when:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)
        Collection<EclipseExternalDependency> classpath = eclipseProject.getClasspath()

        then:
        !classpath.find { it.file.absolutePath.contains 'example-api' }
        classpath.find { it.file.absolutePath.contains 'example-lib' }
    }
}
