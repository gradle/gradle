/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.integtests.resolve.typesafe

import spock.lang.Issue

import static org.gradle.test.fixtures.dsl.GradleDsl.GROOVY
import static org.gradle.test.fixtures.dsl.GradleDsl.KOTLIN

class TypeSafeRootProjectAccessorsIntegrationTest extends AbstractTypeSafeProjectAccessorsIntegrationTest {
    def "generates type-safe accessors for a root project with name = #root)"() {
        given:
        settingsFile << """
            rootProject.name = '$root'
        """
        buildFile << """
            def projectDependency = projects.${accessor}
            assert projectDependency instanceof ProjectDependency
            println('Dependency path: "' + projectDependency.path + '"')
        """

        when:
        succeeds 'help'

        then:
        outputContains 'Dependency path: ":"'

        where:
        root         | accessor
        'test'       | 'test'
        'root'       | 'root'
        'snake_case' | 'snakeCase'
        'kebab-case' | 'kebabCase'
        'camelCase'  | 'camelCase'

    }

    @Issue("https://github.com/gradle/gradle/issues/19254")
    def "can use accessor for root-level project named #name in #dsl DSL"() {
        given:
        createDirs(name)
        settingsFile << """
            rootProject.name = 'test'
            include '$name'
        """
        getBuildFile(dsl) << """
            println("Dependency path: [" + projects.${name}.path + "]")
        """

        when:
        succeeds 'help'

        then:
        outputContains "Dependency path: [:$name]"

        where:
        name                | dsl
        'extensions'        | GROOVY
        'conventionMapping' | GROOVY
        'extensions'        | KOTLIN
    }
}
