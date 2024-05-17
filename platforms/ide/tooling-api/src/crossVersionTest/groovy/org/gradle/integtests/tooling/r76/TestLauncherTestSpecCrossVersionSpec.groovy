/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.integtests.tooling.r76

import org.gradle.integtests.tooling.TestLauncherSpec
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.TestExecutionException
import org.gradle.tooling.TestLauncher
import org.gradle.tooling.TestSpecs

import static org.gradle.integtests.tooling.fixture.TextUtil.normaliseLineSeparators

@ToolingApiVersion('>=7.6')
@TargetGradleVersion(">=7.6")
class TestLauncherTestSpecCrossVersionSpec extends TestLauncherSpec {

    def setup() {
        withFailingTest() // ensures that withTestsFor statements are not ignored
    }

    @TargetGradleVersion('>=3.0 <7.6')
    def "older Gradle versions ignore withTestsFor calls"() {
        when:
        launchTests { TestLauncher launcher ->
            launcher.withTestsFor { TestSpecs specs ->
                specs.forTaskPath(':secondTest').includeClass('example.MyTest')
            }
        }

        then:
        Throwable exception = thrown(TestExecutionException)
        exception.cause.message.startsWith 'No matching tests found in any candidate test task'
    }

    def "can select test classes"() {
        when:
        launchTests { TestLauncher launcher ->
            launcher.withTestsFor { TestSpecs specs ->
                specs.forTaskPath(':secondTest')
                     .includeClass('example.MyTest')
                     .includeClass('example2.MyOtherTest2')
            }
        }

        then:
        events.testClassesAndMethods.size() == 5
        assertTestExecuted(className: 'example.MyTest', methodName: 'foo', task: ':secondTest')
        assertTestExecuted(className: 'example.MyTest', methodName: 'foo2', task: ':secondTest')
        assertTestExecuted(className: 'example2.MyOtherTest2', methodName: 'baz', task: ':secondTest')
    }

    def "can select test methods"() {
        when:
        launchTests { TestLauncher launcher ->
            launcher.withTestsFor { TestSpecs specs ->
                specs.forTaskPath(':secondTest').includeMethod('example.MyTest', 'foo')
            }
        }

        then:
        events.testClassesAndMethods.size() == 2
        assertTestExecuted(className: 'example.MyTest', methodName: 'foo', task: ':secondTest')
    }

    def "can select package"() {
        setup:
        when:
        launchTests { TestLauncher launcher ->
            launcher.withTestsFor { TestSpecs specs ->
                specs.forTaskPath(':secondTest').includePackage('example2')
            }
        }

        then:
        events.testClassesAndMethods.size() == 4
        assertTestExecuted(className: 'example2.MyOtherTest', methodName: 'bar', task: ':secondTest')
        assertTestExecuted(className: 'example2.MyOtherTest2', methodName: 'baz', task: ':secondTest')
    }

    def "can select tests with pattern"() {
        when:
        launchTests { TestLauncher launcher ->
            launcher.withTestsFor { TestSpecs specs ->
                specs.forTaskPath(':secondTest').includePattern('example2.MyOtherTest*.ba*')
            }
        }

        then:
        events.testClassesAndMethods.size() == 4
        assertTestExecuted(className: 'example2.MyOtherTest', methodName: 'bar', task: ':secondTest')
        assertTestExecuted(className: 'example2.MyOtherTest2', methodName: 'baz', task: ':secondTest')
    }

    def "can combine different test selection"() {
        setup:
        file('src/test/java/org/AnotherTest.java').text = '''
            package org;
            public class AnotherTest {
                @org.junit.Test public void testThis() throws Exception {
                     org.junit.Assert.assertEquals(1, 1);
                }
            }
        '''

        when:
        launchTests { TestLauncher launcher ->
            launcher.withTestsFor { TestSpecs specs ->
                specs.forTaskPath(':secondTest')
                     .includePackage('org')
                     .includeClass('example2.MyOtherTest')
                     .includeMethod('example.MyTest', 'foo')
                     .includePattern('example2.MyOther*2.baz')
            }
        }

        then:
        events.testClassesAndMethods.size() == 8
        assertTestExecuted(className: 'org.AnotherTest', methodName: 'testThis', task: ':secondTest') // selected by includePackage
        assertTestExecuted(className: 'example2.MyOtherTest', methodName: 'bar', task: ':secondTest') // selected by includeClass
        assertTestExecuted(className: 'example.MyTest', methodName: 'foo', task: ':secondTest') // selected by includeMethod
        assertTestExecuted(className: 'example2.MyOtherTest2', methodName: 'baz', task: ':secondTest') // selected by include
    }

    def "can target same test tasks with multiple test specs"() {
        when:
        launchTests { TestLauncher launcher ->
            launcher.withTestsFor { TestSpecs specs ->
                specs.forTaskPath(':secondTest').includeClass('example.MyTest')
                specs.forTaskPath(':secondTest').includePackage('example2')
            }
        }

        then:
        events.testClassesAndMethods.size() == 7
        assertTestExecuted(className: 'example.MyTest', methodName: 'foo', task: ':secondTest')
        assertTestExecuted(className: 'example.MyTest', methodName: 'foo2', task: ':secondTest')
        assertTestExecuted(className: 'example2.MyOtherTest', methodName: 'bar', task: ':secondTest')
        assertTestExecuted(className: 'example2.MyOtherTest2', methodName: 'baz', task: ':secondTest')
    }


