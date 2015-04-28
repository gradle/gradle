/*
 * Copyright 2015 the original author or authors.
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


package org.gradle.integtests.tooling.r25

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.test.TestFinishEvent
import org.gradle.tooling.events.test.TestProgressListener

class TestLauncherCrossVersionSpec extends ToolingApiSpecification {

    private TestFile forkingTestBuildFile() {
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:4.12' }
            compileTestJava.options.fork = true  // forked as 'Gradle Test Executor 1'
        """
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "can execute test from BuildInvocation"() {
        given:
        forkingTestBuildFile()

        file("src/test/java/example/MyTest.java") << """
            package example;
            public class MyTest {
                @org.junit.Test public void foo() throws Exception {
                     org.junit.Assert.assertEquals(1, 1);
                }
            }
        """

        when: "we create a new test launcher with a test pattern to execute"
        def result = []
        withConnection {
            ProjectConnection connection ->
                connection.newTestsLauncher()
                    .addJvmTestClasses('example.MyTest')
                    .addTestProgressListener {
                    result.add(it)
                }
                .run()
        }

        then: "the test is executed"
        assert result.size() > 0
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "can execute single test with JVM class include pattern"() {
        given:
        forkingTestBuildFile()

        file("src/test/java/example/MyTest1.java") << """
            package example;
            public class MyTest1 {
                @org.junit.Test public void foo() throws Exception {
                     org.junit.Assert.assertEquals(1, 1);
                }
            }
        """

        file("src/test/java/example/MyTest2.java") << """
            package example;
            public class MyTest2 {
                @org.junit.Test public void foo() throws Exception {
                     org.junit.Assert.assertEquals(1, 2);
                }
            }
        """

        when: "we create a new test launcher with a test pattern to execute"
        def result = []
        withConnection {
            ProjectConnection connection ->
                connection.newTestsLauncher()
                    .addJvmTestClasses('example.MyTest1')
                    .addTestProgressListener {
                    result.add(it)
                }
                .run()
        }

        then: "the test is executed and doesn't fail"
        assert result.size() > 0
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "can execute single test with regex include pattern"() {
        given:
        forkingTestBuildFile()

        file("src/test/java/example/MyTest1.java") << """
            package example;
            public class MyTest1 {
                @org.junit.Test public void foo() throws Exception {
                     org.junit.Assert.assertEquals(1, 1);
                }
            }
        """

        file("src/test/java/example/MyTest2.java") << """
            package example;
            public class MyTest2 {
                @org.junit.Test public void foo() throws Exception {
                     org.junit.Assert.assertEquals(1, 2);
                }
            }
        """

        when: "we create a new test launcher with a test pattern to execute"
        def result = []
        withConnection {
            ProjectConnection connection ->
                connection.newTestsLauncher()
                    .addTestsByPattern('example.MyTest1')
                    .addTestProgressListener {
                    result.add(it)
                }
                .run()
        }

        then: "the test is executed and doesn't fail"
        assert result.size() > 0
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "can execute single test with an exclude pattern"() {
        given:
        forkingTestBuildFile()

        file("src/test/java/example/MyTest1.java") << """
            package example;
            public class MyTest1 {
                @org.junit.Test public void foo() throws Exception {
                     org.junit.Assert.assertEquals(1, 1);
                }
            }
        """

        file("src/test/java/example/MyTest2.java") << """
            package example;
            public class MyTest2 {
                @org.junit.Test public void foo() throws Exception {
                     org.junit.Assert.assertEquals(1, 2);
                }
            }
        """

        when: "we create a new test launcher with a test pattern to exclude"
        def result = []
        withConnection {
            ProjectConnection connection ->
                connection.newTestsLauncher()
                    .excludeJvmTestClasses('example.MyTest2')
                    .addTestProgressListener {
                    result.add(it)
                }
                .run()
        }

        then: "the test is executed and doesn't fail"
        assert result.size() > 0
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "can execute single test method"() {
        given:
        forkingTestBuildFile()

        file("src/test/java/example/MyTest.java") << """
            package example;
            public class MyTest {
                @org.junit.Test public void foo() throws Exception {
                     org.junit.Assert.assertEquals(1, 1);
                }
                @org.junit.Test public void bar() throws Exception {
                     org.junit.Assert.assertEquals(1, 2);
                }
            }
        """

        when: "we create a new test launcher with a test method to execute"
        def result = []
        withConnection {
            ProjectConnection connection ->
                connection.newTestsLauncher()
                    .addJvmTestMethods('example.MyTest', 'foo')
                    .addTestProgressListener {
                    result.add(it)
                }
                .run()
        }

        then: "the test method is executed"
        assert result.size() > 0
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "test will not execute if test task is up-to-date"() {
        given:
        forkingTestBuildFile()

        file("src/test/java/example/MyTest.java") << """
            package example;
            public class MyTest {
                @org.junit.Test public void foo() throws Exception {
                     org.junit.Assert.assertEquals(1, 1);
                }
            }
        """

        TestProgressListener listener = Mock()
        when: "we launch the same test twice"

        withConnection {
            ProjectConnection connection ->
                connection.newTestsLauncher()
                    .addJvmTestMethods('example.MyTest', 'foo')
                    .addTestProgressListener(listener).run()
                connection.newTestsLauncher()
                    .addJvmTestMethods('example.MyTest', 'foo')
                    .addTestProgressListener(listener).run()
        }

        then: "the test method is executed once"
        4 * listener.statusChanged(_ as TestFinishEvent) // 4: test class, test method, test worker, test task
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "can force execution of up-to-date test"() {
        given:
        forkingTestBuildFile()

        file("src/test/java/example/MyTest.java") << """
            package example;
            public class MyTest {
                @org.junit.Test public void foo() throws Exception {
                     org.junit.Assert.assertEquals(1, 1);
                }
            }
        """

        TestProgressListener listener = Mock()
        when: "we launch the same test twice"

        withConnection {
            ProjectConnection connection ->
                connection.newTestsLauncher()
                    .addJvmTestMethods('example.MyTest', 'foo')
                    .addTestProgressListener(listener).run()
                connection.newTestsLauncher()
                    .setAlwaysRunTests(true)
                    .addJvmTestMethods('example.MyTest', 'foo')
                    .addTestProgressListener(listener)
                    .run()
        }

        then: "the test method is executed once"
        8 * listener.statusChanged(_ as TestFinishEvent) // 2 runs x 4: test class, test method, test worker, test task
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")

    def "subsequent tests will execute if test filter is different"() {
        given:
        forkingTestBuildFile()

        file("src/test/java/example/MyTest.java") << """
            package example;
            public class MyTest {
                @org.junit.Test public void foo() throws Exception {
                     org.junit.Assert.assertEquals(1, 1);
                }
                @org.junit.Test public void bar() throws Exception {
                     org.junit.Assert.assertEquals(1, 1);
                }
            }
        """

        TestProgressListener listener = Mock()
        when: "we launch the same test twice"

        withConnection {
            ProjectConnection connection ->
                connection.newTestsLauncher()
                    .addJvmTestMethods('example.MyTest', 'foo')
                    .addTestProgressListener(listener).run()
                connection.newTestsLauncher()
                    .addJvmTestMethods('example.MyTest', 'bar')
                    .addTestProgressListener(listener).run()
        }

        then: "the test method is executed once"
        8 * listener.statusChanged(_ as TestFinishEvent) // 2 runs x 4: test class, test method, test worker, test task
    }
}
