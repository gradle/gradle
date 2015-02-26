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

import org.gradle.logging.ConfigureLogging;
import org.gradle.logging.internal.LogEvent;
import org.gradle.logging.internal.OutputEventListener;
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(JMock.class)
public class LoggingTest {
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private final OutputEventListener outputEventListener = context.mock(OutputEventListener.class);
    private final ConfigureLogging logging = new ConfigureLogging(outputEventListener);

    @Before
    public void attachListener() {
        logging.attachListener();
    }

    @After
    public void resetLogging() {
        logging.resetLogging();
    }

    @Test
    public void routesLogMessagesViaSlf4j() {
        Logger logger = Logging.getLogger(LoggingTest.class);

        expectLogMessage(LogLevel.DEBUG, "debug");
        logger.debug("debug");

        expectLogMessage(LogLevel.INFO, "info");
        logger.info("info");

        expectLogMessage(LogLevel.WARN, "warn");
        logger.warn("warn");

        expectLogMessage(LogLevel.LIFECYCLE, "lifecycle");
        logger.lifecycle("lifecycle");

        expectLogMessage(LogLevel.ERROR, "error");
        logger.error("error");

        expectLogMessage(LogLevel.QUIET, "quiet");
        logger.quiet("quiet");

        expectLogMessage(LogLevel.LIFECYCLE, "lifecycle via level");
        logger.log(LogLevel.LIFECYCLE, "lifecycle via level");
    }

    @Test
    public void ignoresTraceLevelLogging() {
        Logger logger = Logging.getLogger(LoggingTest.class);

        context.checking(new Expectations() {{
            never(outputEventListener);
        }});
        logger.trace("trace");
    }

    @Test
    public void delegatesLevelIsEnabledToSlf4j() {
        logging.setLevel(LogLevel.WARN);

        Logger logger = Logging.getLogger(LoggingTest.class);
        assertTrue(logger.isErrorEnabled());
        assertTrue(logger.isQuietEnabled());
        assertTrue(logger.isWarnEnabled());
        assertFalse(logger.isLifecycleEnabled());
        assertFalse(logger.isInfoEnabled());
        assertFalse(logger.isDebugEnabled());
        assertFalse(logger.isTraceEnabled());

        assertTrue(logger.isEnabled(LogLevel.ERROR));
        assertFalse(logger.isEnabled(LogLevel.INFO));
    }

    private void expectLogMessage(final LogLevel level, final String text) {
        final Matcher<LogEvent> matcher = new BaseMatcher<LogEvent>() {

            public void describeTo(Description description) {
                description.appendText("level: ").appendValue(level).appendText(", text:").appendValue(text);
            }

            public boolean matches(Object o) {
                LogEvent event = (LogEvent) o;
                return event.getLogLevel() == level && event.getMessage().equals(text);
            }
        };

        context.checking(new Expectations() {{
            one(outputEventListener).onOutput(with(matcher));
        }});
    }
}
