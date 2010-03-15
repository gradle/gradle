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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;

import static org.gradle.util.Matchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class DefaultLoggingConfigurerTest {
    private final DefaultLoggingConfigurer configurer = new DefaultLoggingConfigurer();
    private final StandardOutputListener outputListener = new ListenerImpl();
    private final StandardOutputListener errorListener = new ListenerImpl();
    private final Logger logger = LoggerFactory.getLogger("cat1");

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

        assertThat(outputListener.toString(), equalTo(String.format("quiet message%nlifecycle message%ninfo message%n")));
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
    public void formatsProgressLogMessagesWithoutIntermediateProgressEvents() {
        configurer.configure(LogLevel.LIFECYCLE);

        logger.info(Logging.PROGRESS_STARTED, "<start>");
        logger.info(Logging.PROGRESS_COMPLETE, "<complete>");

        assertThat(outputListener.toString(), equalTo(String.format("<start> <complete>%n")));
    }

    @Test
    public void formatsProgressLogMessagesWithEmptyCompleteMessage() {
        configurer.configure(LogLevel.LIFECYCLE);

        logger.info(Logging.PROGRESS_STARTED, "<start>");
        logger.info(Logging.PROGRESS_COMPLETE, "");

        assertThat(outputListener.toString(), equalTo(String.format("<start>%n")));
    }

    @Test
    public void addsMissingEndOfLineWhenProgressAndOtherLogMessagesAreInterleaved() {
        configurer.configure(LogLevel.LIFECYCLE);

        logger.info(Logging.PROGRESS_STARTED, "<start>");
        logger.info(Logging.PROGRESS, "<tick>");
        logger.info(Logging.LIFECYCLE, "<message1>");
        logger.info(Logging.LIFECYCLE, "<message2>");
        logger.info(Logging.PROGRESS, "<tick>");
        logger.info(Logging.PROGRESS_COMPLETE, "<complete>");
        logger.info(Logging.LIFECYCLE, "<message>");

        assertThat(outputListener.toString(), equalTo(String.format("<start> .%n<message1>%n<message2>%n. <complete>%n<message>%n")));
    }

    @Test
    public void addsMissingEndOfLineWhenProgressAndOtherLogMessagesAreInterleavedAndCompleteMessageIsEmpty() {
        configurer.configure(LogLevel.LIFECYCLE);

        logger.info(Logging.PROGRESS_STARTED, "<start>");
        logger.info(Logging.LIFECYCLE, "<message>");
        logger.info(Logging.PROGRESS_COMPLETE, "");

        assertThat(outputListener.toString(), equalTo(String.format("<start>%n<message>%n")));
    }

    @Test
    public void addsMissingEndOfLineWhenProgressMessagesAreInterleaved() {
        configurer.configure(LogLevel.LIFECYCLE);

        logger.info(Logging.PROGRESS_STARTED, "<start1>");
        logger.info(Logging.PROGRESS_STARTED, "<start2>");
        logger.info(Logging.PROGRESS, "<tick2>");
        logger.info(Logging.PROGRESS_COMPLETE, "<complete2>");
        logger.info(Logging.PROGRESS, "<tick1>");
        logger.info(Logging.PROGRESS_COMPLETE, "<complete1>");

        assertThat(outputListener.toString(), equalTo(String.format("<start1>%n<start2> . <complete2>%n. <complete1>%n")));
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
}
