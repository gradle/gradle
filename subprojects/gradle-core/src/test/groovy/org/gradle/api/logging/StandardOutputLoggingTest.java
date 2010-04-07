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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Hans Dockter
 */
public class StandardOutputLoggingTest {
    private void setToNonDefaultValues(boolean out, boolean err) {
        if (out) { StandardOutputLogging.onOut(LogLevel.DEBUG); }
        if (err) { StandardOutputLogging.onErr(LogLevel.INFO); }
    }

    @Before
    public void setUp() {
        StandardOutputLogging.off();
    }

    @After
    public void tearDown() {
        StandardOutputLogging.off();
    }

    @Test
    public void on() {
        setToNonDefaultValues(true, true);
        StandardOutputLogging.on(LogLevel.INFO);
        checkOutIsCaptured(LogLevel.INFO);
        checkErrIsCaptured(LogLevel.ERROR);
    }

    @Test
    public void onOut() {
        setToNonDefaultValues(true, false);
        StandardOutputLogging.onOut(LogLevel.INFO);
        checkOutIsCaptured(LogLevel.INFO);
        checkErrIsStderr();
    }

    @Test
    public void onOutWithLifecycle() {
        setToNonDefaultValues(true, false);
        StandardOutputLogging.onOut(LogLevel.LIFECYCLE);
        checkOutIsCaptured(LogLevel.LIFECYCLE);
        checkErrIsStderr();
    }

    @Test
    public void onOutWithQuiet() {
        setToNonDefaultValues(true, false);
        StandardOutputLogging.onOut(LogLevel.QUIET);
        checkOutIsCaptured(LogLevel.QUIET);
        checkErrIsStderr();
    }

    @Test
    public void onErr() {
        setToNonDefaultValues(false, true);
        StandardOutputLogging.onErr(LogLevel.ERROR);
        checkOutIsStdout();
        checkErrIsCaptured(LogLevel.ERROR);
    }

    @Test
    public void offWhenAlreadyOff() {
        StandardOutputLogging.off();
        checkOutIsStdout();
        checkErrIsStderr();
    }

    @Test
    public void offOut() {
        StandardOutputLogging.on(LogLevel.INFO);
        StandardOutputLogging.offOut();
        checkOutIsStdout();
        checkErrIsCaptured(LogLevel.ERROR);
    }

    @Test
    public void offErr() {
        StandardOutputLogging.on(LogLevel.INFO);
        StandardOutputLogging.offErr();
        checkOutIsCaptured(LogLevel.INFO);
        checkErrIsStderr();
    }

    @Test
    public void init() {
        checkOutIsStdout();
        checkErrIsStderr();
    }

    @Test
    public void getAndRestoreStateWhenCaptureOff() {
        StandardOutputLogging.on(LogLevel.INFO);
        StandardOutputState state = StandardOutputLogging.getStateSnapshot();
        
        StandardOutputLogging.off();

        StandardOutputLogging.restoreState(state);

        checkOutIsCaptured(LogLevel.INFO);
        checkErrIsCaptured(LogLevel.ERROR);
    }

    @Test
    public void getAndRestoreStateWhenLevelsChange() {
        StandardOutputLogging.on(LogLevel.INFO);
        StandardOutputState state = StandardOutputLogging.getStateSnapshot();

        StandardOutputLogging.on(LogLevel.DEBUG);

        StandardOutputLogging.restoreState(state);

        checkOutIsCaptured(LogLevel.INFO);
        checkErrIsCaptured(LogLevel.ERROR);
    }

    @Test
    public void getAndRestoreStateWhenCaptureOn() {
        StandardOutputState state = StandardOutputLogging.getStateSnapshot();

        StandardOutputLogging.on(LogLevel.INFO);

        StandardOutputLogging.restoreState(state);

        checkOutIsStdout();
        checkErrIsStderr();
    }

    private void checkOutIsCaptured(LogLevel expectedOut) {
        assertEquals(StandardOutputLogging.getOut(), System.out);
        assertEquals(StandardOutputLogging.getOutAdapter().getLevel(), expectedOut);
    }

    private void checkOutIsStdout() {
        assertEquals(StandardOutputLogging.DEFAULT_OUT, System.out);
    }

    private void checkErrIsCaptured(LogLevel expectedErr) {
        assertEquals(StandardOutputLogging.getErr(), System.err);
        assertEquals(StandardOutputLogging.getErrAdapter().getLevel(), expectedErr);
    }

    private void checkErrIsStderr() {
        assertEquals(StandardOutputLogging.DEFAULT_ERR, System.err);
    }
}
