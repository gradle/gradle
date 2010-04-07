/*
 * Copyright 2007-2008 the original author or authors.
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

import org.junit.Before;
import org.junit.Test;

import java.io.PrintStream;

import static org.gradle.util.Matchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * @author Hans Dockter
 */
public class StandardOutputStateTest {
    private PrintStream testPrintStreamOut;
    private PrintStream testPrintStreamErr;

    @Before
    public void setUp() {
        testPrintStreamOut = new PrintStream(System.out);
        testPrintStreamErr = new PrintStream(System.out);
    }
    
    @Test
    public void testInit() {
        StandardOutputState state = new StandardOutputState(testPrintStreamOut, LogLevel.INFO, testPrintStreamErr, LogLevel.DEBUG);
        assertSame(testPrintStreamOut, state.getOutStream());
        assertSame(LogLevel.INFO, state.getOutLevel());
        assertSame(testPrintStreamErr, state.getErrStream());
        assertSame(LogLevel.DEBUG, state.getErrLevel());
    }
    
    @Test
    public void testEqualsAndHashCode() {
        StandardOutputState state = new StandardOutputState(testPrintStreamOut, LogLevel.INFO, testPrintStreamErr, LogLevel.DEBUG);
        StandardOutputState same = new StandardOutputState(testPrintStreamOut, LogLevel.INFO, testPrintStreamErr, LogLevel.DEBUG);
        StandardOutputState differentOutStr = new StandardOutputState(System.out, LogLevel.INFO, testPrintStreamErr, LogLevel.DEBUG);
        StandardOutputState differentOutLevel = new StandardOutputState(testPrintStreamOut, LogLevel.ERROR, testPrintStreamErr, LogLevel.DEBUG);
        StandardOutputState differentErrStr = new StandardOutputState(testPrintStreamOut, LogLevel.INFO, System.err, LogLevel.DEBUG);
        StandardOutputState differentErrLevel = new StandardOutputState(testPrintStreamOut, LogLevel.INFO, testPrintStreamErr, LogLevel.ERROR);

        assertThat(state, strictlyEqual(same));
        assertThat(state, not(equalTo(differentOutStr)));
        assertThat(state, not(equalTo(differentOutLevel)));
        assertThat(state, not(equalTo(differentErrStr)));
        assertThat(state, not(equalTo(differentErrLevel)));
    }
}
