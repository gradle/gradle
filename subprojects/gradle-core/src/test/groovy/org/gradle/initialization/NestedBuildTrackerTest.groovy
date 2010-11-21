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
package org.gradle.initialization

import org.gradle.BuildResult
import org.gradle.api.internal.GradleInternal
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

@RunWith(JMock.class)
class NestedBuildTrackerTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final NestedBuildTracker tracker = new NestedBuildTracker()

    @Test
    public void noCurrentBuildByDefault() {
        assertThat(tracker.currentBuild, nullValue())
    }

    @Test
    public void setsCurrentBuildWhenBuildStartsAndStops() {
        def build = context.mock(GradleInternal.class, 'build1')
        def build2 = context.mock(GradleInternal.class, 'build2')

        tracker.buildStarted(build)
        assertThat(tracker.currentBuild, sameInstance(build))

        tracker.buildFinished(new BuildResult(build, null))
        assertThat(tracker.currentBuild, nullValue())

        tracker.buildStarted(build2)
        assertThat(tracker.currentBuild, sameInstance(build2))

        tracker.buildFinished(new BuildResult(build2, null))
        assertThat(tracker.currentBuild, nullValue())
    }

    @Test
    public void pushesBuildWhenBuildStartsWhileOneIsCurrentlyRunning() {
        def build = context.mock(GradleInternal.class, 'build1')
        def build2 = context.mock(GradleInternal.class, 'build2')

        tracker.buildStarted(build)
        assertThat(tracker.currentBuild, sameInstance(build))

        tracker.buildStarted(build2)
        assertThat(tracker.currentBuild, sameInstance(build2))

        tracker.buildFinished(new BuildResult(build2, null))
        assertThat(tracker.currentBuild, sameInstance(build))

        tracker.buildFinished(new BuildResult(build, null))
        assertThat(tracker.currentBuild, nullValue())
    }
}


