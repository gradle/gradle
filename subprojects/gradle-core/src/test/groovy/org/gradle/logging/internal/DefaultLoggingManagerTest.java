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
package org.gradle.logging.internal;

import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.StandardOutputListener;
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
public class DefaultLoggingManagerTest {
    @Rule
    public final RedirectStdOutAndErr outputs = new RedirectStdOutAndErr();
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery();
    private final LoggingSystem loggingSystem = context.mock(LoggingSystem.class);
    private final LoggingSystem stdOutLoggingSystem = context.mock(LoggingSystem.class);
    private final LoggingSystem stdErrLoggingSystem = context.mock(LoggingSystem.class);
    private final LoggingOutputInternal loggingOutput = context.mock(LoggingOutputInternal.class);
    private final DefaultLoggingManager loggingManager = new DefaultLoggingManager(loggingSystem, stdOutLoggingSystem, stdErrLoggingSystem, loggingOutput);

    @Test
    public void defaultValues() {
        assertTrue(loggingManager.isStandardOutputCaptureEnabled());
        assertEquals(LogLevel.QUIET, loggingManager.getStandardOutputCaptureLevel());
        assertEquals(LogLevel.ERROR, loggingManager.getStandardErrorCaptureLevel());
        assertNull(loggingManager.getLevel());
    }

    @Test
    public void canChangeStdOutCaptureLogLevel() {
        loggingManager.captureStandardOutput(LogLevel.ERROR);
        assertTrue(loggingManager.isStandardOutputCaptureEnabled());
        assertEquals(LogLevel.ERROR, loggingManager.getStandardOutputCaptureLevel());
    }

    @Test
    public void canChangeStdErrCaptureLogLevel() {
        loggingManager.captureStandardError(LogLevel.WARN);
        assertEquals(LogLevel.WARN, loggingManager.getStandardErrorCaptureLevel());
    }

    @Test
    public void canChangeLogLevel() {
        loggingManager.setLevel(LogLevel.ERROR);
        assertEquals(LogLevel.ERROR, loggingManager.getLevel());
    }

    @Test
    public void canDisableCapture() {
        loggingManager.disableStandardOutputCapture();
        assertFalse(loggingManager.isStandardOutputCaptureEnabled());
        assertNull(loggingManager.getStandardOutputCaptureLevel());
    }

    @Test
    public void startStopWithCaptureDisabled() {
        loggingManager.disableStandardOutputCapture();

        final LoggingSystem.Snapshot stdOutSnapshot = context.mock(LoggingSystem.Snapshot.class);
        final LoggingSystem.Snapshot stdErrSnapshot = context.mock(LoggingSystem.Snapshot.class);
        context.checking(new Expectations() {{
            ignoring(loggingSystem);
            one(stdOutLoggingSystem).off();
            will(returnValue(stdOutSnapshot));
            one(stdErrLoggingSystem).off();
            will(returnValue(stdErrSnapshot));
        }});

        loggingManager.start();

        context.checking(new Expectations() {{
            one(stdOutLoggingSystem).restore(stdOutSnapshot);
            one(stdErrLoggingSystem).restore(stdErrSnapshot);
        }});

        loggingManager.stop();
    }

    @Test
    public void startStopWithCaptureEnabled() {
        loggingManager.captureStandardOutput(LogLevel.DEBUG);
        loggingManager.captureStandardError(LogLevel.INFO);

        final LoggingSystem.Snapshot stdOutSnapshot = context.mock(LoggingSystem.Snapshot.class);
        final LoggingSystem.Snapshot stdErrSnapshot = context.mock(LoggingSystem.Snapshot.class);
        context.checking(new Expectations() {{
            ignoring(loggingSystem);
            one(stdOutLoggingSystem).on(LogLevel.DEBUG);
            will(returnValue(stdOutSnapshot));
            one(stdErrLoggingSystem).on(LogLevel.INFO);
            will(returnValue(stdErrSnapshot));
        }});

        loggingManager.start();

        context.checking(new Expectations() {{
            one(stdOutLoggingSystem).restore(stdOutSnapshot);
            one(stdErrLoggingSystem).restore(stdErrSnapshot);
        }});

        loggingManager.stop();
    }

