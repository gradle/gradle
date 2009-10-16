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

import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.gradle.api.logging.Logger;
import org.gradle.util.Clock;

@RunWith(org.jmock.integration.junit4.JMock.class)
public class BuildResultLoggerTest {
    private final JUnit4Mockery context = new JUnit4Mockery(){{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
    private Logger logger;
    private BuildListener listener;
    private Clock buildTimeClock;

    @Before
    public void setup() {
        logger = context.mock(Logger.class);
        buildTimeClock = context.mock(Clock.class);
        listener = new BuildResultLogger(logger, buildTimeClock);
    }

    @Test
    public void logsBuildSuccessAndTotalTime() {
        context.checking(new Expectations(){{
            one(buildTimeClock).getTime();
            will(returnValue("10s"));
            one(logger).lifecycle(String.format("%nBUILD SUCCESSFUL%n"));
            one(logger).lifecycle(with(startsWith("Total time: 10s")));
        }});

        listener.buildFinished(new BuildResult(null, null));
    }

    @Test
    public void logsBuildFailedAndTotalTime() {
        context.checking(new Expectations(){{
            one(buildTimeClock).getTime();
            will(returnValue("10s"));
            one(logger).error(String.format("%nBUILD FAILED%n"));
            one(logger).lifecycle(with(startsWith("Total time: 10s")));
        }});

        listener.buildFinished(new BuildResult(null, new RuntimeException()));
    }

}
