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
package org.gradle.api.internal.project;

import org.gradle.api.logging.StandardOutputLogging;
import org.gradle.api.logging.LogLevel;
import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;
import ch.qos.logback.classic.Level;

/**
 * @author Hans Dockter
 */
public class DefaultStandardOutputRedirectorTest {
    private DefaultStandardOutputRedirector standardOutputRedirector;

    @Before
    public void setUp() {
        standardOutputRedirector = new DefaultStandardOutputRedirector();   
    }

    @Test
    public void off() {
        standardOutputRedirector.off();
        assertSame(StandardOutputLogging.DEFAULT_OUT, System.out);
        assertSame(StandardOutputLogging.DEFAULT_ERR, System.err);
    }

    @Test
    public void captureStandardOutput() {
        standardOutputRedirector.on(LogLevel.DEBUG);
        assertSame(StandardOutputLogging.OUT_LOGGING_STREAM.get(), System.out);
        assertSame(StandardOutputLogging.ERR_LOGGING_STREAM.get(), System.err);
        assertEquals(StandardOutputLogging.OUT_LOGGING_STREAM.get().getStandardOutputLoggingAdapter().getLevel(), Level.DEBUG);
        assertEquals(StandardOutputLogging.ERR_LOGGING_STREAM.get().getStandardOutputLoggingAdapter().getLevel(), Level.ERROR);
    }
}