    @Test
    public void startStopWithLogLevelSet() {
        loggingManager.setLevel(LogLevel.DEBUG);

        final LoggingSystem.Snapshot snapshot = context.mock(LoggingSystem.Snapshot.class);
        context.checking(new Expectations() {{
            ignoring(stdOutLoggingSystem);
            ignoring(stdErrLoggingSystem);
            one(loggingSystem).on(LogLevel.DEBUG);
            will(returnValue(snapshot));
        }});

        loggingManager.start();

        context.checking(new Expectations() {{
            one(loggingSystem).restore(snapshot);
        }});

        loggingManager.stop();
    }

    @Test
    public void startStopWithLogLevelNotSet() {
        final LoggingSystem.Snapshot snapshot = context.mock(LoggingSystem.Snapshot.class);
        context.checking(new Expectations() {{
            ignoring(stdOutLoggingSystem);
            ignoring(stdErrLoggingSystem);
            one(loggingSystem).snapshot();
            will(returnValue(snapshot));
        }});

        loggingManager.start();

        context.checking(new Expectations() {{
            one(loggingSystem).restore(snapshot);
        }});

        loggingManager.stop();
    }

    @Test
    public void disableCaptureWhileStarted() {
        final LoggingSystem.Snapshot stdOutSnapshot = context.mock(LoggingSystem.Snapshot.class);
        final LoggingSystem.Snapshot stdErrSnapshot = context.mock(LoggingSystem.Snapshot.class);
        context.checking(new Expectations() {{
            ignoring(loggingSystem);
            one(stdOutLoggingSystem).on(LogLevel.DEBUG);
            will(returnValue(stdOutSnapshot));
            one(stdErrLoggingSystem).on(LogLevel.INFO);
            will(returnValue(stdErrSnapshot));
        }});

        loggingManager.captureStandardOutput(LogLevel.DEBUG);
        loggingManager.captureStandardError(LogLevel.INFO);

        loggingManager.start();

        context.checking(new Expectations() {{
            one(stdOutLoggingSystem).off();
            one(stdErrLoggingSystem).off();
        }});

        loggingManager.disableStandardOutputCapture();

        context.checking(new Expectations() {{
            one(stdOutLoggingSystem).restore(stdOutSnapshot);
            one(stdErrLoggingSystem).restore(stdErrSnapshot);
        }});

        loggingManager.stop();
    }

    @Test
    public void enableCaptureWhileStarted() {
        final LoggingSystem.Snapshot stdOutSnapshot = context.mock(LoggingSystem.Snapshot.class);
        final LoggingSystem.Snapshot stdErrSnapshot = context.mock(LoggingSystem.Snapshot.class);
        context.checking(new Expectations() {{
            ignoring(loggingSystem);
            one(stdOutLoggingSystem).off();
            will(returnValue(stdOutSnapshot));
            one(stdErrLoggingSystem).off();
            will(returnValue(stdErrSnapshot));
        }});

        loggingManager.disableStandardOutputCapture();

        loggingManager.start();

        context.checking(new Expectations() {{
            one(stdOutLoggingSystem).on(LogLevel.DEBUG);
            one(stdErrLoggingSystem).on(LogLevel.INFO);
        }});

        loggingManager.captureStandardOutput(LogLevel.DEBUG);
        loggingManager.captureStandardError(LogLevel.INFO);

        context.checking(new Expectations() {{
            one(stdOutLoggingSystem).restore(stdOutSnapshot);
            one(stdErrLoggingSystem).restore(stdErrSnapshot);
        }});

        loggingManager.stop();
    }

