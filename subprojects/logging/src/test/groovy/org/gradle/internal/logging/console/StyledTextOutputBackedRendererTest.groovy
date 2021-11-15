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

import org.gradle.api.logging.LogLevel
import org.gradle.internal.SystemProperties
import org.gradle.internal.logging.OutputSpecification
import org.gradle.internal.logging.events.LogLevelChangeEvent
import org.gradle.internal.logging.events.RenderableOutputEvent
import org.gradle.internal.logging.events.StyledTextOutputEvent
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.TestLineChoppingStyledTextOutput
import org.gradle.internal.logging.text.TestStyledTextOutput
import spock.lang.Issue

class StyledTextOutputBackedRendererTest extends OutputSpecification {
    def rendersOutputEvent() {
        StyledTextOutput output = new TestStyledTextOutput()
        StyledTextOutputBackedRenderer renderer = new StyledTextOutputBackedRenderer(output)
        RenderableOutputEvent event = Mock()

        when:
        renderer.onOutput(event)

        then:
        1 * event.render(!null) >> { args -> args[0].text('text') }
        output.value == 'text'
    }

    def rendersErrorOutputEvent() {
        StyledTextOutput output = new TestStyledTextOutput()
        StyledTextOutputBackedRenderer renderer = new StyledTextOutputBackedRenderer(output)
        RenderableOutputEvent event = Mock()

        when:
        renderer.onOutput(event)

        then:
        1 * event.logLevel >> LogLevel.ERROR
        1 * event.render(!null) >> { args -> args[0].text('text') }
        output.value == '{error}text{normal}'
    }

    def rendersException() {
        StyledTextOutput output = new TestStyledTextOutput()
        StyledTextOutputBackedRenderer renderer = new StyledTextOutputBackedRenderer(output)
        RenderableOutputEvent event = Mock()
        def failure = new RuntimeException('broken')

        when:
        renderer.onOutput(event)

        then:
        1 * event.render(!null) >> { args -> args[0].exception(failure) }
        output.value == 'java.lang.RuntimeException: broken\n{stacktrace}\n'
    }

    def rendersOutputEventWhenInDebugMode() {
        StyledTextOutput output = new TestStyledTextOutput()
        StyledTextOutputBackedRenderer renderer = new StyledTextOutputBackedRenderer(output)
        RenderableOutputEvent event = event(tenAm, 'message')

        when:
        renderer.onOutput(new LogLevelChangeEvent(LogLevel.DEBUG))
        renderer.onOutput(event)

        then:
        output.value == "${tenAmFormatted} [INFO] [category] message\n"
    }

    def continuesLineWhenPreviousOutputEventDidNotEndWithEOL() {
        TestStyledTextOutput output = new TestStyledTextOutput()
        StyledTextOutputBackedRenderer renderer = new StyledTextOutputBackedRenderer(output)
        RenderableOutputEvent event1 = new StyledTextOutputEvent(tenAm, 'category', LogLevel.INFO, null, 'message')
        RenderableOutputEvent event2 = new StyledTextOutputEvent(tenAm, 'category', LogLevel.INFO, null, toNative('\n'))

        when:
        renderer.onOutput(new LogLevelChangeEvent(LogLevel.DEBUG))
        renderer.onOutput(event1)
        renderer.onOutput(event2)

        then:
        output.value == "${tenAmFormatted} [INFO] [category] message\n"
    }

    def addsEOLWhenPreviousOutputEventDidNotEndWithEOLAndHadDifferentCategory() {
        TestStyledTextOutput output = new TestStyledTextOutput()
        StyledTextOutputBackedRenderer renderer = new StyledTextOutputBackedRenderer(output)
        RenderableOutputEvent event1 = new StyledTextOutputEvent(tenAm, 'category', LogLevel.INFO, null, 'message')
        RenderableOutputEvent event2 = new StyledTextOutputEvent(tenAm, 'category2', LogLevel.INFO, null, 'message2')

        when:
        renderer.onOutput(new LogLevelChangeEvent(LogLevel.DEBUG))
        renderer.onOutput(event1)
        renderer.onOutput(event2)

        then:
        output.value == "${tenAmFormatted} [INFO] [category] message\n${tenAmFormatted} [INFO] [category2] message2"
    }

    @Issue("https://github.com/gradle/gradle/issues/1566")
    def renderMultiNonNativeNewLineTextCorrectly() {
        StyledTextOutput output = new TestLineChoppingStyledTextOutput()
        StyledTextOutputBackedRenderer renderer = new StyledTextOutputBackedRenderer(output)
        RenderableOutputEvent event = Mock()
        String headerLine = "###"
        String firstLine = "# This is the first long line!"
        String secondLine = "# This is the second long line!"
        String thirdLine = "# This is the third long line!"
        String fourthLine = "# This is the fourth long line!"
        String fifthLine = "# This is the fifth long line!"
        String footerLine = "###"

        when:
        renderer.onOutput(event)

        then:
        1 * event.render(!null) >> { args -> args[0].text("$headerLine$eol$firstLine$eol$secondLine$eol$thirdLine$eol$fourthLine$eol$fifthLine$eol$footerLine") }
        output.value == "$headerLine\n$firstLine\n$secondLine\n$thirdLine\n$fourthLine\n$fifthLine\n$footerLine"

        where:
        eol << [SystemProperties.instance.lineSeparator, "\n", "\r\n"]
    }
}
