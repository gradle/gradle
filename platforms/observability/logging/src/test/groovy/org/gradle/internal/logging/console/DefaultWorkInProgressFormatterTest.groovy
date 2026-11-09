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

import org.gradle.internal.logging.events.StyledTextOutputEvent.Span
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.nativeintegration.console.ConsoleMetaData
import org.gradle.internal.operations.OperationIdentifier
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.internal.logging.text.StyledTextOutput.Style.Header
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Normal

class DefaultWorkInProgressFormatterTest extends Specification {
    // Two UTF-16 code units
    private static final String ROCKET = "\uD83D\uDE80"

    def consoleMetaData = Mock(ConsoleMetaData)
    @Subject statusBarFormatter = new DefaultWorkInProgressFormatter(consoleMetaData)

    def "formats operations"() {
        given:
        def op1 = new ProgressOperation("STATUS_1", "VARIANT_CATEGORY", new OperationIdentifier(1), null, 0)
        def op2 = new ProgressOperation(null,  null, new OperationIdentifier(2), op1, 0)
        def op3 = new ProgressOperation("STATUS_2", "VARIANT_CATEGORY", new OperationIdentifier(3), op2, 0)

        expect:
        statusBarFormatter.format(op3, 0).first().text == "> STATUS_1 > STATUS_2"
        statusBarFormatter.format(op2, 0).first().text == "> STATUS_1"
        statusBarFormatter.format(op1, 0).first().text == "> STATUS_1"
    }

    @Issue("https://github.com/gradle/gradle/issues/39544")
    def "leaves out the message of the single work action of an operation, as its line is not shown"() {
        given:
        def task = new ProgressOperation(":pmdMain", "VARIANT_CATEGORY", new OperationIdentifier(1), null, 0)
        def workAction = new ProgressOperation("Executing work action PmdInvoker", "VARIANT_CATEGORY", new OperationIdentifier(2), task, 0, true)
        task.addChild(workAction)
        def resolution = new ProgressOperation("Resolve dependencies", "VARIANT_CATEGORY", new OperationIdentifier(3), workAction, 0)
        workAction.addChild(resolution)

        expect:
        statusBarFormatter.format(resolution, 0).first().text == "> :pmdMain > Resolve dependencies"

        when:
        task.addChild(new ProgressOperation("Executing work action PmdInvoker", "VARIANT_CATEGORY", new OperationIdentifier(4), task, 0, true))

        then:
        statusBarFormatter.format(resolution, 0).first().text == "> :pmdMain > Executing work action PmdInvoker > Resolve dependencies"
    }

    @Issue("https://github.com/gradle/gradle/issues/39544")
    def "adds the number of completed work actions submitted by the single work action of an operation to the line of this operation"() {
        given:
        def task = new ProgressOperation(":work", "VARIANT_CATEGORY", new OperationIdentifier(1), null, 0)
        def workAction = new ProgressOperation("Executing work action A", "VARIANT_CATEGORY", new OperationIdentifier(2), task, 0, true)
        task.addChild(workAction)
        def submitted = new ProgressOperation("Executing work action B", "VARIANT_CATEGORY", new OperationIdentifier(3), workAction, 0, true)
        workAction.addChild(submitted)
        workAction.addChild(new ProgressOperation("Executing work action C", "VARIANT_CATEGORY", new OperationIdentifier(4), workAction, 0, true))

        expect:
        statusBarFormatter.format(task, 0).first().text == "> :work > 0 work actions completed"
        statusBarFormatter.format(submitted, 0).first().text == "> :work > Executing work action B"

        when:
        workAction.workActionCompleted()

        then:
        statusBarFormatter.format(task, 0).first().text == "> :work > 1 work action completed"
    }

    @Issue("https://github.com/gradle/gradle/issues/39544")
    def "adds the number of completed work actions to the line of their parent, from its second work action"() {
        given:
        def task = new ProgressOperation(":work", "VARIANT_CATEGORY", new OperationIdentifier(1), null, 0)
        def first = new ProgressOperation("Executing work action A", "VARIANT_CATEGORY", new OperationIdentifier(2), task, 0, true)
        task.addChild(first)

        expect:
        statusBarFormatter.format(task, 0).first().text == "> :work"

        when:
        task.addChild(new ProgressOperation("Executing work action B", "VARIANT_CATEGORY", new OperationIdentifier(3), task, 0, true))

        then:
        statusBarFormatter.format(task, 0).first().text == "> :work > 0 work actions completed"
        statusBarFormatter.format(first, 0).first().text == "> :work > Executing work action A"

        when:
        task.workActionCompleted()

        then:
        statusBarFormatter.format(task, 0).first().text == "> :work > 1 work action completed"

        when:
        task.workActionCompleted()

        then:
        statusBarFormatter.format(task, 0).first().text == "> :work > 2 work actions completed"
    }

