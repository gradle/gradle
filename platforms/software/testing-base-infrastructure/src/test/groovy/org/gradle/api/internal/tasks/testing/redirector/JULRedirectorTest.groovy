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

package org.gradle.api.internal.tasks.testing.redirector

import org.apache.commons.io.IOUtils
import org.gradle.internal.SystemProperties
import org.gradle.util.internal.RedirectStdOutAndErr
import org.junit.Rule
import spock.lang.Specification

import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.LogRecord
import java.util.logging.Logger

class JULRedirectorTest extends Specification {
    private static final String EOL = SystemProperties.instance.lineSeparator

    @Rule public final RedirectStdOutAndErr outputs = new RedirectStdOutAndErr()
    private DefaultStandardOutputRedirector redirector = new JULRedirector()
    private final StandardOutputRedirector.OutputListener stdOutListener = Mock()
    private final StandardOutputRedirector.OutputListener stdErrListener = Mock()
    private final Logger logger1 = Logger.getLogger("")
    private final Logger logger2 = Logger.getLogger("hello")
    private final Logger logger3 = Logger.getLogger("hello.world")
    private static String savedJULConfigClassProperty = null;

    public static class DummyFormatter extends Formatter {
        @Override
        String format(LogRecord record) {
            return "[" + record.level + "] " + record.message + EOL
        }
    }

    public static class JULCustomInit {
        JULCustomInit() {
            LogManager.getLogManager().readConfiguration(IOUtils.toInputStream(
                'handlers = java.util.logging.ConsoleHandler' + EOL +
                '.level = FINEST' + EOL +
                'java.util.logging.ConsoleHandler.formatter = ' + DummyFormatter.class.name + EOL +
                'java.util.logging.ConsoleHandler.level = FINEST' + EOL +
                'hello.level = INFO' + EOL +
                'hello.world.level = FINER' + EOL))
        }
    }

    def setup() {
        savedJULConfigClassProperty = System.getProperty("java.util.logging.config.class")
    }

    def cleanup() {
        if (savedJULConfigClassProperty == null) {
            System.clearProperty("java.util.logging.config.class")
        } else {
            System.setProperty("java.util.logging.config.class", savedJULConfigClassProperty)
        }
    }

    def "start and stop do nothing when nothing is redirected"() {
        when:
        redirector.start()
        [Level.SEVERE, Level.WARNING, Level.INFO, Level.CONFIG, Level.FINE, Level.FINER, Level.FINEST].each {
            logger1.log(it, "Test");
            logger2.log(it, "Test");
            logger3.log(it, "Test");
        }
        redirector.stop()

        then:
        0 * _

        System.out == outputs.stdOutPrintStream
        System.err == outputs.stdErrPrintStream
    }

    def "start and stop output with redirection and default logging"() {
        when:
        redirector.redirectStandardOutputTo(stdOutListener)
        redirector.redirectStandardErrorTo(stdErrListener)

        redirector.start()
        [Level.SEVERE, Level.WARNING, Level.INFO, Level.CONFIG, Level.FINE, Level.FINER, Level.FINEST].each {
            logger1.log(it, "Test");
            logger2.log(it, "Test");
            logger3.log(it, "Test");
        }
        redirector.stop()

        then:
        3 * stdErrListener.onOutput("${Level.SEVERE.getLocalizedName()}: Test$EOL")
        3 * stdErrListener.onOutput("${Level.WARNING.getLocalizedName()}: Test$EOL")
        3 * stdErrListener.onOutput("${Level.INFO.getLocalizedName()}: Test$EOL")
        0 * stdErrListener.onOutput("${Level.CONFIG.getLocalizedName()}: Test$EOL")
        0 * stdErrListener.onOutput("${Level.FINE.getLocalizedName()}: Test$EOL")
        0 * stdErrListener.onOutput("${Level.FINER.getLocalizedName()}: Test$EOL")
        0 * stdErrListener.onOutput("${Level.FINEST.getLocalizedName()}: Test$EOL")

        System.out == outputs.stdOutPrintStream
        System.err == outputs.stdErrPrintStream
    }

    def "start and stop output with redirection and custom logging"() {
        System.setProperty("java.util.logging.config.class", JULCustomInit.class.name)

        when:
        redirector.redirectStandardOutputTo(stdOutListener)
        redirector.redirectStandardErrorTo(stdErrListener)

        redirector.start()
        [Level.SEVERE, Level.WARNING, Level.INFO, Level.CONFIG, Level.FINE, Level.FINER, Level.FINEST].each {
            logger1.log(it, "Test");
            logger2.log(it, "Test");
            logger3.log(it, "Test");
        }
        redirector.stop()

        then:
        3 * stdErrListener.onOutput('[SEVERE] Test' + EOL)
        3 * stdErrListener.onOutput('[WARNING] Test' + EOL)
        3 * stdErrListener.onOutput('[INFO] Test' + EOL)
        2 * stdErrListener.onOutput('[CONFIG] Test' + EOL)
        2 * stdErrListener.onOutput('[FINE] Test' + EOL)
        2 * stdErrListener.onOutput('[FINER] Test' + EOL)
        1 * stdErrListener.onOutput('[FINEST] Test' + EOL)
        0 * _

        System.out == outputs.stdOutPrintStream
        System.err == outputs.stdErrPrintStream
    }
}
