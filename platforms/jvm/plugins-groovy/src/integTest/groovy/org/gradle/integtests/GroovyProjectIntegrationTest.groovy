/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class GroovyProjectIntegrationTest extends AbstractIntegrationSpec {

    def handlesJavaSourceOnly() {
        given:
        buildFile << "apply plugin: 'groovy'"

        and:
        file("src/main/java/somepackage/SomeClass.java") << "public class SomeClass { }"
        file("settings.gradle") << "rootProject.name='javaOnly'"

        when:
        run "build"

        then:
        file("build/libs/javaOnly.jar").exists()
    }

    def "supports central repository declaration"() {
        given:
        buildFile << """
plugins {
    id 'groovy'
}

dependencies {
    implementation 'org.codehaus.groovy:groovy-all:2.5.13'
}
"""
        settingsFile << """
rootProject.name = 'groovyCompilation'
dependencyResolutionManagement {
    ${mavenCentralRepository()}
}
"""
        and:
        file('src/main/groovy/Test.groovy') << """
class Test { }
"""
        when:
        succeeds 'compileGroovy'

        then:
        executedAndNotSkipped(':compileGroovy')
    }
}
