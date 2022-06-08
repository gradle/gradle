/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.plugins

import org.gradle.integtests.fixtures.WellBehavedPluginTest
import spock.lang.Issue

class WarPluginIntegrationTest extends WellBehavedPluginTest {

    def "setup"() {
        file("settings.gradle") << "rootProject.name='root'"
    }

    @Override
    String getMainTask() {
        return "build"
    }

    @Issue("https://github.com/gradle/gradle/issues/20642")
    def "conflicting dependency files are renamed to include their group"() {
        given:
        mavenRepo.module('org.gradle.first', 'name', '1.0').publish()
        mavenRepo.module('org.gradle.second', 'name', '1.0').publish()
        mavenRepo.module('org.gradle.first', 'other', '1.0').publish()

        and:
        buildFile << """
plugins {
  id 'war'
}
repositories {
    maven { url '$mavenRepo.uri' }
}

dependencies {
    implementation 'org.gradle.first:name:1.0'
    implementation 'org.gradle.second:name:1.0'
    implementation 'org.gradle.first:other:1.0'
}
"""
        when:
        run "war"
        and:
        file("build/libs/root.war").unzipTo(file("unzipped"))

        then:
        file('unzipped/WEB-INF/lib').allDescendants() ==
            ['org-gradle-first-name-1.0.jar', 'org-gradle-second-name-1.0.jar', 'other-1.0.jar'] as Set
    }
}
