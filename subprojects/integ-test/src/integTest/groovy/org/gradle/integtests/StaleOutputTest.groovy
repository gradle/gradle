/*
 * Copyright 2016 the original author or authors.
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
import spock.lang.Issue

class StaleOutputTest extends AbstractIntegrationSpec {
    @Issue(['GRADLE-2440', 'GRADLE-2579'])
    def 'Stale output is removed after input source directory is emptied.'() {
        setup: 'a minimal java build'
        buildScript("apply plugin: 'java'")
        def fooJavaFile = file('src/main/java/Foo.java') << 'public class Foo {}'
        def fooClassFile = file('build/classes/main/Foo.class')

        when: 'a build is executed'
        succeeds('clean', 'build')

        then: 'class file exists as expected'
        fooClassFile.exists()

        when: 'only java source file is deleted, leaving empty input source directory'
        fooJavaFile.delete()

        and: 'another build is executed'
        succeeds('build')

        then: 'source file was actually deleted'
        !fooClassFile.exists()

        and: 'another build is executed'
        succeeds('build')

        then: 'source file is still absent'
        !fooClassFile.exists()
    }
}
