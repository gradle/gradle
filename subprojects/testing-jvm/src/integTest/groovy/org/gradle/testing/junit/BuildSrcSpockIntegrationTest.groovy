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

package org.gradle.testing.junit

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class BuildSrcSpockIntegrationTest extends AbstractIntegrationSpec {
    def "can run spock tests with mock of class using gradleApi"() {
        file("build.gradle") << """
apply plugin: 'groovy'

repositories {
    jcenter()
}

dependencies {
    compile gradleApi()
    compile localGroovy()

    testCompile 'junit:junit:4.12@jar',
        'org.spockframework:spock-core:1.0-groovy-2.4@jar',
        'cglib:cglib-nodep:2.2',
        'org.objenesis:objenesis:1.2'
}
"""
        file("src/main/groovy/MockIt.groovy") << """
class MockIt {
    void call() {
    }
}
"""

        file("src/main/groovy/Caller.groovy") << """
class Caller {
    private MockIt callable

    Caller(MockIt callable) {
        this.callable = callable
    }

    void call() {
       callable.call()
    }
}
"""
        file("src/test/groovy/TestSpec.groovy") << """
import spock.lang.Specification

class TestSpec extends Specification {
    def testMethod() {
        final callable = Mock(MockIt)
        def caller = new Caller(callable)
        when:
        caller.call()
        then:
        1 * callable.call()
        0 * _
    }
}
"""
        expect:
        succeeds("test")
    }
}
