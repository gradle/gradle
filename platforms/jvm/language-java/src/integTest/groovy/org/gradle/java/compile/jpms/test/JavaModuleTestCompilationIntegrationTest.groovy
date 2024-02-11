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

package org.gradle.java.compile.jpms.test

class JavaModuleTestCompilationIntegrationTest extends AbstractJavaModuleTestingIntegrationTest {

    def "compiles blackbox tests as separate module using the module path"() {
        given:
        buildFile << """
            dependencies { testImplementation 'junit:junit:4.13' }
        """
        consumingModuleInfo('exports consumer')
        consumingModuleClass()
        testModuleInfo('requires consumer', 'requires junit')
        testModuleClass()

        when:
        succeeds ':compileTestJava'

        then:
        javaClassFile('module-info.class').exists()
        javaClassFile('consumer/MainModule.class').exists()
        classFile('java', 'test', 'module-info.class').exists()
        classFile('java', 'test', 'consumer/test/MainModuleTest.class').exists()
    }

    def "fails blackbox test compilation with old junit version"() {
        given:
        // JUnit 4.12 does not declare an Automatic-Module-Name
        buildFile << """
            dependencies { testImplementation 'junit:junit:4.12' }
        """
        consumingModuleInfo('exports consumer')
        consumingModuleClass()
        testModuleInfo('requires consumer', 'requires junit')
        testModuleClass()

        when:
        fails ':compileTestJava'

        then:
        failure.assertHasErrorOutput 'error: module not found: junit'
    }

}
