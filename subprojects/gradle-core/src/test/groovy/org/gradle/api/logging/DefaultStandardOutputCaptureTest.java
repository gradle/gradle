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

import org.gradle.util.RedirectStdOutAndErr;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Hans Dockter
 */
public class DefaultStandardOutputCaptureTest {
    @Rule
    public final RedirectStdOutAndErr outputs = new RedirectStdOutAndErr();
    private final DefaultStandardOutputCapture standardOutputCapture = new DefaultStandardOutputCapture();

    @Test
    public void defaultValues() {
        assertTrue(standardOutputCapture.isEnabled());
        assertEquals(LogLevel.QUIET, standardOutputCapture.getLevel());
    }

    @Test
    public void canChangeLogLevel() {
        standardOutputCapture.captureStandardOutput(LogLevel.ERROR);
        assertTrue(standardOutputCapture.isEnabled());
        assertEquals(LogLevel.ERROR, standardOutputCapture.getLevel());
    }

    @Test
    public void canDisableCapture() {
        standardOutputCapture.disableStandardOutputCapture();
        assertFalse(standardOutputCapture.isEnabled());
        assertNull(standardOutputCapture.getLevel());
    }

    @Test
    public void startStopWithCaptureDisabledWhenStdOutNotBeingCaptured() {
        StandardOutputState state = StandardOutputLogging.getStateSnapshot();

        standardOutputCapture.disableStandardOutputCapture();

        standardOutputCapture.start();

        assertSame(StandardOutputLogging.DEFAULT_OUT, System.out);
        assertSame(StandardOutputLogging.DEFAULT_ERR, System.err);

        standardOutputCapture.stop();

        assertEquals(state, StandardOutputLogging.getStateSnapshot());
    }

    @Test
    public void startStopWithCaptureDisabledWhenStdOutBeingCaptured() {
        StandardOutputLogging.on(LogLevel.ERROR);
        StandardOutputState state = StandardOutputLogging.getStateSnapshot();

        standardOutputCapture.disableStandardOutputCapture();

        standardOutputCapture.start();

        assertSame(StandardOutputLogging.DEFAULT_OUT, System.out);
        assertSame(StandardOutputLogging.DEFAULT_ERR, System.err);

        standardOutputCapture.stop();

        assertEquals(state, StandardOutputLogging.getStateSnapshot());
    }

    @Test
    public void startStopWithCaptureEnabledWhenStdOutNotBeingCaptured() {
        StandardOutputState oldState = StandardOutputLogging.getStateSnapshot();

        standardOutputCapture.captureStandardOutput(LogLevel.DEBUG);

        standardOutputCapture.start();

        assertSame(StandardOutputLogging.getOut(), System.out);
        assertSame(StandardOutputLogging.getErr(), System.err);
        assertEquals(StandardOutputLogging.getOutAdapter().getLevel(), LogLevel.DEBUG);
        assertEquals(StandardOutputLogging.getErrAdapter().getLevel(), LogLevel.ERROR);

        standardOutputCapture.stop();

        assertEquals(oldState, StandardOutputLogging.getStateSnapshot());
    }

    @Test
    public void startStopWithCaptureEnabledWhenStdOutBeingCaptured() {
        StandardOutputLogging.onOut(LogLevel.ERROR);
        StandardOutputLogging.onErr(LogLevel.DEBUG);

        standardOutputCapture.captureStandardOutput(LogLevel.DEBUG);

        StandardOutputState oldState = StandardOutputLogging.getStateSnapshot();

        standardOutputCapture.start();

        assertSame(StandardOutputLogging.getOut(), System.out);
        assertSame(StandardOutputLogging.getErr(), System.err);
        assertEquals(StandardOutputLogging.getOutAdapter().getLevel(), LogLevel.DEBUG);
        assertEquals(StandardOutputLogging.getErrAdapter().getLevel(), LogLevel.ERROR);

        standardOutputCapture.stop();

        assertEquals(oldState, StandardOutputLogging.getStateSnapshot());
    }

    @Test
    public void disableCaptureWhileStarted() {
        StandardOutputState oldState = StandardOutputLogging.getStateSnapshot();

        standardOutputCapture.captureStandardOutput(LogLevel.DEBUG);

        standardOutputCapture.start();

        assertSame(StandardOutputLogging.getOut(), System.out);
        assertSame(StandardOutputLogging.getErr(), System.err);
        assertEquals(StandardOutputLogging.getOutAdapter().getLevel(), LogLevel.DEBUG);
        assertEquals(StandardOutputLogging.getErrAdapter().getLevel(), LogLevel.ERROR);

        standardOutputCapture.disableStandardOutputCapture();

        assertSame(StandardOutputLogging.DEFAULT_OUT, System.out);
        assertSame(StandardOutputLogging.DEFAULT_ERR, System.err);

        standardOutputCapture.stop();

        assertEquals(oldState, StandardOutputLogging.getStateSnapshot());
    }

    @Test
    public void enableCaptureWhileStarted() {
        StandardOutputState oldState = StandardOutputLogging.getStateSnapshot();

        standardOutputCapture.disableStandardOutputCapture();

        standardOutputCapture.start();

        assertSame(StandardOutputLogging.DEFAULT_OUT, System.out);
        assertSame(StandardOutputLogging.DEFAULT_ERR, System.err);

        standardOutputCapture.captureStandardOutput(LogLevel.DEBUG);

        assertSame(StandardOutputLogging.getOut(), System.out);
        assertSame(StandardOutputLogging.getErr(), System.err);
        assertEquals(StandardOutputLogging.getOutAdapter().getLevel(), LogLevel.DEBUG);
        assertEquals(StandardOutputLogging.getErrAdapter().getLevel(), LogLevel.ERROR);

        standardOutputCapture.stop();

        assertEquals(oldState, StandardOutputLogging.getStateSnapshot());
    }
    
    @Test
    public void changeCaptureLevelWhileStarted() {
        StandardOutputState oldState = StandardOutputLogging.getStateSnapshot();

        standardOutputCapture.captureStandardOutput(LogLevel.DEBUG);

        standardOutputCapture.start();

        assertSame(StandardOutputLogging.getOut(), System.out);
        assertSame(StandardOutputLogging.getErr(), System.err);
        assertEquals(StandardOutputLogging.getOutAdapter().getLevel(), LogLevel.DEBUG);
        assertEquals(StandardOutputLogging.getErrAdapter().getLevel(), LogLevel.ERROR);

        standardOutputCapture.captureStandardOutput(LogLevel.WARN);

        assertSame(StandardOutputLogging.getOut(), System.out);
        assertSame(StandardOutputLogging.getErr(), System.err);
        assertEquals(StandardOutputLogging.getOutAdapter().getLevel(), LogLevel.WARN);
        assertEquals(StandardOutputLogging.getErrAdapter().getLevel(), LogLevel.ERROR);

        standardOutputCapture.stop();

        assertEquals(oldState, StandardOutputLogging.getStateSnapshot());
    }
}
