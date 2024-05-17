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

package org.gradle.java.compile.jpms.javadoc

import org.gradle.java.compile.jpms.AbstractJavaModuleIntegrationTest

class JavaModuleJavadocIntegrationTest extends AbstractJavaModuleIntegrationTest {

    def setup() {
        buildFile << """
            dependencies {
                implementation 'org:moda:1.0'
            }
        """
    }

    def "generates javadoc for a module using the module path"() {
        given:
        publishJavaModule('moda')
        consumingModuleInfo('requires moda')
        consumingModuleClass('moda.ModaClass')

        when:
        succeeds ':javadoc'

        then:
        !file('build/docs/javadoc/consumer/package-summary.html').exists()
        file('build/docs/javadoc/consumer/module-summary.html').exists()
    }

}
