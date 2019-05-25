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
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import org.junit.Test;

public class BuildResultTest {
    @Test
    public void rethrowDoesNothingWhenNoBuildFailure() {
        BuildResult result = new BuildResult(null, null);
        result.rethrowFailure();
    }

    @Test
    public void rethrowsGradleException() {
        Throwable failure = new GradleException();
        BuildResult result = new BuildResult(null, failure);

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