    def "can target different test tasks with one test spec"() {
        when:
        launchTests { TestLauncher launcher ->
            launcher.withTestsFor { TestSpecs specs ->
                specs.forTaskPath(':test').includeClass('example.MyTest')
                specs.forTaskPath(':secondTest').includePackage('example2')
            }
        }

        then:
        events.testClassesAndMethods.size() == 7
        assertTestExecuted(className: 'example.MyTest', methodName: 'foo', task: ':test')
        assertTestExecuted(className: 'example.MyTest', methodName: 'foo2', task: ':test')
        assertTestExecuted(className: 'example2.MyOtherTest', methodName: 'bar', task: ':secondTest')
        assertTestExecuted(className: 'example2.MyOtherTest2', methodName: 'baz', task: ':secondTest')
    }

    def "fails with meaningful error when requested tests not found"() {
        when:
        launchTests { TestLauncher launcher ->
            launcher.withTestsFor { TestSpecs specs ->
                specs.forTaskPath(':test')
                     .includeClass('example.UnknownClass')
                     .includePackage("com.unknown")
                     .includeMethod('com.OtherClass', 'unknownMethod')
                     .includePattern('not.matching.pattern')
            }
        }

        then:
        def e = thrown(TestExecutionException)
        normaliseLineSeparators(e.cause.message) == """No matching tests found in any candidate test task.
    Requested tests:
        Test class: example.UnknownClass in task :test
        Test method com.OtherClass.unknownMethod() in task :test
        Test package com.unknown in task :test
        Test pattern not.matching.pattern in task :test"""
    }

    def "can use patterns in all include methods"() {
        setup:
        file('src/test/java/org/AnotherTest.java').text = '''
            package org;
            public class AnotherTest {
                @org.junit.Test public void testThis() throws Exception {
                     org.junit.Assert.assertEquals(1, 1);
                }
            }
        '''

        when:
        launchTests { TestLauncher launcher ->
            launcher.withTestsFor { TestSpecs specs ->
                specs.forTaskPath(':secondTest')
                     .includePackage('o*g')
                     .includeClass('example2.MyOtherT*')
                     .includeMethod('example.MyT*', 'f*o')
                     .includePattern('example2.MyOther*2.ba*')
            }
        }

        then:
        events.testClassesAndMethods.size() == 8
        assertTestExecuted(className: 'org.AnotherTest', methodName: 'testThis', task: ':secondTest')
        assertTestExecuted(className: 'example2.MyOtherTest', methodName: 'bar', task: ':secondTest')
        assertTestExecuted(className: 'example.MyTest', methodName: 'foo', task: ':secondTest')
        assertTestExecuted(className: 'example2.MyOtherTest2', methodName: 'baz', task: ':secondTest')
    }

    def "can select test classes from test task with filters"() {
        setup:
        buildFile << '''
            secondTest {
                filter {
                    includeTest "example.MyTest", "foo"
                }
            }
        '''

        when:
        launchTests { TestLauncher launcher ->
            launcher.withTestsFor { TestSpecs specs ->
                specs.forTaskPath(':secondTest').includeClass('example.MyTest')
            }
        }

        then:
        events.testClassesAndMethods.size() == 2
        assertTestExecuted(className: 'example.MyTest', methodName: 'foo', task: ':secondTest')
    }

    def "can select tests with patterns from test task with filters"() {
        setup:
        buildFile << '''
            secondTest {
                filter {
                    includeTest "example.MyTest", "foo"
                }
            }
        '''

        when:
        launchTests { TestLauncher launcher ->
            launcher.withTestsFor { TestSpecs specs ->
                specs.forTaskPath(':secondTest').includePattern("example.*")
            }
        }

        then:
        events.testClassesAndMethods.size() == 2
        assertTestExecuted(className: 'example.MyTest', methodName: 'foo', task: ':secondTest')
    }

    def "can select tests in a package from test task with filters"() {
        setup:
        buildFile << '''
            secondTest {
                filter {
                    includeTest "example.MyTest", "foo"
                }
            }
        '''

        when:
        launchTests { TestLauncher launcher ->
            launcher.withTestsFor { TestSpecs specs ->
                specs.forTaskPath(':secondTest').includePackage("example")
            }
        }

        then:
        events.testClassesAndMethods.size() == 2
        assertTestExecuted(className: 'example.MyTest', methodName: 'foo', task: ':secondTest')

    }

    def "can select test methods from test task with filters"() {
        setup:
        buildFile << '''
            secondTest {
                filter {
                    includeTest "example.MyTest", "foo"
                    includeTest "example.MyTest", "foo2"
                }
            }
        '''

        when:
        launchTests { TestLauncher launcher ->
            launcher.withTestsFor { TestSpecs specs ->
                specs.forTaskPath(':secondTest').includeMethod('example.MyTest', 'foo')
            }
        }

        then:
        events.testClassesAndMethods.size() == 2
        assertTestExecuted(className: 'example.MyTest', methodName: 'foo', task: ':secondTest')
    }

    def "cannot bypass test filters from test task configuration"() {
        setup:
        buildFile << '''
            secondTest {
                filter {
                    includeTest "example2.MyOtherTest2", "baz"
                }
            }
        '''

        when:
        launchTests { TestLauncher launcher ->
            launcher.withTestsFor { TestSpecs specs ->
                specs.forTaskPath(':secondTest').includeClass('example.MyTest')
            }
        }

        then:
        Throwable exception = thrown(TestExecutionException)
        exception.cause.message.startsWith 'No matching tests found in any candidate test task'
    }
}
