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
package org.gradle.integtests.testing

import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.internal.AbstractIntegrationTest
import org.junit.Rule
import org.junit.Test
import spock.lang.Issue

public class TestOutputListenerIntegrationTest extends AbstractIntegrationTest {
    @Rule public final TestResources resources = new TestResources()

    @Test
    @Issue("GRADLE-1009")
    public void "standard output is shown when tests are executed"() {
        def test = file("src/test/java/SomeTest.java")
        test << """
import org.junit.*;

public class SomeTest {
    @Test
    public void showsOutputWhenPassing() {
        System.out.println("out passing");
        System.err.println("err passing");
        Assert.assertTrue(true);
    }

    @Test
    public void showsOutputWhenFailing() {
        System.out.println("out failing");
        System.err.println("err failing");
        Assert.assertTrue(false);
    }
}
"""
        def buildFile = file('build.gradle')
        buildFile << """
apply plugin: 'java'

repositories {
    mavenCentral()
}

dependencies {
    testCompile "junit:junit:4.8.2"
}

test.addOutputListener(new VerboseOutputListener(logger: project.logger))

class VerboseOutputListener implements OutputListener {

    def logger

    public void onOutput(OutputEvent outputEvent) {
        logger.lifecycle("" + outputEvent.destination + " " + outputEvent.message);
    }
}
"""

        //when
        def failure = executer.withTasks('test').runWithFailure()

        //then
        assert failure.output.contains('StdOut out passing')
        assert failure.output.contains('StdOut out failing')
        assert failure.output.contains('StdErr err passing')
        assert failure.output.contains('StdErr err failing')
    }
}