    @Test
    public void changeCaptureLevelWhileStarted() {
        final LoggingSystem.Snapshot stdOutSnapshot = context.mock(LoggingSystem.Snapshot.class);
        final LoggingSystem.Snapshot stdErrSnapshot = context.mock(LoggingSystem.Snapshot.class);
        context.checking(new Expectations() {{
            ignoring(loggingSystem);
            one(stdOutLoggingSystem).on(LogLevel.DEBUG);
            will(returnValue(stdOutSnapshot));
            one(stdErrLoggingSystem).on(LogLevel.DEBUG);
            will(returnValue(stdErrSnapshot));
        }});

        loggingManager.captureStandardOutput(LogLevel.DEBUG);
        loggingManager.captureStandardError(LogLevel.DEBUG);

        loggingManager.start();

        context.checking(new Expectations() {{
            one(stdOutLoggingSystem).on(LogLevel.WARN);
        }});

        loggingManager.captureStandardOutput(LogLevel.WARN);

        context.checking(new Expectations() {{
            one(stdOutLoggingSystem).restore(stdOutSnapshot);
            one(stdErrLoggingSystem).restore(stdErrSnapshot);
        }});

        loggingManager.stop();
    }

    @Test
    public void changeLogLevelWhileStarted() {
        final LoggingSystem.Snapshot snapshot = context.mock(LoggingSystem.Snapshot.class);
        context.checking(new Expectations() {{
            ignoring(stdOutLoggingSystem);
            ignoring(stdErrLoggingSystem);
            one(loggingSystem).snapshot();
            will(returnValue(snapshot));
        }});

        loggingManager.start();

        context.checking(new Expectations() {{
            ignoring(stdOutLoggingSystem);
            one(loggingSystem).on(LogLevel.LIFECYCLE);
            will(returnValue(context.mock(LoggingSystem.Snapshot.class)));
        }});

        loggingManager.setLevel(LogLevel.LIFECYCLE);

        context.checking(new Expectations() {{
            one(loggingSystem).restore(snapshot);
        }});

        loggingManager.stop();
    }

    @Test
    public void addsListenersOnStartAndRemovesOnStop() {
        final StandardOutputListener stdoutListener = context.mock(StandardOutputListener.class);
        final StandardOutputListener stderrListener = context.mock(StandardOutputListener.class);

        loggingManager.addStandardOutputListener(stdoutListener);
        loggingManager.addStandardErrorListener(stderrListener);

        context.checking(new Expectations() {{
            ignoring(loggingSystem);
            ignoring(stdOutLoggingSystem);
            ignoring(stdErrLoggingSystem);
            one(loggingOutput).addStandardOutputListener(stdoutListener);
            one(loggingOutput).addStandardErrorListener(stderrListener);
        }});

        loggingManager.start();

        context.checking(new Expectations() {{
            one(loggingOutput).removeStandardOutputListener(stdoutListener);
            one(loggingOutput).removeStandardErrorListener(stderrListener);
        }});

        loggingManager.stop();
    }

    @Test
    public void addsListenersWhileStarted() {
        final StandardOutputListener stdoutListener = context.mock(StandardOutputListener.class);
        final StandardOutputListener stderrListener = context.mock(StandardOutputListener.class);

        context.checking(new Expectations() {{
            ignoring(loggingSystem);
            ignoring(stdOutLoggingSystem);
            ignoring(stdErrLoggingSystem);
        }});

        loggingManager.start();

        context.checking(new Expectations() {{
            one(loggingOutput).addStandardOutputListener(stdoutListener);
            one(loggingOutput).addStandardErrorListener(stderrListener);
        }});

        loggingManager.addStandardOutputListener(stdoutListener);
        loggingManager.addStandardErrorListener(stderrListener);
    }

    @Test
    public void removesListenersWhileStarted() {
        final StandardOutputListener stdoutListener = context.mock(StandardOutputListener.class);
        final StandardOutputListener stderrListener = context.mock(StandardOutputListener.class);

        loggingManager.addStandardOutputListener(stdoutListener);
        loggingManager.addStandardErrorListener(stderrListener);

        context.checking(new Expectations() {{
            ignoring(loggingSystem);
            ignoring(stdOutLoggingSystem);
            ignoring(stdErrLoggingSystem);
            one(loggingOutput).addStandardOutputListener(stdoutListener);
            one(loggingOutput).addStandardErrorListener(stderrListener);
        }});

        loggingManager.start();

        context.checking(new Expectations() {{
            one(loggingOutput).removeStandardOutputListener(stdoutListener);
            one(loggingOutput).removeStandardErrorListener(stderrListener);
        }});

        loggingManager.removeStandardOutputListener(stdoutListener);
        loggingManager.removeStandardErrorListener(stderrListener);
    }
}