    def "puts the owner of an operation before its message (#owner)"() {
        given:
        def operation = new ProgressOperation("Building task graph", "VARIANT_CATEGORY", new OperationIdentifier(1), null, 0, false, owner)

        expect:
        statusBarFormatter.format(operation, 0).first().text == expected

        where:
        owner      | expected
        ":lib"     | "> :lib > Building task graph"
        ":lib:app" | "> :lib:app > Building task graph"
        null       | "> Building task graph"
    }

    def "puts the owner of an operation after the message of its parent"() {
        given:
        def settings = new ProgressOperation("Evaluating settings", "VARIANT_CATEGORY", new OperationIdentifier(1), null, 0, false, null)
        def listeners = new ProgressOperation("Running projects loaded listeners", "VARIANT_CATEGORY", new OperationIdentifier(2), settings, 0, false, ":plugins")

        expect:
        statusBarFormatter.format(listeners, 0).first().text == "> Evaluating settings > :plugins > Running projects loaded listeners"
    }

    def "does not show the owner of an operation without a message"() {
        given:
        def settings = new ProgressOperation("Evaluating settings", "VARIANT_CATEGORY", new OperationIdentifier(1), null, 0)
        def withoutMessage = new ProgressOperation(null, "VARIANT_CATEGORY", new OperationIdentifier(2), settings, 0, false, ":lib")
        def alone = new ProgressOperation(null, "VARIANT_CATEGORY", new OperationIdentifier(3), null, 0, false, ":lib")

        expect:
        statusBarFormatter.format(withoutMessage, 0).first().text == "> Evaluating settings"
        statusBarFormatter.format(alone, 0).first().text == "> IDLE"
    }

    def "shows the time after the message of an operation with an owner"() {
        given:
        def operation = new ProgressOperation("Running projects evaluated listeners", "VARIANT_CATEGORY", new OperationIdentifier(1), null, 0, false, ":lib")

        expect:
        statusBarFormatter.format(operation, 12_000) == [new Span(Header, "> :lib > Running projects evaluated listeners"), new Span(Normal, "  12s")]
    }

    def "cuts a long line with an owner as the same line with the owner in its message (#cols columns)"() {
        given:
        consoleMetaData.getCols() >> cols
        def message = "Generating dependency accessors for 'aCatalogWithAVeryLongNameThatDoesNotFitOnTheLine'"
        def withPath = new ProgressOperation(message, "VARIANT_CATEGORY", new OperationIdentifier(1), null, 0, false, ":lib")

        expect:
        statusBarFormatter.format(withPath, 25_000) == statusBarFormatter.format(operationOf(":lib > " + message), 25_000)

        where:
        cols << [80, 40, 12]
    }

    def "keeps a line of #length characters, which fits in one less than the console width"() {
        given:
        consoleMetaData.getCols() >> 80

        expect:
        statusBarFormatter.format(operationOf(line), 0).first().text == "> $line"

        where:
        line << [":compileJava", ":services:payments:test > " + "x" * 51]
        length = "> $line".length()
    }

    @Issue("https://github.com/gradle/gradle/issues/39556")
    def "keeps the first segment and the end of the text of a longer line, from #boundary"() {
        given:
        consoleMetaData.getCols() >> cols

        when:
        def text = statusBarFormatter.format(operationOf(line), 0).first().text

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
        def text = statusBarFormatter.format(operationOf(line), 0).first().text

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
        def text = statusBarFormatter.format(operationOf(line), 0).first().text

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
        def text = statusBarFormatter.format(operationOf(line), 0).first().text

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
        statusBarFormatter.format(operationOf(line), 0).first().text == expected

        where:
        cut                | line                                             | expected
        "start of the end" | ":test > " + "x" * 30 + ROCKET + "y" * 15        | "> :test > ..." + "y" * 15
        "end of the start" | "a" * 11 + ROCKET + "b" * 30                     | "> " + "a" * 11 + "..." + "b" * 13
    }

