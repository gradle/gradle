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
package org.gradle.api.internal.tasks.testing.redirector

import org.gradle.internal.SystemProperties
import org.gradle.util.internal.RedirectStdOutAndErr
import org.junit.Rule
import spock.lang.Specification

class DefaultStandardOutputRedirectorTest extends Specification {
    private static final String EOL = SystemProperties.instance.lineSeparator

    @Rule public final RedirectStdOutAndErr outputs = new RedirectStdOutAndErr()
    private final DefaultStandardOutputRedirector redirector = new DefaultStandardOutputRedirector()
    private final StandardOutputRedirector.OutputListener stdOutListener = Mock()
    private final StandardOutputRedirector.OutputListener stdErrListener = Mock()

    def startAndStopDoesNothingWhenNothingRedirected() {
        when:
        redirector.start()
        System.out.println('this is stdout')
        System.err.println('this is stderr')
        redirector.stop()

        then:
        System.out == outputs.stdOutPrintStream
        System.err == outputs.stdErrPrintStream
    }

    def startAndStopRedirectsStdOut() {
        when:
        redirector.redirectStandardOutputTo(stdOutListener)
        redirector.start()
        System.out.println('this is stdout')
        System.err.println('this is stderr')
        redirector.stop()

        then:
        1 * stdOutListener.onOutput('this is stdout' + EOL)
        0 * stdOutListener._
        System.out == outputs.stdOutPrintStream
        System.err == outputs.stdErrPrintStream
    }

    def startAndStopRedirectsStdErr() {
        when:
        redirector.redirectStandardErrorTo(stdErrListener)
        redirector.start()
        System.out.println('this is stdout')
        System.err.println('this is stderr')
        redirector.stop()

        then:
        1 * stdErrListener.onOutput('this is stderr' + EOL)
        0 * stdErrListener._
        System.out == outputs.stdOutPrintStream
        System.err == outputs.stdErrPrintStream
    }

    def receivesPartialOutput() {
        when:
        redirector.redirectStandardOutputTo(stdOutListener)
        redirector.start()
        System.out.print('this is stdout')
        redirector.stop()

        then:
        1 * stdOutListener.onOutput('this is stdout')
        System.out == outputs.stdOutPrintStream
        System.err == outputs.stdErrPrintStream
    }

    def receivesPartialOutputOnFlush() {
        when:
        redirector.redirectStandardOutputTo(stdOutListener)
        redirector.start()
        System.out.print('this is stdout')
        System.out.flush()

        then:
        1 * stdOutListener.onOutput('this is stdout')
    }

    def canRedirectMultipleTimes() {
        when:
        redirector.redirectStandardErrorTo(stdErrListener)
        redirector.start()
        redirector.stop()
        redirector.redirectStandardOutputTo(stdOutListener)
        redirector.start()
        System.out.println('this is stdout')
        System.err.println('this is stderr')
        redirector.stop()

        then:
        1 * stdOutListener.onOutput('this is stdout' + EOL)
        0 * stdOutListener._
        0 * stdErrListener._
        System.out == outputs.stdOutPrintStream
        System.err == outputs.stdErrPrintStream
    }
}
