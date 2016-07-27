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
import org.gradle.test.fixtures.maven.MavenDependencyExclusion
import spock.lang.Issue

class MavenJavaProjectPublishIntegrationTest extends AbstractIntegrationSpec {

    @Issue("GRADLE-3513")
    def "can publish jar and meta-data to maven repository"() {
        given:
        using m2

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
    compile "commons-collections:commons-collections:3.2.2"
    runtime "commons-io:commons-io:1.4"
    compile 'org.springframework:spring-core:2.5.6', {
        exclude group: 'commons-logging', module: 'commons-logging'
    }
    compile ('org.apache.camel:camel-jackson:2.15.3') {
        exclude group: 'org.apache.camel'
    }
    compile ("commons-beanutils:commons-beanutils:1.8.3") {
        exclude module: 'commons-logging'
    }
    compile ("commons-dbcp:commons-dbcp:1.4") {
        transitive = false
    }
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
        println mavenModule.pomFile.text
        mavenModule.assertArtifactsPublished("publishTest-1.9.pom", "publishTest-1.9.jar")
        mavenModule.parsedPom.scopes.compile.assertDependsOn("commons-collections:commons-collections:3.2.2", "org.springframework:spring-core:2.5.6",  "commons-dbcp:commons-dbcp:1.4", "org.apache.camel:camel-jackson:2.15.3", "commons-beanutils:commons-beanutils:1.8.3")
        mavenModule.parsedPom.scopes.runtime.assertDependsOn("commons-io:commons-io:1.4")
        assert mavenModule.parsedPom.scopes.compile.hasDependencyExclusion("org.springframework:spring-core:2.5.6", new MavenDependencyExclusion("commons-logging", "commons-logging"))
        assert mavenModule.parsedPom.scopes.compile.hasDependencyExclusion("commons-dbcp:commons-dbcp:1.4", new MavenDependencyExclusion("*", "*"))
        assert mavenModule.parsedPom.scopes.compile.hasDependencyExclusion("org.apache.camel:camel-jackson:2.15.3", new MavenDependencyExclusion("org.apache.camel", "*"))
        assert mavenModule.parsedPom.scopes.compile.hasDependencyExclusion("commons-beanutils:commons-beanutils:1.8.3", new MavenDependencyExclusion("*", "commons-logging"))
    }

    def "compile only dependencies are not included in published pom"() {
        given:
        using m2

        file("settings.gradle") << "rootProject.name = 'publishTest' "

        and:
        buildFile << """
apply plugin: 'java'
apply plugin: 'maven'

group = 'org.gradle.test'
version = '1.1'

repositories {
    mavenCentral()
}

dependencies {
    compileOnly "javax.servlet:servlet-api:2.5"
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
        def mavenModule = mavenRepo.module("org.gradle.test", "publishTest", "1.1")
        mavenModule.assertArtifactsPublished("publishTest-1.1.pom", "publishTest-1.1.jar")
        mavenModule.parsedPom.scopes.size() == 0
    }
}
