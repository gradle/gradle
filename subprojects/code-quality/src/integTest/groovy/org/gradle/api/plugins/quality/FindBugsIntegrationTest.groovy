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
package org.gradle.api.plugins.quality

import spock.lang.Issue

class FindBugsIntegrationTest extends AbstractFindBugsPluginIntegrationTest {

    @Issue(["https://github.com/gradle/gradle/issues/1745",
        "https://github.com/gradle/gradle/issues/1094"])
    def "remove non-jar files from -auxclasspath"() {
        given:
        def nonJarFile = temporaryFolder.createFile("test.xml")
        nonJarFile << 'something'
        buildFile << """
            apply plugin: 'findbugs'
            apply plugin: 'java'
            
            dependencies {
                compile files('${nonJarFile.toString().replace('\\', '\\\\')}')
            }
        """
        testDirectory.createFile('src/main/java/a/A.java') << 'package a;class A{}'

        when:
        run 'findbugsMain'

        then:
        result.output.contains('BUILD SUCCESSFUL')
        !result.error.contains('IOException')
        !result.error.contains('Wrong magic bytes')
    }
}
