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
package org.gradle;

import org.gradle.api.logging.LogLevel;
import org.gradle.logging.StyledTextOutputFactory;
import org.gradle.logging.internal.TestStyledTextOutput;
import org.gradle.util.Clock;
import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(org.jmock.integration.junit4.JMock.class)
public class BuildResultLoggerTest {
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private StyledTextOutputFactory textOutputFactory;
    private TestStyledTextOutput textOutput;
    private BuildListener listener;
    private Clock buildTimeClock;

    @Before
    public void setup() {
        textOutput = new TestStyledTextOutput();
        buildTimeClock = context.mock(Clock.class);
        textOutputFactory = context.mock(StyledTextOutputFactory.class);
        listener = new BuildResultLogger(textOutputFactory, buildTimeClock);
    }

    @Test
    public void logsBuildSuccessAndTotalTime() {
        context.checking(new Expectations() {{
            one(buildTimeClock).getTime();
            will(returnValue("10s"));
            one(textOutputFactory).create(BuildResultLogger.class, LogLevel.LIFECYCLE);
            will(returnValue(textOutput));
        }});

        listener.buildFinished(new BuildResult(null, null));

        assertEquals("\n{success}BUILD SUCCESSFUL{normal}\n\nTotal time: 10s\n", textOutput.getValue());
    }

    @Test
    public void logsBuildFailedAndTotalTime() {
        context.checking(new Expectations() {{
            one(buildTimeClock).getTime();
            will(returnValue("10s"));
            one(textOutputFactory).create(BuildResultLogger.class, LogLevel.LIFECYCLE);
            will(returnValue(textOutput));
        }});

        listener.buildFinished(new BuildResult(null, new RuntimeException()));

        assertEquals("\n{failure}BUILD FAILED{normal}\n\nTotal time: 10s\n", textOutput.getValue());
    }
}
