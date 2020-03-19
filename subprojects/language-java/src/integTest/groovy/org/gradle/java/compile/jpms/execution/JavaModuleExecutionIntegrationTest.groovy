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

package org.gradle.java.compile.jpms.execution

import org.gradle.java.compile.jpms.AbstractJavaModuleIntegrationTest

class JavaModuleExecutionIntegrationTest extends AbstractJavaModuleIntegrationTest {

    def setup() {
        buildFile.text = buildFile.text.replace('java-library', 'application')
        buildFile << """
            application {
                mainClassName = 'consumer.MainModule'
                // mainModuleName = 'consumer'
            }
            dependencies {
                implementation 'org:moda:1.0'
            }
        """
    }

    // documents current behavior, which will be fixed once the feature is implemented
    // https://github.com/gradle/gradle/issues/12428
    def "runs a module using the module path"() {
        given:
        publishJavaModule('moda')
        consumingModuleInfo('requires moda')
        consumingModuleClass('moda.ModaClass')

        when:
        succeeds ':run'

        then:
        outputContains("Module Name: null") // <- this should be 'consumer'
    }

}
