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

class JavaModuleWhiteboxTestExcutionIntegrationTest extends AbstractJavaModuleTestingIntegrationTest {

    def "runs whitebox tests on classpath"() {
        given:
        buildFile << """
            dependencies { testImplementation 'junit:junit:4.13' }
        """
        consumingModuleInfo('exports consumer')
        consumingModuleClass()
        testModuleClass('org.junit.Assert.assertNull(consumer.MainModule.class.getModule().getName())')

        when:
        succeeds ':test'

        then:
        javaClassFile('module-info.class').exists()
        javaClassFile('consumer/MainModule.class').exists()
        classFile('java', 'test', 'consumer/test/MainModuleTest.class').exists()
    }

    def "runs whitebox test on module path by tweaking command line arguments"() {
        given:
        buildFile << """
            dependencies { testImplementation 'junit:junit:4.13' }
            def patchArgs = ['--patch-module', "consumer=\${compileJava.destinationDirectory.getAsFile().get().path}"]
            compileTestJava {
                options.compilerArgs = patchArgs
            }
            test {
                jvmArgs = patchArgs
            }
        """
        consumingModuleInfo('exports consumer')
        consumingModuleClass()
        testModuleClass('''
            consumer.MainModule mainModule = new consumer.MainModule();
            org.junit.Assert.assertEquals("protected name", mainModule.protectedName());
            org.junit.Assert.assertEquals("consumer", this.getClass().getModule().getName());
            ''', 'org.junit.Test', 'consumer')
        file('src/test/java/module-info.java').text = """
            open module consumer {
                requires junit;
            }
        """

        when:
        succeeds ':test'

        then:
        javaClassFile('module-info.class').exists()
        javaClassFile('consumer/MainModule.class').exists()
        classFile('java', 'test', 'consumer/MainModuleTest.class').exists()
    }

}
