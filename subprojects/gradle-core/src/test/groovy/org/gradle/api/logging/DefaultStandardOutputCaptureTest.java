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

import org.gradle.logging.LoggingSystem;
import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.util.RedirectStdOutAndErr;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultStandardOutputCaptureTest {
    @Rule
    public final RedirectStdOutAndErr outputs = new RedirectStdOutAndErr();
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery();
    private final LoggingSystem loggingSystem = context.mock(LoggingSystem.class);
    private final LoggingSystem stdOutLoggingSystem = context.mock(LoggingSystem.class);
    private final DefaultStandardOutputCapture standardOutputCapture = new DefaultStandardOutputCapture(loggingSystem, stdOutLoggingSystem);

    @Test
    public void defaultValues() {
        assertTrue(standardOutputCapture.isStandardOutputCaptureEnabled());
        assertEquals(LogLevel.QUIET, standardOutputCapture.getStandardOutputCaptureLevel());
        assertNull(standardOutputCapture.getLevel());
    }

    @Test
    public void canChangeStdOutCaptureLogLevel() {
        standardOutputCapture.captureStandardOutput(LogLevel.ERROR);
        assertTrue(standardOutputCapture.isStandardOutputCaptureEnabled());
        assertEquals(LogLevel.ERROR, standardOutputCapture.getStandardOutputCaptureLevel());
    }

    @Test
    public void canChangeLogLevel() {
        standardOutputCapture.setLevel(LogLevel.ERROR);
        assertEquals(LogLevel.ERROR, standardOutputCapture.getLevel());
    }

    @Test
    public void canDisableCapture() {
        standardOutputCapture.disableStandardOutputCapture();
        assertFalse(standardOutputCapture.isStandardOutputCaptureEnabled());
        assertNull(standardOutputCapture.getStandardOutputCaptureLevel());
    }

    @Test
    public void startStopWithCaptureDisabled() {
        standardOutputCapture.disableStandardOutputCapture();

        final LoggingSystem.Snapshot snapshot = context.mock(LoggingSystem.Snapshot.class);
        context.checking(new Expectations() {{
            ignoring(loggingSystem);
            one(stdOutLoggingSystem).off();
            will(returnValue(snapshot));
        }});

        standardOutputCapture.start();

        context.checking(new Expectations(){{
            one(stdOutLoggingSystem).restore(snapshot);
        }});

        standardOutputCapture.stop();
    }

    @Test
    public void startStopWithCaptureEnabled() {
        standardOutputCapture.captureStandardOutput(LogLevel.DEBUG);

        final LoggingSystem.Snapshot snapshot = context.mock(LoggingSystem.Snapshot.class);
        context.checking(new Expectations() {{
            ignoring(loggingSystem);
            one(stdOutLoggingSystem).on(LogLevel.DEBUG);
            will(returnValue(snapshot));
        }});

        standardOutputCapture.start();

        context.checking(new Expectations(){{
            one(stdOutLoggingSystem).restore(snapshot);
        }});

        standardOutputCapture.stop();
    }

    @Test
    public void startStopWithLogLevelSet() {
        standardOutputCapture.setLevel(LogLevel.DEBUG);

        final LoggingSystem.Snapshot snapshot = context.mock(LoggingSystem.Snapshot.class);
        context.checking(new Expectations() {{
            ignoring(stdOutLoggingSystem);
            one(loggingSystem).on(LogLevel.DEBUG);
            will(returnValue(snapshot));
        }});

        standardOutputCapture.start();

        context.checking(new Expectations(){{
            one(loggingSystem).restore(snapshot);
        }});

        standardOutputCapture.stop();
    }

    @Test
    public void startStopWithLogLevelNotSet() {
        final LoggingSystem.Snapshot snapshot = context.mock(LoggingSystem.Snapshot.class);
        context.checking(new Expectations() {{
            ignoring(stdOutLoggingSystem);
            one(loggingSystem).snapshot();
            will(returnValue(snapshot));
        }});

        standardOutputCapture.start();

        context.checking(new Expectations() {{
            one(loggingSystem).restore(snapshot);
        }});

        standardOutputCapture.stop();
    }

    @Test
    public void disableCaptureWhileStarted() {
        final LoggingSystem.Snapshot snapshot = context.mock(LoggingSystem.Snapshot.class);
        context.checking(new Expectations() {{
            ignoring(loggingSystem);
            one(stdOutLoggingSystem).on(LogLevel.DEBUG);
            will(returnValue(snapshot));
        }});

        standardOutputCapture.captureStandardOutput(LogLevel.DEBUG);

        standardOutputCapture.start();

        context.checking(new Expectations() {{
            one(stdOutLoggingSystem).off();
            will(returnValue(context.mock(LoggingSystem.Snapshot.class)));
        }});

        standardOutputCapture.disableStandardOutputCapture();

        context.checking(new Expectations() {{
            one(stdOutLoggingSystem).restore(snapshot);
        }});

        standardOutputCapture.stop();
    }

    @Test
    public void enableCaptureWhileStarted() {
        final LoggingSystem.Snapshot snapshot = context.mock(LoggingSystem.Snapshot.class);
        context.checking(new Expectations() {{
            ignoring(loggingSystem);
            one(stdOutLoggingSystem).off();
            will(returnValue(snapshot));
        }});

        standardOutputCapture.disableStandardOutputCapture();

        standardOutputCapture.start();

        context.checking(new Expectations() {{
            one(stdOutLoggingSystem).on(LogLevel.DEBUG);
            will(returnValue(context.mock(LoggingSystem.Snapshot.class)));
        }});

        standardOutputCapture.captureStandardOutput(LogLevel.DEBUG);

        context.checking(new Expectations(){{
            one(stdOutLoggingSystem).restore(snapshot);
        }});

        standardOutputCapture.stop();
    }
    
    @Test
    public void changeCaptureLevelWhileStarted() {
        final LoggingSystem.Snapshot snapshot = context.mock(LoggingSystem.Snapshot.class);
        context.checking(new Expectations() {{
            ignoring(loggingSystem);
            one(stdOutLoggingSystem).on(LogLevel.DEBUG);
            will(returnValue(snapshot));
        }});

        standardOutputCapture.captureStandardOutput(LogLevel.DEBUG);

        standardOutputCapture.start();

        context.checking(new Expectations() {{
            one(stdOutLoggingSystem).on(LogLevel.WARN);
            will(returnValue(context.mock(LoggingSystem.Snapshot.class)));
        }});

        standardOutputCapture.captureStandardOutput(LogLevel.WARN);

        context.checking(new Expectations(){{
            one(stdOutLoggingSystem).restore(snapshot);
        }});

        standardOutputCapture.stop();
    }
    
    @Test
    public void changeLogLevelWhileStarted() {
        final LoggingSystem.Snapshot snapshot = context.mock(LoggingSystem.Snapshot.class);
        context.checking(new Expectations() {{
            ignoring(stdOutLoggingSystem);
            one(loggingSystem).snapshot();
            will(returnValue(snapshot));
        }});

        standardOutputCapture.start();

        context.checking(new Expectations() {{
            ignoring(stdOutLoggingSystem);
            one(loggingSystem).on(LogLevel.LIFECYCLE);
            will(returnValue(context.mock(LoggingSystem.Snapshot.class)));
        }});

        standardOutputCapture.setLevel(LogLevel.LIFECYCLE);

        context.checking(new Expectations(){{
            one(loggingSystem).restore(snapshot);
        }});

        standardOutputCapture.stop();
    }
}
