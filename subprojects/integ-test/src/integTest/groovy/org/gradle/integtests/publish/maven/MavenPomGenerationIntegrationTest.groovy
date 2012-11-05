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
// this spec documents the status quo, not a desired behavior
class MavenPomGenerationIntegrationTest extends AbstractIntegrationSpec {
    def "how configuration of archive task affects generated POM"() {
        buildFile << """
apply plugin: "java"
apply plugin: "maven"

group = "org.gradle.test"
version = 1.9

jar {
    ${jarBaseName ? "baseName = '$jarBaseName'" : ""}
    ${jarVersion ? "version = '$jarVersion'" : ""}
    ${jarExtension ? "extension = '$jarExtension'" : ""}
    ${jarClassifier ? "classifier = '$jarClassifier'" : ""}
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
        def mavenModule = mavenRepo.module("org.gradle.test", pomArtifactId, pomVersion)
        def pom = mavenModule.pom
        pom.groupId == "org.gradle.test"
        pom.artifactId == pomArtifactId
        pom.version == pomVersion
        pom.packaging == pomPackaging

        where:
        jarBaseName  | jarVersion | jarExtension | jarClassifier | pomArtifactId | pomVersion | pomPackaging
        "myBaseName" | "2.3"      | "jar"        | null          | "myBaseName"  | "1.9"      | null
        "myBaseName" | "2.3"      | "war"        | null          | "myBaseName"  | "1.9"      | "war"
    }

    def "how configuration of mavenDeployer.pom object affects generated POM"() {
        buildFile << """
apply plugin: "java"
apply plugin: "maven"

group = "org.gradle.test"
version = 1.9

jar {
    baseName = "jarBaseName"
    version = "2.3"
    extension = "war"
}

uploadArchives {
    repositories {
        mavenDeployer {
            repository(url: "${mavenRepo.uri}")
            ${deployerGroupId ? "pom.groupId = '$deployerGroupId'" : ""}
            ${deployerArtifactId ? "pom.artifactId = '$deployerArtifactId'" : ""}
            ${deployerVersion ? "pom.version = '$deployerVersion'" : ""}
            ${deployerPackaging ? "pom.packaging = '$deployerPackaging'" : ""}
        }
    }
}
        """

        when:
        run "uploadArchives"

        then:
        def mavenModule = mavenRepo.module(pomGroupId, pomArtifactId, pomVersion)
        def pom = mavenModule.pom
        pom.groupId == pomGroupId
        pom.artifactId == pomArtifactId
        pom.version == pomVersion
        pom.packaging == pomPackaging

        where:
        deployerGroupId  | deployerArtifactId   | deployerVersion | deployerPackaging | pomGroupId       | pomArtifactId        | pomVersion | pomPackaging
        "deployer.group" | "deployerArtifactId" | "2.7"           | "jar"             | "deployer.group" | "deployerArtifactId" | "2.7"      | "war"
    }
}
