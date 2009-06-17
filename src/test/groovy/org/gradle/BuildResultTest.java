package org.gradle;

import org.gradle.api.GradleException;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Test;

public class BuildResultTest {
    @Test
    public void rethrowDoesNothingWhenNoBuildFailure() {
        BuildResult result = new BuildResult(null, null, null);
        result.rethrowFailure();
    }

    @Test
    public void rethrowsGradleException() {
        Throwable failure = new GradleException();
        BuildResult result = new BuildResult(null, null, failure);

        try {
            result.rethrowFailure();
            fail();
        } catch (Exception e) {
            assertThat(e, sameInstance(failure));
        }
    }

    @Test
    public void rethrowWrapsOtherExceptions() {
        Throwable failure = new RuntimeException();
        BuildResult result = new BuildResult(null, null, failure);

        try {
            result.rethrowFailure();
            fail();
        } catch (GradleException e) {
            assertThat(e.getMessage(), equalTo("Build aborted because of an internal error."));
            assertThat(e.getCause(), sameInstance(failure));
        }
    }
}
