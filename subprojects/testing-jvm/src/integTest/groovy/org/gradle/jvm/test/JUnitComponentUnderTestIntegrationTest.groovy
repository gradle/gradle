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

package org.gradle.jvm.test

import org.gradle.integtests.fixtures.DefaultTestExecutionResult

class JUnitComponentUnderTestIntegrationTest extends AbstractJUnitTestExecutionIntegrationSpec {

    def "can test a JVM library that declares an external dependency"() {
        given:
        applyJUnitPlugin()
        greeterLibraryWithExternalDependency()
        myTestSuiteSpec('greeter')
        greeterTestCase()

        when:
        succeeds ':myTestGreeterJarBinaryTest'

        then:
        executedAndNotSkipped ':compileGreeterJarGreeterJava', ':compileMyTestGreeterJarBinaryMyTestJava', ':myTestGreeterJarBinaryTest'

        and:
        notExecuted 'greeterApiJar' // API jar of the component under test doesn't need to be built
        notExecuted 'createGreeterJar' // runtime jar of the component under test doesn't need to be built
    }

    def "can test a JVM library that declares an API dependency"() {
        given:
        applyJUnitPlugin()
        greeterLibraryWithExternalDependency()
        superGreeterLibraryWithApiDependencyOnGreeter()
        myTestSuiteSpec('superGreeter')
        superGreeterTestCase()

        when:
        succeeds ':myTestSuperGreeterJarBinaryTest'

        then:
        executedAndNotSkipped ':compileGreeterJarGreeterJava', ':greeterApiJar', ':compileSuperGreeterJarSuperGreeterJava', ':createGreeterJar', ':compileMyTestSuperGreeterJarBinaryMyTestJava', ':myTestSuperGreeterJarBinaryTest'
    }

    def "can test a JVM library that declares an API"() {
        given:
        applyJUnitPlugin()
        greeterLibrary()
        greeterWithPrivateAPI()
        myTestSuiteSpec('greeter')
        greeterTestCase()
        privateApiGreeterTestCase()

        when:
        succeeds ':myTestGreeterJarBinaryTest'

        then:
        executedAndNotSkipped ':compileGreeterJarGreeterJava', ':compileMyTestGreeterJarBinaryMyTestJava', ':myTestGreeterJarBinaryTest'

        and:
        def result = new DefaultTestExecutionResult(testDirectory, 'build', 'myTest', 'greeterJar')
        result.assertTestClassesExecuted('com.acme.GreeterTest', 'com.acme.internal.UtilsTest')
        result.testClass('com.acme.GreeterTest')
            .assertTestCount(1, 0, 0)
            .assertTestsExecuted('testGreeting')
        result.testClass('com.acme.internal.UtilsTest')
            .assertTestCount(1, 0, 0)
            .assertTestsExecuted('testGreetingPrefix')
    }

    def "tests are not re-executed when sources of components under test haven't changed"() {
        given:
        applyJUnitPlugin()
        greeterLibrary()
        myTestSuiteSpec('greeter')
        greeterTestCase()

        when:
        succeeds ':myTestGreeterJarBinaryTest'

        then:
        executedAndNotSkipped ':compileGreeterJarGreeterJava', ':myTestGreeterJarBinaryTest'
        def result = new DefaultTestExecutionResult(testDirectory, 'build', 'myTest', 'greeterJar')
        result.assertTestClassesExecuted('com.acme.GreeterTest')
        result.testClass('com.acme.GreeterTest')
            .assertTestCount(1, 0, 0)
            .assertTestsExecuted('testGreeting')

        when:
        succeeds ':myTestGreeterJarBinaryTest'

        then:
        skipped ':myTestGreeterJarBinaryTest'

    }

    def "updating sources of the component under test should re-execute the tests"() {
        given:
        applyJUnitPlugin()
        greeterLibrary()
        myTestSuiteSpec('greeter')
        greeterTestCase()

        when:
        succeeds ':myTestGreeterJarBinaryTest'

        then:
        executedAndNotSkipped ':compileGreeterJarGreeterJava', ':myTestGreeterJarBinaryTest'

        when:
        file('src/greeter/java/com/acme/Greeter.java').write '''package com.acme;
            public class Greeter {
                public String greet(String name) {
                    return "Hello, " + name;
                }
            }
        '''

        then:
        fails ':myTestGreeterJarBinaryTest'
        def result = new DefaultTestExecutionResult(testDirectory, 'build', 'myTest', 'greeterJar')
        result.assertTestClassesExecuted('com.acme.GreeterTest')
        result.testClass('com.acme.GreeterTest')
            .assertTestCount(1, 1, 0)
            .assertTestsExecuted('testGreeting')
    }

    def "tests should be listed when calling tasks"() {
        given:
        applyJUnitPlugin()
        greeterLibrary()
        myTestSuiteSpec('greeter')
        greeterTestCase()

        when:
        succeeds 'tasks'

        then:
        outputContains 'myTestGreeterJarBinaryTest - Runs test suite \'myTest:greeterJarBinary\'.'
    }

