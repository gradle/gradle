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

package org.gradle.invocation;

import org.gradle.StartParameter;
import org.gradle.execution.DefaultTaskExecuter;
import org.gradle.api.internal.project.DefaultProjectRegistry;
import org.gradle.api.internal.plugins.DefaultPluginRegistry;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.Project;
import org.gradle.util.GradleVersion;
import org.gradle.util.TestClosure;
import org.gradle.util.HelperUtil;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runners.JUnit4;
import org.junit.runner.RunWith;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Expectations;

import java.io.File;
import java.io.IOException;

@RunWith(JUnit4.class)
public class DefaultBuildTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final StartParameter parameter = new StartParameter(){{
        setPluginPropertiesFile(new File("plugin.properties"));   
    }};
    private final DefaultGradle gradle = new DefaultGradle(parameter, null);

    @Test
    public void usesGradleVersion() {
        assertThat(gradle.getGradleVersion(), equalTo(new GradleVersion().getVersion()));
    }

    @Test
    public void usesStartParameterForDirLocations() throws IOException {
        parameter.setGradleHomeDir(new File("home"));
        parameter.setGradleUserHomeDir(new File("user"));

        assertThat(gradle.getGradleHomeDir(), equalTo(new File("home").getCanonicalFile()));
        assertThat(gradle.getGradleUserHomeDir(), equalTo(new File("user").getCanonicalFile()));
    }

    @Test
    public void createsADefaultProjectRegistry() {
        assertTrue(gradle.getProjectRegistry().getClass().equals(DefaultProjectRegistry.class));
    }

    @Test
    public void createsATaskGraph() {
        assertTrue(gradle.getTaskGraph().getClass().equals(DefaultTaskExecuter.class));
    }

    @Test
    public void createsAPluginRegistry() {
        assertTrue(gradle.getPluginRegistry().getClass().equals(DefaultPluginRegistry.class));
    }

    @Test
    public void broadcastsProjectEventsToListeners() {
        final ProjectEvaluationListener listener = context.mock(ProjectEvaluationListener.class);
        final Project project = context.mock(Project.class);
        final RuntimeException failure = new RuntimeException();
        context.checking(new Expectations() {{
            one(listener).beforeEvaluate(project);
            one(listener).afterEvaluate(project, failure);
        }});

        gradle.addProjectEvaluationListener(listener);

        gradle.getProjectEvaluationBroadcaster().beforeEvaluate(project);
        gradle.getProjectEvaluationBroadcaster().afterEvaluate(project, failure);
    }

    @Test
    public void broadcastsBeforeProjectEvaluateEventsToClosures() {
        final TestClosure closure = context.mock(TestClosure.class);
        final Project project = context.mock(Project.class);
        context.checking(new Expectations(){{
            one(closure).call(project);
        }});

        gradle.beforeProject(HelperUtil.toClosure(closure));

        gradle.getProjectEvaluationBroadcaster().beforeEvaluate(project);
    }

    @Test
    public void broadcastsAfterProjectEvaluateEventsToClosures() {
        final TestClosure closure = context.mock(TestClosure.class);
        final Project project = context.mock(Project.class);
        context.checking(new Expectations(){{
            one(closure).call(project);
        }});

        gradle.afterProject(HelperUtil.toClosure(closure));

        gradle.getProjectEvaluationBroadcaster().afterEvaluate(project, null);
    }
}
