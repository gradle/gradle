/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.testing

import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.testing.fixture.JUnitMultiVersionIntegrationSpec
import org.junit.Before
import org.junit.Rule
import spock.lang.Issue

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_4_LATEST
import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_VINTAGE_JUPITER

@Issue("GRADLE-1009")
@TargetCoverage({ JUNIT_4_LATEST + JUNIT_VINTAGE_JUPITER })
class TestOutputListenerIntegrationTest extends JUnitMultiVersionIntegrationSpec {
    @Rule public final TestResources resources = new TestResources(temporaryFolder)

    @Before
    void before() {
        executer.noExtraLogging()
    }

    def "can use standard output listener for tests"() {
        given:
        def test = file("src/test/java/SomeTest.java")
        test << """
import org.junit.*;

public class SomeTest {
    @Test public void showsOutputWhenPassing() {
        System.out.println("out passing");
        System.err.println("err passing");
        Assert.assertTrue(true);
    }

    @Test public void showsOutputWhenFailing() {
        System.out.println("out failing");
        System.err.println("err failing");
        Assert.assertTrue(false);
    }
}
"""
        buildFile << """
apply plugin: 'java'
${mavenCentralRepository()}
dependencies { testImplementation "$testJunitCoordinates" }

test.addTestOutputListener(new VerboseOutputListener(logger: project.logger))

def removeMe = new RemoveMeListener()
test.addTestOutputListener(removeMe)
test.removeTestOutputListener(removeMe)

class VerboseOutputListener implements TestOutputListener {

    def logger

    public void onOutput(TestDescriptor descriptor, TestOutputEvent event) {
        logger.lifecycle(descriptor.toString() + " " + event.destination + " " + event.message);
    }
}

class RemoveMeListener implements TestOutputListener {
    public void onOutput(TestDescriptor descriptor, TestOutputEvent event) {
        println "remove me!"
    }
}
"""

        when:
        def failure = executer.withTasks('test').runWithFailure()

        then:
        failure.output.contains('Test showsOutputWhenPassing(SomeTest) StdOut out passing')
        failure.output.contains('Test showsOutputWhenFailing(SomeTest) StdOut out failing')
        failure.output.contains('Test showsOutputWhenPassing(SomeTest) StdErr err passing')
        failure.output.contains('Test showsOutputWhenFailing(SomeTest) StdErr err failing')

        !failure.output.contains("remove me!")
    }

    @UnsupportedWithConfigurationCache
    def "can register output listener at gradle level and using onOutput method"() {
        given:
        def test = file("src/test/java/SomeTest.java")
        test << """
import org.junit.*;

public class SomeTest {
    @Test public void foo() {
        System.out.println("message from foo");
    }
}
"""
        buildFile << """
apply plugin: 'java'
${mavenCentralRepository()}
dependencies { testImplementation "junit:junit:4.13" }

test.onOutput { descriptor, event ->
    logger.lifecycle("first: " + event.message)
}

gradle.addListener(new VerboseOutputListener(logger: project.logger))

class VerboseOutputListener implements TestOutputListener {

    def logger

    public void onOutput(TestDescriptor descriptor, TestOutputEvent event) {
        logger.lifecycle("second: " + event.message);
    }
}
"""

        when:
        succeeds('test')

        then:
        outputContains('first: message from foo')
        outputContains('second: message from foo')
    }

    def "shows standard streams configured via closure"() {
        given:
        def test = file("src/test/java/SomeTest.java")
        test << """
import org.junit.*;

public class SomeTest {
    @Test public void foo() {
        System.out.println("message from foo");
    }
}
"""
        buildFile << """
apply plugin: 'java'
${mavenCentralRepository()}
dependencies { testImplementation "junit:junit:4.13" }

test.testLogging {
    showStandardStreams = true
}
"""

        when:
        executer.withArgument('-i')
        succeeds('test')

        then:
        outputContains('message from foo')
    }

    def "shows standard stream also for testNG"() {
        given:
        ignoreWhenJUnitPlatform()
        def test = file("src/test/java/SomeTest.java")
        test << """
import org.testng.*;
import org.testng.annotations.*;

public class SomeTest {
    @Test public void foo() {
        System.out.println("output from foo");
        System.err.println("error from foo");
    }
}
"""

        buildFile << """
apply plugin: 'java'
${mavenCentralRepository()}
dependencies { testImplementation 'org.testng:testng:6.3.1' }

test {
    useTestNG()
    testLogging.showStandardStreams = true
}
"""
        when: "run with quiet"
        executer.withArguments("-q")
        succeeds('test')

        then:
        outputDoesNotContain('output from foo')

        when: "run with lifecycle"
        executer.noExtraLogging()
        succeeds('cleanTest', 'test')

        then:
        outputContains('output from foo')
    }
}
