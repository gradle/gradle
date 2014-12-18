/*
 * Copyright 2014 the original author or authors.
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

class MavenPublishVersionRangeIntegrationTest extends AbstractIntegrationSpec {

    public void "version range is mapped to maven syntax in published pom file"() {
        given:
        file("settings.gradle") << "rootProject.name = 'publishTest' "
        and:
        buildFile << """
apply plugin: 'java'
apply plugin: 'maven'

group = 'org.gradle.test'
version = '1.9'

dependencies {
    compile "group:projectA:latest.release"
    runtime "group:projectB:latest.integration"
}

uploadArchives {
    repositories {
        mavenDeployer {
            repository(url: "${mavenRepo.uri}")
        }
    }
}
"""

        when:
        run "uploadArchives"

        then:
        def mavenModule = mavenRepo.module("org.gradle.test", "publishTest", "1.9")
        mavenModule.assertArtifactsPublished("publishTest-1.9.pom", "publishTest-1.9.jar")
        mavenModule.parsedPom.scopes.compile.assertDependsOn("group:projectA:RELEASE")
        mavenModule.parsedPom.scopes.runtime.assertDependsOn("group:projectB:LATEST")
    }
}