    def "cuts the line at its end when the width leaves no room for its end"() {
        given:
        consoleMetaData.getCols() >> 6

        expect:
        statusBarFormatter.format(operationOf("these are more than 10 characters"), 0).first().text == "> the"
    }

    @Issue("https://github.com/gradle/gradle/issues/39559")
    def "shows the time after the line once the work has run for 10 s (#elapsed ms)"() {
        given:
        def operation = new ProgressOperation(":compileJava", "VARIANT_CATEGORY", new OperationIdentifier(1), null, 1_000)

        expect:
        statusBarFormatter.format(operation, 1_000 + elapsed) == expected

        where:
        elapsed | expected
        9_999   | [new Span(Header, "> :compileJava")]
        10_000  | [new Span(Header, "> :compileJava"), new Span(Normal, "  10s")]
        72_000  | [new Span(Header, "> :compileJava"), new Span(Normal, "  1m 12s")]
    }

    @Issue("https://github.com/gradle/gradle/issues/39559")
    def "shows the time of the deepest operation with a message"() {
        given:
        def task = new ProgressOperation(":test", "VARIANT_CATEGORY", new OperationIdentifier(1), null, 0)
        def testClass = new ProgressOperation("Executing test FooTest", "VARIANT_CATEGORY", new OperationIdentifier(2), task, 50_000)
        def withoutMessage = new ProgressOperation(null, "VARIANT_CATEGORY", new OperationIdentifier(3), testClass, 71_000)

        expect:
        text(statusBarFormatter.format(testClass, 72_000)) == "> :test > Executing test FooTest  22s"
        text(statusBarFormatter.format(withoutMessage, 72_000)) == "> :test > Executing test FooTest  22s"
        text(statusBarFormatter.format(task, 72_000)) == "> :test  1m 12s"
    }

    @Issue("https://github.com/gradle/gradle/issues/39559")
    def "shows the time only next to a part of the message of its operation, when #reason (#cols columns)"() {
        given:
        consoleMetaData.getCols() >> cols

        when:
        def text = text(statusBarFormatter.format(operationOf(line), 25_000))

        then:
        text == expected
        text.length() <= cols - 1

        where:
        reason                                      | cols | line                                                                                                                                 | expected
        "the end of a long line is kept"            | 80   | ":services:payments:test > Executing test com.example.payments.retry.SlowNetworkRetryIntegrationTest"                                 | "> :services:payments:test > ...retry.SlowNetworkRetryIntegrationTest  25s"
        "the end is only kept without the time"     | 80   | ":platforms:core-configuration:configuration-cache:integTest > Executing test org...impl.ConfigurationCacheValueSourceIntegrationTest" | "> :platforms:core-configuration:configuration-cache:integTest > ...egrationTest"
        "the end is dropped"                        | 80   | ":platforms:core-configuration:configuration-cache:configCacheIntegTest > Executing test com.example.FooTest"                         | "> :platforms:core-configuration:configuration-cache:configCacheIntegTest > ..."
        "one character of the message is kept"      | 9    | ":compileJava"                                                                                                                       | "> :  25s"
        "no character of the message is kept"       | 8    | ":compileJava"                                                                                                                       | "> ...va"
        "the first character does not fit"          | 9    | ROCKET + "compileJava"                                                                                                               | "> " + ROCKET + "...a"
        "only an ellipsis of the message fits"      | 12   | ROCKET + "compileJava" + ROCKET                                                                                                      | "> " + ROCKET + "c...a" + ROCKET
    }

    def "placeholder is used when message is empty"() {
        given:
        def operation = new ProgressOperation(null, null, new OperationIdentifier(1), null, 0)

        expect:
        statusBarFormatter.format(operation, 0).first().text == "> IDLE"
        statusBarFormatter.format(operation, 0).first().style == StyledTextOutput.Style.Normal
    }

    /**
     * The operation of the last segment of the line, with a parent for each segment before it.
     */
    private static ProgressOperation operationOf(String line) {
        ProgressOperation operation = null
        line.split(" > ").eachWithIndex { String message, int i ->
            operation = new ProgressOperation(message, "VARIANT_CATEGORY", new OperationIdentifier(i + 1), operation, 0)
        }
        operation
    }

    private static String text(List<Span> spans) {
        spans*.text.join("")
    }
}