    def "one test suite binary is created for each variant of component under test"() {
        given:
        applyJUnitPlugin()
        greeterLibrary()
        greeterWithVariants()
        myTestSuiteSpec('greeter')
        greeterTestCase()

        when:
        succeeds ':myTestGreeterJava7JarBinaryTest'
        def result = new DefaultTestExecutionResult(testDirectory, 'build', 'myTest', 'greeterJava7Jar')

        then:
        executedAndNotSkipped ':myTestGreeterJava7JarBinaryTest', ':compileGreeterJava7JarGreeterJava'
        result.assertTestClassesExecuted('com.acme.GreeterTest')
        result.testClass('com.acme.GreeterTest')
            .assertTestCount(1, 0, 0)
            .assertTestsExecuted('testGreeting')

        when:
        succeeds ':myTestGreeterJava8JarBinaryTest'
        result = new DefaultTestExecutionResult(testDirectory, 'build', 'myTest', 'greeterJava8Jar')

        then:
        executedAndNotSkipped ':myTestGreeterJava8JarBinaryTest', ':compileGreeterJava8JarGreeterJava'
        result.assertTestClassesExecuted('com.acme.GreeterTest')
        result.testClass('com.acme.GreeterTest')
            .assertTestCount(1, 0, 0)
            .assertTestsExecuted('testGreeting')

    }

    def "junit test run task is properly wired to binaries check tasks and lifecycle check task"() {
        given:
        applyJUnitPlugin()
        greeterLibrary()
        myTestSuiteSpec('greeter')
        greeterTestCase()
        buildFile << '''
            task customGreeterCheck()
            model {
                components {
                    greeter {
                        binaries.all {
                            checkedBy($.tasks.customGreeterCheck)
                        }
                    }
                }
            }
        '''.stripIndent()

        when:
        succeeds 'check'
        then:
        executed ':customGreeterCheck', ':checkGreeterJar', ':checkMyTestGreeterJarBinary', ':myTestGreeterJarBinaryTest'

        when:
        run 'checkMyTestGreeterJarBinary'
        then:
        executed ':myTestGreeterJarBinaryTest'

        when:
        run 'checkGreeterJar'
        then:
        executed ':customGreeterCheck', ':myTestGreeterJarBinaryTest'
    }

    private void greeterLibrary() {
        buildFile << '''
            model {
                components {
                    greeter(JvmLibrarySpec)
                }
            }
        '''
        file('src/greeter/java/com/acme/Greeter.java') << '''package com.acme;
            public class Greeter {
                public String greet(String name) {
                    return "Hello, " + name + "!";
                }
            }
        '''
    }

    private void greeterWithPrivateAPI() {
        buildFile << '''
            model {
                components {
                    greeter {
                        api {
                            exports 'com.acme'
                        }
                    }
                }
            }
        '''
        file('src/greeter/java/com/acme/Greeter.java').write '''package com.acme;
            import com.acme.internal.Utils;

            public class Greeter {
                public String greet(String name) {
                    return Utils.GREETER_PREFIX + name + "!";
                }
            }
        '''
        file('src/greeter/java/com/acme/internal/Utils.java') << '''package com.acme.internal;
            import java.util.Properties;
            import java.io.IOException;

            public class Utils {
                static {
                    String tmp = null;
                    try {
                        Properties properties = new Properties();
                        properties.load(Utils.class.getClassLoader().getResourceAsStream("greeter.properties"));
                        tmp = properties.getProperty("prefix");
                    } catch (Exception ex) {
                        tmp = "not found";
                    } finally {
                        GREETER_PREFIX = tmp;
                    }
                }
                public static final String GREETER_PREFIX;
            }
        '''
        file('src/greeter/resources/greeter.properties') << 'prefix=Hello, '
    }

    private void greeterWithVariants() {
        buildFile << '''
            model {
                components {
                    greeter {
                        targetPlatform 'java7'
                        targetPlatform 'java8'
                    }
                }
            }
        '''
    }

    private void greeterLibraryWithExternalDependency() {
        buildFile << '''
            model {
                components {
                    greeter(JvmLibrarySpec) {
                        sources {
                            java {
                                dependencies {
                                    module 'org.apache.commons:commons-lang3:3.4'
                                }
                            }
                        }
                    }
                }
            }
        '''
        file('src/greeter/java/com/acme/Greeter.java') << '''package com.acme;
            import static org.apache.commons.lang3.text.WordUtils.capitalize;

            public class Greeter {
                public String greet(String name) {
                    return "Hello, " + capitalize(name) + "!";
                }
            }
        '''
    }

    private void superGreeterLibraryWithApiDependencyOnGreeter() {
        buildFile << '''
            model {
                components {
                    superGreeter(JvmLibrarySpec) {
                        api {
                            dependencies {
                                library 'greeter'
                            }
                        }
                    }
                }
            }
        '''
        file('src/superGreeter/java/com/acme/SuperGreeter.java') << '''package com.acme;

            public class SuperGreeter extends Greeter {
                public String greet(String name) {
                    return super.greet(name).toUpperCase();
                }
            }
        '''
    }

    private void greeterTestCase() {
        file('src/myTest/java/com/acme/GreeterTest.java') << '''package com.acme;
            import org.junit.Test;

            import static org.junit.Assert.*;

            public class GreeterTest {
                @Test
                public void testGreeting() {
                    Greeter greeter = new Greeter();
                    assertEquals("Hello, Amanda!", greeter.greet("Amanda"));
                }
            }
        '''
    }

    private void privateApiGreeterTestCase() {
        file('src/myTest/java/com/acme/internal/UtilsTest.java') << '''package com.acme.internal;
            import org.junit.Test;

            import static org.junit.Assert.*;

            public class UtilsTest {
                @Test
                public void testGreetingPrefix() {
                    assertEquals("Hello, ", Utils.GREETER_PREFIX);
                }
            }
        '''
    }

    private void superGreeterTestCase() {
        file('src/myTest/java/com/acme/SuperGreeterTest.java') << '''package com.acme;
            import org.junit.Test;

            import static org.junit.Assert.*;

            public class SuperGreeterTest {
                @Test
                public void testGreeting() {
                    SuperGreeter greeter = new SuperGreeter();
                    assertEquals("HELLO, AMANDA!", greeter.greet("Amanda"));
                }
            }
        '''
    }
}
