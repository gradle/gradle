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
package org.gradle.initialization;

import ch.qos.logback.classic.LoggerContext;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logging;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.api.specs.Spec;
import org.gradle.logging.Console;
import org.gradle.logging.Label;
import org.gradle.logging.TextArea;
import org.gradle.util.ReplaceStdOutAndErr;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileDescriptor;
import java.io.StringWriter;

import static org.gradle.util.Matchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class DefaultLoggingConfigurerTest {
    private final TerminalDetectorStub terminalDetector = new TerminalDetectorStub();
    private final ConsoleStub console = new ConsoleStub();
    private final DefaultLoggingConfigurer configurer = new DefaultLoggingConfigurer(terminalDetector) {
        @Override
        Console createConsole() {
            return console;
        }
    };
    private final StandardOutputListener outputListener = new ListenerImpl();
    private final StandardOutputListener errorListener = new ListenerImpl();
    private final Logger logger = LoggerFactory.getLogger("cat1");
    @Rule
    public final ReplaceStdOutAndErr outputs = new ReplaceStdOutAndErr();

    @Before
    public void setUp() {
        configurer.addStandardOutputListener(outputListener);
        configurer.addStandardErrorListener(errorListener);
    }

    @After
    public void tearDown() {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        lc.reset();
    }

    @Test
    public void canListenOnStdOutput() {
        configurer.configure(LogLevel.INFO);

        logger.debug("debug message");
        logger.info("info message");
        logger.warn("warn message");
        logger.error("error message");

        assertThat(outputListener.toString(), equalTo(String.format("info message%nwarn message%n")));
    }

    @Test
    public void canListenOnStdError() {
        configurer.configure(LogLevel.INFO);

        logger.debug("debug message");
        logger.info("info message");
        logger.warn("warn message");
        logger.error("error message");

        assertThat(errorListener.toString(), equalTo(String.format("error message%n")));
    }

    @Test
    public void filtersProgressAndLowerWhenConfiguredAtQuietLevel() {
        configurer.configure(LogLevel.QUIET);

        logger.info(Logging.QUIET, "quiet message");
        logger.info(Logging.PROGRESS_STARTED, "<start>");
        logger.info(Logging.PROGRESS, "<progress>");
        logger.info(Logging.PROGRESS_COMPLETE, "<complete>");
        logger.info(Logging.LIFECYCLE, "lifecycle message");
        logger.info("info message");
        logger.debug("debug message");

        assertThat(outputListener.toString(), equalTo(String.format("quiet message%n")));
    }

    @Test
    public void filtersInfoAndLowerWhenConfiguredAtLifecycleLevel() {
        configurer.configure(LogLevel.LIFECYCLE);

        logger.info(Logging.QUIET, "quiet message");
        logger.info(Logging.LIFECYCLE, "lifecycle message");
        logger.info("info message");
        logger.debug("debug message");

        assertThat(outputListener.toString(), equalTo(String.format("quiet message%nlifecycle message%n")));
    }

    @Test
    public void filtersDebugAndLowerWhenConfiguredAtInfoLevel() {
        configurer.configure(LogLevel.INFO);

        logger.info(Logging.QUIET, "quiet message");
        logger.info(Logging.LIFECYCLE, "lifecycle message");
        logger.info("info message");
        logger.debug("debug message");

        assertThat(outputListener.toString(), equalTo(String.format(
                "quiet message%nlifecycle message%ninfo message%n")));
    }

    @Test
    public void formatsProgressLogMessages() {
        configurer.configure(LogLevel.LIFECYCLE);

        logger.info(Logging.PROGRESS_STARTED, "<start>");
        logger.info(Logging.PROGRESS, "<tick>");
        logger.info(Logging.PROGRESS, "<tick>");
        logger.info(Logging.PROGRESS_COMPLETE, "<complete>");

        assertThat(outputListener.toString(), equalTo(String.format("<start> .. <complete>%n")));
    }

    @Test
    public void formatsProgressLogMessagesForInfoLevel() {
        configurer.configure(LogLevel.INFO);

        logger.info(Logging.PROGRESS_STARTED, "<start>");
        logger.info(Logging.PROGRESS, "<tick>");
        logger.info(Logging.PROGRESS, "<tick>");
        logger.info(Logging.PROGRESS_COMPLETE, "<complete>");

        assertThat(outputListener.toString(), equalTo(String.format("<start> .. <complete>%n")));
    }

    @Test
    public void formatsProgressLogMessagesForDebugLevel() {
        configurer.configure(LogLevel.DEBUG);

        logger.info(Logging.PROGRESS_STARTED, "<start>");
        logger.info(Logging.PROGRESS, "<tick1>");
        logger.info(Logging.PROGRESS, "<tick2>");
        logger.info(Logging.PROGRESS_COMPLETE, "<complete>");

        assertThat(outputListener.toString(), containsLine(endsWith(String.format("<start>"))));
        assertThat(outputListener.toString(), containsLine(endsWith(String.format("<tick1>"))));
        assertThat(outputListener.toString(), containsLine(endsWith(String.format("<tick2>"))));
        assertThat(outputListener.toString(), containsLine(endsWith(String.format("<complete>"))));
    }

    @Test
    public void routesLoggingMessagesWhenStdOutAndStdErrAreTerminals() {
        terminalDetector.stderrIsTerminal().stdoutIsTerminal();

        configurer.configure(LogLevel.LIFECYCLE);

        logger.info(Logging.LIFECYCLE, "lifecycle");
        logger.error("error");

        assertThat(outputListener.toString(), equalTo(String.format("lifecycle%n")));
        assertThat(errorListener.toString(), equalTo(String.format("error%n")));
        assertThat(console.toString(), equalTo(String.format("lifecycle%nerror%n")));
        assertThat(outputs.getStdOut(), equalTo(""));
        assertThat(outputs.getStdErr(), equalTo(""));
    }

    @Test
    public void routesLoggingMessagesWhenStdOutIsTerminal() {
        terminalDetector.stdoutIsTerminal();

        configurer.configure(LogLevel.LIFECYCLE);

        logger.info(Logging.LIFECYCLE, "lifecycle");
        logger.error("error");

        assertThat(outputListener.toString(), equalTo(String.format("lifecycle%n")));
        assertThat(errorListener.toString(), equalTo(String.format("error%n")));
        assertThat(console.toString(), equalTo(String.format("lifecycle%n")));
        assertThat(outputs.getStdOut(), equalTo(""));
        assertThat(outputs.getStdErr(), equalTo(String.format("error%n")));
    }

    @Test
    public void routesLoggingMessagesWhenStdErrIsTerminal() {
        terminalDetector.stderrIsTerminal();

        configurer.configure(LogLevel.LIFECYCLE);

        logger.info(Logging.LIFECYCLE, "lifecycle");
        logger.error("error");

        assertThat(outputListener.toString(), equalTo(String.format("lifecycle%n")));
        assertThat(errorListener.toString(), equalTo(String.format("error%n")));
        assertThat(console.toString(), equalTo(String.format("error%n")));
        assertThat(outputs.getStdOut(), equalTo(String.format("lifecycle%n")));
        assertThat(outputs.getStdErr(), equalTo(""));
    }

    @Test
    public void routesLoggingMessagesWhenNeitherStdOutAndStdErrAreTerminals() {
        configurer.configure(LogLevel.LIFECYCLE);

        logger.info(Logging.LIFECYCLE, "lifecycle");
        logger.error("error");

        assertThat(outputListener.toString(), equalTo(String.format("lifecycle%n")));
        assertThat(errorListener.toString(), equalTo(String.format("error%n")));
        assertThat(console.toString(), equalTo(""));
        assertThat(outputs.getStdOut(), equalTo(String.format("lifecycle%n")));
        assertThat(outputs.getStdErr(), equalTo(String.format("error%n")));
    }

    @Test
    public void doesNotUseTheConsoleWhenSetToDebugLevel() {
        terminalDetector.stderrIsTerminal().stdoutIsTerminal();

        configurer.configure(LogLevel.DEBUG);

        logger.info(Logging.LIFECYCLE, "lifecycle");
        logger.error("error");

        assertThat(outputListener.toString(), containsLine(endsWith("lifecycle")));
        assertThat(errorListener.toString(), containsLine(endsWith("error")));
        assertThat(console.toString(), equalTo(""));
        assertThat(outputs.getStdOut(), containsLine(endsWith("lifecycle")));
        assertThat(outputs.getStdErr(), containsLine(endsWith("error")));
    }

    private static class ListenerImpl implements StandardOutputListener {
        private final StringWriter writer = new StringWriter();

        @Override
        public String toString() {
            return writer.toString();
        }

        public void onOutput(CharSequence output) {
            writer.append(output);
        }
    }

    private static class TerminalDetectorStub implements Spec<FileDescriptor> {
        boolean stdoutIsTerminal;
        boolean stderrIsTerminal;

        public boolean isSatisfiedBy(FileDescriptor element) {
            if (element == FileDescriptor.out) {
                return stdoutIsTerminal;
            }
            if (element == FileDescriptor.err) {
                return stderrIsTerminal;
            }
            return false;
        }

        public TerminalDetectorStub stderrIsTerminal() {
            stderrIsTerminal = true;
            return this;
        }

        public TerminalDetectorStub stdoutIsTerminal() {
            stdoutIsTerminal = true;
            return this;
        }
    }

    private static class ConsoleStub implements Console, TextArea {
        private final StringWriter writer = new StringWriter();

        @Override
        public String toString() {
            return writer.toString();
        }

        public Label addStatusBar() {
            throw new UnsupportedOperationException();
        }

        public TextArea getMainArea() {
            return this;
        }

        public void append(CharSequence text) {
            writer.append(text);
        }
    }
}
