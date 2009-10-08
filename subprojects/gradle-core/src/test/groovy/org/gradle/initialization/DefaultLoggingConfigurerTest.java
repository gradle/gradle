/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.api.logging.Logging;
import org.gradle.listener.ListenerManager;
import org.gradle.listener.DefaultListenerManager;
import static org.hamcrest.Matchers.*;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;

public class DefaultLoggingConfigurerTest {
    private final DefaultLoggingConfigurer configurer = new DefaultLoggingConfigurer();
    private final StandardOutputListener outputListener = new ListenerImpl();
    private final StandardOutputListener errorListener = new ListenerImpl();
    private final Logger logger = LoggerFactory.getLogger("cat1");

    @Before
    public void setUp() {
        ListenerManager listenerManager = new DefaultListenerManager();
        configurer.initialize(listenerManager);
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
        System.setProperty("keykey", "value");
        configurer.configure(LogLevel.INFO);

        logger.debug("debug message");
        logger.info("info message");
        logger.warn("warn message");
        logger.error("error message");

        assertThat(outputListener.toString(), equalTo(String.format("info message%nwarn message%n")));
    }

    @Test
    public void canListenOnStdError() {
        System.out.println("System.getProperty(\"keykey\"); = " + System.getProperty("keykey"));
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
        logger.info(Logging.PROGRESS, "<progress message>");
        logger.info(Logging.LIFECYCLE, "lifecycle message");
        logger.info("info message");
        logger.debug("debug message");

        assertThat(outputListener.toString(), equalTo(String.format("quiet message%n")));
    }
    
    @Test
    public void filtersInfoAndLowerWhenConfiguredAtLifecycleLevel() {
        configurer.configure(LogLevel.LIFECYCLE);

        logger.info(Logging.QUIET, "quiet message");
        logger.info(Logging.PROGRESS, "<progress message>");
        logger.info(Logging.LIFECYCLE, "lifecycle message");
        logger.info("info message");
        logger.debug("debug message");

        assertThat(outputListener.toString(), equalTo(String.format("quiet message%n<progress message>lifecycle message%n")));
    }

    @Test
    public void filtersDebugAndLowerWhenConfiguredAtInfoLevel() {
        configurer.configure(LogLevel.INFO);

        logger.info(Logging.QUIET, "quiet message");
        logger.info(Logging.PROGRESS, "<progress message>");
        logger.info(Logging.LIFECYCLE, "lifecycle message");
        logger.info("info message");
        logger.debug("debug message");

        assertThat(outputListener.toString(), equalTo(String.format("quiet message%n<progress message>lifecycle message%ninfo message%n")));
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
