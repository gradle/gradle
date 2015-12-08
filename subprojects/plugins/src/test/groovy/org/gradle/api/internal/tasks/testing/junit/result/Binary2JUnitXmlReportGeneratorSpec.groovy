/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit.result

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import java.util.regex.Pattern

class Binary2JUnitXmlReportGeneratorSpec extends Specification {

    public static final int MAX_FILENAME_LEN = 250

    @Rule private TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider()
    private resultsProvider = Mock(TestResultsProvider)
    private generator = new Binary2JUnitXmlReportGenerator(temp.testDirectory, resultsProvider, TestOutputAssociation.WITH_SUITE)

    def setup() {
        generator.saxWriter = Mock(JUnitXmlResultWriter)
    }

    def "writes results"() {
        def fooTest = new TestClassResult(1, 'FooTest', 100)
            .add(new TestMethodResult(1, "foo"))

        def barTest = new TestClassResult(2, 'BarTest', 100)
            .add(new TestMethodResult(2, "bar"))
            .add(new TestMethodResult(3, "bar2"))

        resultsProvider.visitClasses(_) >> { Action action ->
            action.execute(fooTest)
            action.execute(barTest)
        }

        when:
        generator.generate()

        then:
        1 * generator.saxWriter.write(fooTest, _)
        1 * generator.saxWriter.write(barTest, _)
        0 * generator.saxWriter._
    }

    def "adds context information to the failure if something goes wrong"() {
        def fooTest = new TestClassResult(1, 'FooTest', 100)
                .add(new TestMethodResult(1, "foo"))

        resultsProvider.visitClasses(_) >> { Action action ->
            action.execute(fooTest)
        }
        generator.saxWriter.write(fooTest, _) >> { throw new IOException("Boo!") }

        when:
        generator.generate()

        then:
        def ex = thrown(GradleException)
        ex.message.startsWith('Could not write XML test results for FooTest')
        ex.cause.message == "Boo!"
    }

    def fileNameIsSafe(String testName){
        given:
        Pattern pattern = Pattern.compile("[a-zA-Z0-9\\.#_/\$-]+");
        TestClassResult result = new TestClassResult(1, testName, 100)

        when:
        def fileName = generator.getReportFileName(result)
        def matcher = pattern.matcher(fileName);
        println fileName

        then:
        fileName.length() < MAX_FILENAME_LEN
        matcher.matches()

        where:
        testName << [
            'abc ~#@*+%{}<>\\[]|"^' * 20,
            'ąęþó→↓←ß©ęœπąśðæŋ’ə…ł≤µń”„ćźż',
            '| customer1 | 127.0.0.1 | nod1 | 2 | MML command | /webapp/protocolSelection?$selected_rows.Customer=customer1&$selected_rows.ElementManagerIP=127.0.0.1&$selected_rows.Manager=10.0.0.1&$selected_rows.Node=nod1&$selected_rows.NodeAlias=nod1&$selected_rows.Region=region1&datasource=the_silo&FORCE_SHOW_APPLET=YES&login=user3&VNECLI_NODE_TYPE_CLASS_PARAMETERS=OSSRC_IP%3D127.0.0.1%7CNode%3Dnod1&Protocol=OSSRC_MML |'
        ]
    }

}
