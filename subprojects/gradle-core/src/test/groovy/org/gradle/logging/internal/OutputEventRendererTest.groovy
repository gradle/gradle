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

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.StandardOutputListener
import org.gradle.util.RedirectStdOutAndErr
import org.junit.Rule

class OutputEventRendererTest extends OutputSpecification {
    @Rule public final RedirectStdOutAndErr outputs = new RedirectStdOutAndErr()
    private final ConsoleStub console = new ConsoleStub()
    private OutputEventRenderer renderer

    def setup() {
        renderer = new OutputEventRenderer()
        renderer.addStandardOutput(outputs.stdOutPrintStream)
        renderer.addStandardError(outputs.stdErrPrintStream)
        renderer.configure(LogLevel.INFO)
    }

    def rendersLogEventsToStdOut() {
        when:
        renderer.onOutput(event('message', LogLevel.INFO))

        then:
        outputs.stdOut.readLines() == ['message']
        outputs.stdErr == ''
    }

    def rendersErrorLogEventsToStdErr() {
        when:
        renderer.onOutput(event('message', LogLevel.ERROR))

        then:
        outputs.stdOut == ''
        outputs.stdErr.readLines() == ['message']
    }

    def rendersLogEventsWhenLogLevelIsDebug() {
        when:
        renderer.configure(LogLevel.DEBUG)
        renderer.onOutput(event(tenAm, 'message', LogLevel.INFO))

        then:
        outputs.stdOut.readLines() == ['10:00:00.000 [INFO] [category] message']
        outputs.stdErr == ''
    }

    def rendersLogEventsToStdOutListener() {
        def listener = new TestListener()

        when:
        renderer.addStandardOutputListener(listener)
        renderer.onOutput(event('info', LogLevel.INFO))
        renderer.onOutput(event('error', LogLevel.ERROR))

        then:
        listener.value.readLines() == ['info']
    }

    def doesNotRenderLogEventsToRemovedStdOutListener() {
        def listener = new TestListener()

        when:
        renderer.addStandardOutputListener(listener)
        renderer.removeStandardOutputListener(listener)
        renderer.onOutput(event('info', LogLevel.INFO))
        renderer.onOutput(event('error', LogLevel.ERROR))

        then:
        listener.value == ''
    }

    def rendersLogEventsToStdOutListenerWhenLogLevelIsDebug() {
        def listener = new TestListener()

        when:
        renderer.configure(LogLevel.DEBUG)
        renderer.addStandardOutputListener(listener)
        renderer.onOutput(event(tenAm, 'message', LogLevel.INFO))

        then:
        listener.value.readLines() == ['10:00:00.000 [INFO] [category] message']
    }
    
    def rendersErrorLogEventsToStdErrListener() {
        def listener = new TestListener()

        when:
        renderer.addStandardErrorListener(listener)
        renderer.onOutput(event('info', LogLevel.INFO))
        renderer.onOutput(event('error', LogLevel.ERROR))

        then:
        listener.value.readLines() == ['error']
    }

    def doesNotRenderLogEventsToRemovedStdErrListener() {
        def listener = new TestListener()

        when:
        renderer.addStandardErrorListener(listener)
        renderer.removeStandardErrorListener(listener)
        renderer.onOutput(event('info', LogLevel.INFO))
        renderer.onOutput(event('error', LogLevel.ERROR))

        then:
        listener.value == ''
    }

    def rendersLogEventsToStdErrListenerWhenLogLevelIsDebug() {
        def listener = new TestListener()

        when:
        renderer.configure(LogLevel.DEBUG)
        renderer.addStandardErrorListener(listener)
        renderer.onOutput(event(tenAm, 'message', LogLevel.ERROR))

        then:
        listener.value.readLines() == ['10:00:00.000 [ERROR] [category] message']
    }

    def rendersProgressEvents() {
        when:
        renderer.onOutput(start('description'))
        renderer.onOutput(complete('status'))

        then:
        outputs.stdOut.readLines() == ['description status']
        outputs.stdErr == ''
    }

    def doesNotRendersProgressEventsForLogLevelQuiet() {
        when:
        renderer.configure(LogLevel.QUIET)
        renderer.onOutput(start('description'))
        renderer.onOutput(complete('status'))

        then:
        outputs.stdOut == ''
        outputs.stdErr == ''
    }

    def rendersLogEventsWhenStdOutAndStdErrAreTerminal() {
        renderer.addConsole(console, true, true)

        when:
        renderer.onOutput(start('description'))
        renderer.onOutput(event('info', LogLevel.INFO))
        renderer.onOutput(event('error', LogLevel.ERROR))
        renderer.onOutput(complete('status'))

        then:
        console.value.readLines() == ['description', 'info', 'error', 'description status']
    }

    def rendersLogEventsWhenOnlyStdOutIsTerminal() {
        renderer.addConsole(console, true, false)

        when:
        renderer.onOutput(start('description'))
        renderer.onOutput(event('info', LogLevel.INFO))
        renderer.onOutput(event('error', LogLevel.ERROR))
        renderer.onOutput(complete('status'))

        then:
        console.value.readLines() == ['description', 'info', 'description status']
    }

    def rendersLogEventsWhenOnlyStdErrIsTerminal() {
        renderer.addConsole(console, false, true)

        when:
        renderer.onOutput(start('description'))
        renderer.onOutput(event('info', LogLevel.INFO))
        renderer.onOutput(event('error', LogLevel.ERROR))
        renderer.onOutput(complete('status'))

        then:
        console.value.readLines() == ['error']
    }
}

private static class ConsoleStub implements Console, TextArea {
    private final StringWriter writer = new StringWriter();

    public String getValue() {
        return writer.toString();
    }

    public Label addStatusBar() {
        return new Label() {
            void close() {
            }

            void setText(String text) {
            }
        }
    }

    public TextArea getMainArea() {
        return this;
    }

    public void append(CharSequence text) {
        writer.append(text);
    }
}

private static class TestListener implements StandardOutputListener {
    private final StringWriter writer = new StringWriter();

    def getValue() {
        return writer.toString()
    }

    public void onOutput(CharSequence output) {
        writer.append(output);
    }
}

