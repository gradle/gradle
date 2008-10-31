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

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import org.hamcrest.Matchers;

import java.io.PrintStream;

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
        StandardOutputState state = new StandardOutputState(testPrintStreamOut, testPrintStreamErr);
        assertSame(testPrintStreamOut, state.getOutStream());
        assertSame(testPrintStreamErr, state.getErrStream());
    }

    @Test
    public void equalityAndHashcode() {
        StandardOutputState state = new StandardOutputState(testPrintStreamOut, testPrintStreamErr);
        assertEquals(state, new StandardOutputState(testPrintStreamOut, testPrintStreamErr));
        assertThat(state, Matchers.not(Matchers.equalTo(
                new StandardOutputState(testPrintStreamOut, new PrintStream(System.out)))));
        assertEquals(state.hashCode(), new StandardOutputState(testPrintStreamOut, testPrintStreamErr).hashCode());
    }


}
