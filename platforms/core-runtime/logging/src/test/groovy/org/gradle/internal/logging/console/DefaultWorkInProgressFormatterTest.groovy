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

package org.gradle.internal.logging.console

import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.nativeintegration.console.ConsoleMetaData
import org.gradle.internal.operations.OperationIdentifier
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Subject

class DefaultWorkInProgressFormatterTest extends Specification {
    // Two UTF-16 code units
    private static final String ROCKET = "\uD83D\uDE80"

    def consoleMetaData = Mock(ConsoleMetaData)
    @Subject statusBarFormatter = new DefaultWorkInProgressFormatter(consoleMetaData)

    def "formats operations"() {
        given:
        def op1 = new ProgressOperation("STATUS_1", "VARIANT_CATEGORY", new OperationIdentifier(1), null)
        def op2 = new ProgressOperation(null,  null, new OperationIdentifier(2), op1)
        def op3 = new ProgressOperation("STATUS_2", "VARIANT_CATEGORY", new OperationIdentifier(3), op2)

        expect:
        statusBarFormatter.format(op3).first().text == "> STATUS_1 > STATUS_2"
        statusBarFormatter.format(op2).first().text == "> STATUS_1"
        statusBarFormatter.format(op1).first().text == "> STATUS_1"
    }

    @Issue("https://github.com/gradle/gradle/issues/39544")
    def "adds the number of completed work actions to the line of their parent, from its second work action"() {
        given:
        def task = new ProgressOperation(":work", "VARIANT_CATEGORY", new OperationIdentifier(1), null)
        def first = new ProgressOperation("Executing work action A", "VARIANT_CATEGORY", new OperationIdentifier(2), task, true)
        task.addChild(first)

        expect:
        statusBarFormatter.format(task).first().text == "> :work"

        when:
        task.addChild(new ProgressOperation("Executing work action B", "VARIANT_CATEGORY", new OperationIdentifier(3), task, true))

        then:
        statusBarFormatter.format(task).first().text == "> :work > 0 work actions completed"
        statusBarFormatter.format(first).first().text == "> :work > Executing work action A"

        when:
        task.workActionCompleted()

        then:
        statusBarFormatter.format(task).first().text == "> :work > 1 work action completed"

        when:
        task.workActionCompleted()

        then:
        statusBarFormatter.format(task).first().text == "> :work > 2 work actions completed"
    }

    def "keeps a line of #length characters, which fits in one less than the console width"() {
        given:
        consoleMetaData.getCols() >> 80

        expect:
        statusBarFormatter.format(operationOf(line)).first().text == "> $line"

        where:
        line << [":compileJava", ":services:payments:test > " + "x" * 51]
        length = "> $line".length()
    }

    @Issue("https://github.com/gradle/gradle/issues/39556")
    def "keeps the first segment and the end of the text of a longer line, from #boundary"() {
        given:
        consoleMetaData.getCols() >> cols

        when:
        def text = statusBarFormatter.format(operationOf(line)).first().text

        then:
        text == expected
        text.length() <= cols - 1

        where:
        boundary                                                   | cols | line                                                                                                                                 | expected
        "a '.' followed by a letter"                               | 80   | ":services:payments:test > Executing test com.example.payments.retry.SlowNetworkRetryIntegrationTest"                                 | "> :services:payments:test > ...payments.retry.SlowNetworkRetryIntegrationTest"
        "a change to upper case"                                   | 90   | ":platforms:core-configuration:configuration-cache:integTest > Executing test org...impl.ConfigurationCacheValueSourceIntegrationTest" | "> :platforms:core-configuration:configuration-cache:integTest > ...SourceIntegrationTest"
        "a '.' followed by a letter, in the smallest room of 10"   | 80   | ":feature:payments:checkout:impl:internal:legacy:compileKotlin > Executing test com.example.FooTest"                                  | "> :feature:payments:checkout:impl:internal:legacy:compileKotlin > ...FooTest"
        "a space followed by a letter"                             | 80   | ":app:compileJava > Resolve dependencies of :app:compileClasspath > guava-33.4.0-jre.jar > 1.2 MB/1.7 MB downloaded"                 | "> :app:compileJava > ...guava-33.4.0-jre.jar > 1.2 MB/1.7 MB downloaded"
        "a space, not a '.' before a digit"                        | 41   | ":app:compileJava > Resolve dependencies of :app:compileClasspath > guava-33.4.0-jre.jar > 1.2 MB/1.7 MB downloaded"                 | "> :app:compileJava > ...MB downloaded"
        "where the width allows, without a boundary"               | 30   | ":work > " + "x" * 60                                                                                                                | "> :work > ..." + "x" * 16
        "where the width allows, on a line one character too long" | 80   | ":services:payments:test > " + "x" * 52                                                                                              | "> :services:payments:test > ..." + "x" * 48
        "where the width allows, when a boundary keeps too little" | 80   | ":platforms:core-configuration:configuration-cache:integTest > Executing test org...impl.ConfigurationCacheValueSourceIntegrationTest" | "> :platforms:core-configuration:configuration-cache:integTest > ...egrationTest"
    }

