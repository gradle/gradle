/*
 * Copyright 2011 the original author or authors.
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
import org.gradle.integtests.fixtures.MavenFileRepository

class MavenJavaProjectPublishIntegrationTest extends AbstractIntegrationSpec {
    public void "can publish jar and meta-data to maven repository"() {
        given:
        file("settings.gradle") << "rootProject.name = 'publishTest' "

        and:
        buildFile << """
apply plugin: 'java'
apply plugin: 'maven'

group = 'org.gradle.test'
version = '1.9'

repositories {
    mavenCentral()
}

dependencies {
    compile "commons-collections:commons-collections:3.2.1"
    runtime "commons-io:commons-io:1.4"
}

uploadArchives {
    repositories {
        mavenDeployer {
            repository(url: uri("maven-repo"))
        }
    }
}
"""

        when:
        run "uploadArchives"

        then:
        def mavenModule = new MavenFileRepository(file("maven-repo")).module("org.gradle.test", "publishTest", "1.9")
        mavenModule.assertArtifactsPublished("publishTest-1.9.pom", "publishTest-1.9.jar")
        mavenModule.pom.scopes.compile.assertDependsOn("commons-collections", "commons-collections", "3.2.1")
        mavenModule.pom.scopes.runtime.assertDependsOn("commons-io", "commons-io", "1.4")
    }
}
