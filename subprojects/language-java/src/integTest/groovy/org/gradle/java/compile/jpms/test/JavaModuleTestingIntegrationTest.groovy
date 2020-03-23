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

import org.gradle.java.compile.jpms.AbstractJavaModuleIntegrationTest

class JavaModuleTestingIntegrationTest extends AbstractJavaModuleIntegrationTest {

    def setup() {
        buildFile << """
            ${mavenCentralRepository()}
        """
    }

    def "runs whitebox tests on classpath"() {
        given:
        buildFile << """
            dependencies { testImplementation 'junit:junit:4.13' }
        """
        consumingModuleInfo('exports consumer')
        consumingModuleClass()
        testModuleClass('assertNull(consumer.MainModule.class.getModule().getName())')

        when:
        succeeds ':test'

        then:
        javaClassFile('module-info.class').exists()
        javaClassFile('consumer/MainModule.class').exists()
        classFile('java', 'test', 'consumer/test/MainModuleTest.class').exists()
    }

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

    // documents current behavior, which will be fixed once the feature is implemented
    // https://github.com/gradle/gradle/issues/12427
    def "runs blackbox test as module using the module path"() {
        given:
        buildFile << """
            dependencies { testImplementation 'junit:junit:4.13' }

            tasks.withType(Test) {
                // TODO workaround for module-info.class being scanned, remove once feature is implemented
                scanForTestClasses = false
                include '**/*Test.class'
            }
        """
        consumingModuleInfo('exports consumer')
        consumingModuleClass()
        testModuleInfo('requires consumer', 'requires junit')
        testModuleClass('assertEquals("consumer", consumer.MainModule.class.getModule().getName())')

        when:
        // expected to succeed once the feature is implemented
        fails ':test'

        then:
        outputContains 'java.lang.AssertionError at MainModuleTest.java:11'
    }

    //TODO once running ad module is implemented, add a test reflectively checking that the module version was baked in: module.getDescriptor().rawVersion()

    protected testModuleClass(String statement = 'new consumer.MainModule()') {
        file('src/test/java/consumer/test/MainModuleTest.java').text = """
            package consumer.test;

            import org.junit.Test;
            import static org.junit.Assert.*;

            public class MainModuleTest {

                @Test
                public void testMain() {
                    $statement;
                }
            }
        """
    }

    protected testModuleInfo(String... statements) {
        file('src/test/java/module-info.java').text = "module consumer.test { ${statements.collect { it + ';' }.join(' ') } }"
    }

}
