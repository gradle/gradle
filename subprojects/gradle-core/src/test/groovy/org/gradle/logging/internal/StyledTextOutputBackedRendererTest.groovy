/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.logging.internal

import org.gradle.logging.StyledTextOutput
import org.gradle.api.logging.LogLevel

class StyledTextOutputBackedRendererTest extends OutputSpecification {
    def rendersOutputEvent() {
        StyledTextOutput output = Mock()
        StyledTextOutputBackedRenderer renderer = new StyledTextOutputBackedRenderer(output)
        RenderableOutputEvent event = Mock()

        when:
        renderer.onOutput(event)

        then:
        1 * event.render(!null) >> { args -> args[0].text('text') }
        2 * output.style(StyledTextOutput.Style.Normal)
        1 * output.text('text')
        0 * output._
    }

    def rendersException() {
        StyledTextOutput output = Mock()
        StyledTextOutputBackedRenderer renderer = new StyledTextOutputBackedRenderer(output)
        RenderableOutputEvent event = Mock()
        def failure = new RuntimeException('broken')

        when:
        renderer.onOutput(event)

        then:
        1 * event.render(!null) >> { args -> args[0].exception(failure) }
        2 * output.style(StyledTextOutput.Style.Normal)
        1 * output.text({it.startsWith('java.lang.RuntimeException: broken')})
        0 * output._
    }

    def rendersOutputEventWhenInDebugMode() {
        TestStyledTextOutput output = new TestStyledTextOutput()
        StyledTextOutputBackedRenderer renderer = new StyledTextOutputBackedRenderer(output)
        RenderableOutputEvent event = event(tenAm, 'message')

        when:
        renderer.onOutput(new LogLevelChangeEvent(LogLevel.DEBUG))
        renderer.onOutput(event)

        then:
        output.value == toNative('10:00:00.000 [INFO] [category] message\n')
    }
    
    def continuesLineWhenPreviousOutputEventDidNotEndWithEOL() {
        TestStyledTextOutput output = new TestStyledTextOutput()
        StyledTextOutputBackedRenderer renderer = new StyledTextOutputBackedRenderer(output)
        RenderableOutputEvent event1 = new StyledTextOutputEvent(tenAm, 'category', LogLevel.INFO, 'message')
        RenderableOutputEvent event2 = new StyledTextOutputEvent(tenAm, 'category', LogLevel.INFO, toNative('\n'))

        when:
        renderer.onOutput(new LogLevelChangeEvent(LogLevel.DEBUG))
        renderer.onOutput(event1)
        renderer.onOutput(event2)

        then:
        output.value == toNative('10:00:00.000 [INFO] [category] message\n')
    }

    def addsEOLWhenPreviousOutputEventDidNotEndWithEOLAndHadDifferentCategory() {
        TestStyledTextOutput output = new TestStyledTextOutput()
        StyledTextOutputBackedRenderer renderer = new StyledTextOutputBackedRenderer(output)
        RenderableOutputEvent event1 = new StyledTextOutputEvent(tenAm, 'category', LogLevel.INFO, 'message')
        RenderableOutputEvent event2 = new StyledTextOutputEvent(tenAm, 'category2', LogLevel.INFO, 'message2')

        when:
        renderer.onOutput(new LogLevelChangeEvent(LogLevel.DEBUG))
        renderer.onOutput(event1)
        renderer.onOutput(event2)

        then:
        output.value == toNative('10:00:00.000 [INFO] [category] message\n10:00:00.000 [INFO] [category2] message2')
    }
}
