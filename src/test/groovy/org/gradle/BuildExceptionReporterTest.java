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

import joptsimple.OptionSet;
import org.gradle.api.GradleException;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;

@RunWith(org.jmock.integration.junit4.JMock.class)
public class BuildExceptionReporterTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private Logger logger;
    private BuildExceptionReporter reporter;

    @Before
    public void setup() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        logger = context.mock(Logger.class);
        final OptionSet optionSet = context.mock(OptionSet.class);
        reporter = new BuildExceptionReporter(logger);
        reporter.setOptions(optionSet);

        context.checking(new Expectations() {{
            allowing(optionSet).has(with(aNonNull(String.class)));
            will(returnValue(false));
        }});
    }

    @Test
    public void reportsBuildFailure() {
        final GradleException exception = new GradleException("<message>");

        context.checking(new Expectations() {{
            one(logger).error(with(containsString("Build failed with an exception.")));
            one(logger).error(String.format("Exception: %s", exception));
        }});

        reporter.buildFinished(new BuildResult(null, exception));
    }

    @Test
    public void reportsBuildFailureWhenOptionsHaveNotBeenSet() {
        final GradleException exception = new GradleException("<message>");

        context.checking(new Expectations() {{
            one(logger).error(with(containsString("Build failed with an exception.")));
            one(logger).error(String.format("Exception: %s", exception));
        }});

        reporter = new BuildExceptionReporter(logger);
        reporter.buildFinished(new BuildResult(null, exception));
    }

    @Test
    public void reportsInternalFailure() {
        final RuntimeException failure = new RuntimeException("<message>");

        context.checking(new Expectations() {{
            one(logger).error(with(containsString("Build aborted because of an internal error.")), with(sameInstance(failure)));
        }});

        reporter.buildFinished(new BuildResult(null, failure));
    }

    @Test
    public void reportsInternalFailureWhenOptionsHaveNotBeenSet() {
        final RuntimeException failure = new RuntimeException("<message>");

        context.checking(new Expectations() {{
            one(logger).error(with(containsString("Build aborted because of an internal error.")), with(sameInstance(failure)));
        }});

        reporter = new BuildExceptionReporter(logger);
        reporter.buildFinished(new BuildResult(null, failure));
    }

    @Test
    public void doesNothingWheBuildIsSuccessful() {
        reporter.buildFinished(new BuildResult(null, null));
    }

}
