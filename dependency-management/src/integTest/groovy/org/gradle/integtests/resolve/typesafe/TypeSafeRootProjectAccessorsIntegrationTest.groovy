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

class TypeSafeRootProjectAccessorsIntegrationTest extends AbstractTypeSafeProjectAccessorsIntegrationTest {
    def "generates type-safe accessors for a root project with name = #root)"() {
        given:
        settingsFile << """
            rootProject.name = '$root'
        """
        buildFile << """
            def projectDependency = projects.${accessor}
            assert projectDependency instanceof ProjectDependency
            println("Dependency path: \\"\${projectDependency.dependencyProject.path}\\"")
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
}
