/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.java.compile.jpms.compile

import org.gradle.java.compile.jpms.AbstractJavaModuleIntegrationTest

class CachingAndIncrementalJavaModuleCompileIntegrationTest extends AbstractJavaModuleIntegrationTest {

    def setup() {
        buildFile << """
            dependencies {
                api 'org:moda:1.0'
            }
        """
    }

    def "detects changes in module-info file"() {
        when:
        publishJavaModule('moda')
        consumingModuleInfo()
        consumingModuleClass('moda.ModaClass')

        then:
        fails ':compileJava'

        when:
        consumingModuleInfo('requires moda')
        succeeds ':compileJava'

        then:
        javaClassFile('module-info.class').exists()
        javaClassFile('consumer/MainModule.class').exists()
    }

    def "detects change from library to module"() {
        when:
        publishJavaLibrary('moda')
        consumingModuleInfo('requires moda')
        consumingModuleClass('moda.ModaClass')

        then:
        fails ':compileJava'
        failure.assertHasErrorOutput 'error: module not found: moda'

        when:
        publishJavaModule('moda')
        succeeds ':compileJava'

        then:
        javaClassFile('module-info.class').exists()
        javaClassFile('consumer/MainModule.class').exists()
    }

    def "detects change from library to automatic module"() {
        when:
        publishJavaLibrary('moda')
        consumingModuleInfo('requires moda')
        consumingModuleClass('moda.ModaClass')

        then:
        fails ':compileJava'
        failure.assertHasErrorOutput 'error: module not found: moda'

        when:
        publishAutoModule('moda')
        succeeds ':compileJava'

        then:
        javaClassFile('module-info.class').exists()
        javaClassFile('consumer/MainModule.class').exists()
    }

    def "detects change from module to library"() {
        when:
        publishJavaModule('moda')
        consumingModuleInfo('requires moda')
        consumingModuleClass('moda.ModaClass')
        succeeds ':compileJava'

        then:
        javaClassFile('module-info.class').exists()
        javaClassFile('consumer/MainModule.class').exists()

        when:
        publishJavaLibrary('moda')
        fails ':compileJava'

        then:
        failure.assertHasErrorOutput 'error: module not found: moda'
    }

}
