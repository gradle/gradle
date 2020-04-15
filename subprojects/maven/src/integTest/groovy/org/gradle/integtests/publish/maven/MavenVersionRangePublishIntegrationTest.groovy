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
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import spock.lang.Issue
import spock.lang.Unroll

class MavenVersionRangePublishIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        // the OLD publish plugins work with the OLD deprecated Java plugin configuration (compile/runtime)
        executer.noDeprecationChecks()
        using m2 //uploadArchives leaks into local ~/.m2
    }

    @ToBeFixedForInstantExecution
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
    compile "group:projectB:latest.integration"
    compile "group:projectC:1.+"
    compile "group:projectD:[1.0,2.0)"
    compile "group:projectE:[1.0]"
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
        def mavenModule = mavenRepo.module("org.gradle.test", "publishTest", "1.9").withoutExtraChecksums()
        mavenModule.assertArtifactsPublished("publishTest-1.9.pom", "publishTest-1.9.jar")
        mavenModule.parsedPom.scopes.compile.assertDependsOn(
                "group:projectA:RELEASE",
                "group:projectB:LATEST",
                "group:projectC:1.+",
                "group:projectD:[1.0,2.0)",
                "group:projectE:1.0"
        )
    }

    @Issue("GRADLE-3233")
    @Unroll
    @ToBeFixedForInstantExecution
    def "publishes POM dependency with #versionType version for Gradle dependency with null version"() {
        given:
        file("settings.gradle") << "rootProject.name = 'publishTest' "
        and:
        buildFile << """
apply plugin: 'java'
apply plugin: 'maven'

group = 'org.gradle.test'
version = '1.9'

dependencies {
    compile $dependencyNotation
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
        mavenModule.parsedPom.scopes.compile.assertDependsOn("group:projectA:")
        where:
        versionType | dependencyNotation
        "empty"     | "'group:projectA'"
        "null"      | "group:'group', name:'projectA', version:null"
    }
}
