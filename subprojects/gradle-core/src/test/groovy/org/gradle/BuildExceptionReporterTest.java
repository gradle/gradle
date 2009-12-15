/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.GradleException;
import org.gradle.api.LocationAwareException;
import org.gradle.util.HelperUtil;
import org.hamcrest.Matcher;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;

import java.util.Arrays;

import static org.hamcrest.Matchers.*;

@RunWith(JMock.class)
public class BuildExceptionReporterTest {
    private final JUnit4Mockery context = new JUnit4Mockery(){{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
    private Logger logger = context.mock(Logger.class);
    private StartParameter startParameter = new StartParameter();
    private BuildExceptionReporter reporter = new BuildExceptionReporter(logger, startParameter);

    @Test
    public void reportsBuildFailure() {
        final GradleException exception = new GradleException("<message>");
        final Matcher<String> errorMessage = allOf(containsString("Build failed with an exception."),
                containsString("<message>"));

        context.checking(new Expectations() {{
            one(logger).error(with(errorMessage));
        }});

        reporter.buildFinished(HelperUtil.createBuildResult(exception));
    }

    @Test
    public void reportsBuildFailureWhenCauseHasNoMessage() {
        final GradleException exception = new GradleException();
        final Matcher<String> errorMessage = allOf(containsString("Build failed with an exception."),
                containsString(GradleException.class.getName() + " (no error message)"));

        context.checking(new Expectations() {{
            one(logger).error(with(errorMessage));
        }});

        reporter.buildFinished(HelperUtil.createBuildResult(exception));
    }

    @Test
    public void reportsLocationAwareException() {
        final TestException exception = exception("<location>", "<message>", new RuntimeException("<cause>"));

        final Matcher<String> errorMessage = allOf(containsString("Build failed with an exception."),
                containsString("<location>"),
                containsString("<message>"),
                containsString("Cause: <cause>"));

        context.checking(new Expectations() {{
            one(logger).error(with(errorMessage));
        }});

        reporter.buildFinished(HelperUtil.createBuildResult(exception));
    }

    private TestException exception(final String location, final String message, final Throwable... causes) {
        final TestException testException = context.mock(TestException.class);
        context.checking(new Expectations() {{
            allowing(testException).getLocation();
            will(returnValue(location));
            allowing(testException).getOriginalMessage();
            will(returnValue(message));
            allowing(testException).getReportableCauses();
            will(returnValue(Arrays.asList(causes)));
        }});
        return testException;
    }

    @Test
    public void reportsLocationAwareExceptionWithMultipleCauses() {
        final TestException exception = exception("<location>", "<message>", new RuntimeException("<outer>"),
                new RuntimeException("<cause>"));

        final Matcher<String> errorMessage = allOf(containsString("Build failed with an exception."),
                containsString("<location>"),
                containsString("<message>"),
                containsString("Cause: <outer>"),
                containsString("<cause>"));

        context.checking(new Expectations() {{
            one(logger).error(with(errorMessage));
        }});

        reporter.buildFinished(HelperUtil.createBuildResult(exception));
    }

    @Test
    public void reportsLocationAwareExceptionWhenCauseHasNoMessage() {
        final TestException exception = exception("<location>", "<message>", new RuntimeException());

        final Matcher<String> errorMessage = allOf(containsString("Build failed with an exception."),
                containsString("<location>"),
                containsString("<message>"),
                containsString("Cause: " + RuntimeException.class.getName() + " (no error message)"));

        context.checking(new Expectations() {{
            one(logger).error(with(errorMessage));
        }});

        reporter.buildFinished(HelperUtil.createBuildResult(exception));
    }

    @Test
    public void reportsBuildFailureWhenOptionsHaveNotBeenSet() {
        final GradleException exception = new GradleException("<message>");
        final Matcher<String> errorMessage = allOf(containsString("Build failed with an exception."),
                containsString("<message>"));

        context.checking(new Expectations() {{
            one(logger).error(with(errorMessage));
        }});

        reporter = new BuildExceptionReporter(logger, startParameter);
        reporter.buildFinished(HelperUtil.createBuildResult(exception));
    }

    @Test
    public void reportsInternalFailure() {
        final RuntimeException failure = new RuntimeException("<message>");

        context.checking(new Expectations() {{
            one(logger).error(with(containsString("Build aborted because of an internal error.")), with(sameInstance(failure)));
        }});

        reporter.buildFinished(HelperUtil.createBuildResult(failure));
    }

    @Test
    public void reportsInternalFailureWhenOptionsHaveNotBeenSet() {
        final RuntimeException failure = new RuntimeException("<message>");

        context.checking(new Expectations() {{
            one(logger).error(with(containsString("Build aborted because of an internal error.")), with(sameInstance(failure)));
        }});

        reporter = new BuildExceptionReporter(logger, startParameter);
        reporter.buildFinished(HelperUtil.createBuildResult(failure));
    }

    @Test
    public void doesNothingWheBuildIsSuccessful() {
        reporter.buildFinished(HelperUtil.createBuildResult(null));
    }

    public abstract class TestException extends GradleException implements LocationAwareException {
    }

}
