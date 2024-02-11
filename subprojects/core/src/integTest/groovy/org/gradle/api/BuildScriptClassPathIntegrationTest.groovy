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

package org.gradle.api

import groovy.test.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Ignore
import spock.lang.Issue

class BuildScriptClassPathIntegrationTest extends AbstractIntegrationSpec {
    def "script can use xerces without affecting that used for dependency resolution"() {
        buildFile << """
buildscript {
    ${mavenCentralRepository()}
    dependencies {
        classpath 'xerces:xercesImpl:2.9.1'
    }
}
plugins {
    id 'java'
}

${mavenCentralRepository()}

dependencies {
    implementation "com.google.guava:guava:19.0"
}

task show {
    def runtimeClasspath = configurations.runtimeClasspath
    doLast {
        runtimeClasspath.files.each { println it }
    }
}
"""

        expect:
        succeeds("show")
    }

    @Issue("gradle/gradle#742")
    @NotYetImplemented
    @Ignore("Apparently sometimes the test passes on CI")
    def "doesn't cache the metaclass from previous execution if build script changes"() {
        buildFile '''
void bar() {
   println 'Original bar'
}

FileCollection.metaClass.environmentMarkers = { String... markerStrings ->
   bar()
   // to make this test pass, a workaround is to uncomment this line
   // see the ticket for explanation why this happens
   //delegate.class.metaClass = null
}

configurations {
   compile
}

dependencies {
   compile files('foo.jar') { environmentMarkers('sss') }
}

task foo {
   doLast {
      bar()
   }
}
        '''
        run 'foo'

        when:
        buildFile.text = buildFile.text.replaceAll('Original bar', 'New bar')
        succeeds 'foo'

        then:
        outputDoesNotContain('Original bar')
    }

    def 'buildscript classpath has proper usage attribute'() {
        buildFile << """
buildscript {
    configurations.classpath {
        def value = attributes.getAttribute(Usage.USAGE_ATTRIBUTE)
        assert value.name == Usage.JAVA_RUNTIME
    }
}
"""
        expect:
        succeeds()
    }
}