    @Issue("https://github.com/gradle/gradle/issues/39556")
    def "ends the line with ' > ...' when #reason (#cols columns)"() {
        given:
        consoleMetaData.getCols() >> cols

        when:
        def text = statusBarFormatter.format(operationOf(line)).first().text

        then:
        text == expected
        text.length() <= cols - 1

        where:
        reason                                              | cols | line                                                                                                                  | expected
        "the room for the end is less than 10 characters"   | 80   | ":feature:payments:checkout:impl:internals:legacy:compileKotlin > Executing test com.example.FooTest"                  | "> :feature:payments:checkout:impl:internals:legacy:compileKotlin > ..."
        "the first segment fits, but not with ' > ...'"     | 80   | ":feature:payments:checkout:impl:internal:legacy:bridge:compileTestKotlin > Executing test com.example.FooTest"        | "> :feature:payments:checkout:impl:internal:legacy...:compileTestKotlin > ..."
        "the first segment does not fit"                    | 80   | ":feature:payments:checkout:impl:internal:legacy:bridge:generated:compileDebugUnitTestKotlin > Executing test FooTest" | "> :feature:payments:checkout:impl:internal...:compileDebugUnitTestKotlin > ..."
    }

    @Issue("https://github.com/gradle/gradle/issues/39556")
    def "cuts the first segment before its task name when #reason (#cols columns)"() {
        given:
        consoleMetaData.getCols() >> cols

        when:
        def text = statusBarFormatter.format(operationOf(line)).first().text

        then:
        text == expected
        text.length() <= cols - 1

        where:
        reason                          | cols | line                                                                                                                  | expected
        "the line has one segment"      | 80   | ":feature:payments:checkout:impl:internal:legacy:bridge:generated:compileDebugUnitTestKotlin"                         | "> :feature:payments:checkout:impl:internal:legacy...:compileDebugUnitTestKotlin"
        "the line has one segment"      | 60   | ":feature:payments:checkout:impl:internal:legacy:bridge:generated:compileDebugUnitTestKotlin"                         | "> :feature:payments:checkout...:compileDebugUnitTestKotlin"
    }

    @Issue("https://github.com/gradle/gradle/issues/39556")
    def "cuts the first segment near half of the width when #reason"() {
        given:
        consoleMetaData.getCols() >> cols

        when:
        def text = statusBarFormatter.format(operationOf(line)).first().text

        then:
        text == expected
        text.length() <= cols - 1

        where:
        reason                                           | cols | line                                                                                          | expected
        "its task name is longer than half of the width" | 40   | ":feature:payments:checkout:impl:internal:legacy:bridge:generated:compileDebugUnitTestKotlin" | "> :feature:payments...UnitTestKotlin"
        "half of the width is in a project name"         | 44   | ":feature:payments:checkout:impl:internal:legacy:bridge:generated:compileDebugUnitTestKotlin" | "> :feature:payments...DebugUnitTestKotlin"
        "the console is too narrow for ' > ...'"         | 10   | ":app:test > Executing test FooTest"                                                          | "> ...test"
        "it has no ':'"                                  | 30   | "Building buildSrc and many other things"                                                     | "> Building bui...other things"
        "it has no ':', on 10 columns"                   | 10   | "these are more than 10 characters"                                                           | "> th...rs"
    }

    def "does not split a surrogate pair at the #cut"() {
        given:
        consoleMetaData.getCols() >> 30

        expect:
        statusBarFormatter.format(operationOf(line)).first().text == expected

        where:
        cut                | line                                             | expected
        "start of the end" | ":test > " + "x" * 30 + ROCKET + "y" * 15        | "> :test > ..." + "y" * 15
        "end of the start" | "a" * 11 + ROCKET + "b" * 30                     | "> " + "a" * 11 + "..." + "b" * 13
    }

    def "cuts the line at its end when the width leaves no room for its end"() {
        given:
        consoleMetaData.getCols() >> 6

        expect:
        statusBarFormatter.format(operationOf("these are more than 10 characters")).first().text == "> the"
    }

    def "placeholder is used when message is empty"() {
        given:
        def operation = new ProgressOperation(null, null, new OperationIdentifier(1), null)

        expect:
        statusBarFormatter.format(operation).first().text == "> IDLE"
        statusBarFormatter.format(operation).first().style == StyledTextOutput.Style.Normal
    }

    /**
     * The operation of the last segment of the line, with a parent for each segment before it.
     */
    private static ProgressOperation operationOf(String line) {
        ProgressOperation operation = null
        line.split(" > ").eachWithIndex { String message, int i ->
            operation = new ProgressOperation(message, "VARIANT_CATEGORY", new OperationIdentifier(i + 1), operation)
        }
        operation
    }
}
