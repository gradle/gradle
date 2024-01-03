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

package org.gradle.integtests.resolve.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class ProviderApiDependenciesIntegrationTest extends AbstractIntegrationSpec {
    @Issue("https://github.com/gradle/gradle/issues/20722")
    def "can add dependency with addLater derived from another provider with Java plugin"() {
        buildFile << """
plugins {
    id 'java'
}

def version = objects.property(String)

configurations.testImplementation.dependencies.addLater(version.map { project.dependencies.create("com.example:artifact:\${it}") })

tasks.all {} // force realize all tasks

version.set('5.6')

assert configurations.testRuntimeClasspath.allDependencies.size() == 1 
        """
        expect:
        succeeds("help")
    }

    def "can add dependency with addLater derived from another provider indirectly with Java plugin"() {
        buildFile << """
plugins {
    id 'java'
}

def version = objects.property(String)

configurations.testImplementation.dependencies.addLater(provider { project.dependencies.create("com.example:artifact:\${version.get()}") })

tasks.all {} // force realize all tasks 

version.set('5.6') // later set the provider value

assert configurations.testRuntimeClasspath.allDependencies.size() == 1
        """
        expect:
        succeeds("help")
    }
}
