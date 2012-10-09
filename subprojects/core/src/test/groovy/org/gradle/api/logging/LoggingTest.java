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

package org.gradle.api.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import org.gradle.logging.LoggingTestHelper;
import org.gradle.util.JUnit4GroovyMockery;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Marker;

import static org.junit.Assert.*;

@RunWith(JMock.class)
public class LoggingTest {
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private final Appender<ILoggingEvent> appender = context.mock(Appender.class);
    private final LoggingTestHelper logging = new LoggingTestHelper(appender);

    @Before
    public void attachAppender() {
        logging.attachAppender();
    }

    @After
    public void detachAppender() {
        logging.detachAppender();
    }

    @Test
    public void routesLogMessagesViaSlf4j() {
        Logger logger = Logging.getLogger(LoggingTest.class);

        expectLogMessage(Level.TRACE, null, "trace");
        logger.trace("trace");

        expectLogMessage(Level.DEBUG, null, "debug");
        logger.debug("debug");

        expectLogMessage(Level.INFO, null, "info");
        logger.info("info");

        expectLogMessage(Level.WARN, null, "warn");
        logger.warn("warn");

        expectLogMessage(Level.INFO, Logging.LIFECYCLE, "lifecycle");
        logger.lifecycle("lifecycle");

        expectLogMessage(Level.ERROR, null, "error");
        logger.error("error");

        expectLogMessage(Level.INFO, Logging.QUIET, "quiet");
        logger.quiet("quiet");

        expectLogMessage(Level.INFO, Logging.LIFECYCLE, "lifecycle via level");
        logger.log(LogLevel.LIFECYCLE, "lifecycle via level");
    }

    @Test
    public void delegatesLevelIsEnabledToSlf4j() {
        logging.setLevel(Level.WARN);

        Logger logger = Logging.getLogger(LoggingTest.class);
        assertTrue(logger.isErrorEnabled());
        assertTrue(logger.isWarnEnabled());
        assertFalse(logger.isQuietEnabled());
        assertFalse(logger.isLifecycleEnabled());
        assertFalse(logger.isInfoEnabled());
        assertFalse(logger.isDebugEnabled());
        assertFalse(logger.isTraceEnabled());

        assertTrue(logger.isEnabled(LogLevel.ERROR));
        assertFalse(logger.isEnabled(LogLevel.INFO));
    }

    private void expectLogMessage(final Level level, final Marker marker, final String text) {
        final Matcher<LoggingEvent> matcher = new BaseMatcher<LoggingEvent>() {

            public void describeTo(Description description) {
                description.appendText("level: ").appendValue(level).appendText(", marker: ").appendValue(marker)
                        .appendText(", text:").appendValue(text);
            }

            public boolean matches(Object o) {
                LoggingEvent event = (LoggingEvent) o;
                return event.getLevel().equals(level) && event.getMessage().equals(text) && event.getMarker() == marker;
            }
        };

        context.checking(new Expectations() {{
            one(appender).doAppend(with(matcher));
        }});
    }
}
