package org.gradle;

import org.gradle.api.GradleException;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Test;

public class BuildResultTest {
    @Test
    public void rethrowDoesNothingWhenNoBuildFailure() {
        BuildResult result = new BuildResult(null, null);
        result.rethrowFailure();
    }

    @Test
    public void rethrowsRuntimeException() {
        RuntimeException failure = new RuntimeException();
        BuildResult result = new BuildResult(null, failure);

        try {
            result.rethrowFailure();
            fail();
        } catch (Exception e) {
            assertThat(e, sameInstance((Throwable) failure));
        }
    }

    @Test
    public void rethrowsThrowable() {
        Throwable failure = new Throwable();
        BuildResult result = new BuildResult(null, failure);

        try {
            result.rethrowFailure();
            fail();
        } catch (GradleException e) {
            assertThat(e.getMessage(), equalTo("Build aborted because of an internal error."));
            assertThat(e.getCause(), sameInstance(failure));
        }
    }
}
