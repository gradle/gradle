package org.gradle.invocation;

import org.gradle.StartParameter;
import org.gradle.execution.DefaultTaskExecuter;
import org.gradle.api.internal.project.DefaultProjectRegistry;
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
    private final StartParameter parameter = new StartParameter();
    private final DefaultBuild build = new DefaultBuild(parameter, null, null);

    @Test
    public void usesGradleVersion() {
        assertThat(build.getGradleVersion(), equalTo(new GradleVersion().getVersion()));
    }

    @Test
    public void usesStartParameterForDirLocations() throws IOException {
        parameter.setGradleHomeDir(new File("home"));
        parameter.setGradleUserHomeDir(new File("user"));

        assertThat(build.getGradleHomeDir(), equalTo(new File("home").getCanonicalFile()));
        assertThat(build.getGradleUserHomeDir(), equalTo(new File("user").getCanonicalFile()));
    }

    @Test
    public void createsADefaultProjectRegistry() {
        assertTrue(build.getProjectRegistry().getClass().equals(DefaultProjectRegistry.class));
    }

    @Test
    public void createsATaskGraph() {
        assertTrue(build.getTaskGraph().getClass().equals(DefaultTaskExecuter.class));
    }

    @Test
    public void broadcastsProjectEventsToListeners() {
        final ProjectEvaluationListener listener = context.mock(ProjectEvaluationListener.class);
        final Project project = context.mock(Project.class);
        context.checking(new Expectations() {{
            one(listener).beforeEvaluate(project);
            one(listener).afterEvaluate(project);
        }});

        build.addProjectEvaluationListener(listener);

        build.getProjectEvaluationBroadcaster().beforeEvaluate(project);
        build.getProjectEvaluationBroadcaster().afterEvaluate(project);
    }

    @Test
    public void broadcastsBeforeProjectEvaluateEventsToClosures() {
        final TestClosure closure = context.mock(TestClosure.class);
        final Project project = context.mock(Project.class);
        context.checking(new Expectations(){{
            one(closure).call(project);
        }});

        build.beforeProjectEvaluate(HelperUtil.toClosure(closure));

        build.getProjectEvaluationBroadcaster().beforeEvaluate(project);
    }

    @Test
    public void broadcastsAfterProjectEvaluateEventsToClosures() {
        final TestClosure closure = context.mock(TestClosure.class);
        final Project project = context.mock(Project.class);
        context.checking(new Expectations(){{
            one(closure).call(project);
        }});

        build.afterProjectEvaluate(HelperUtil.toClosure(closure));

        build.getProjectEvaluationBroadcaster().afterEvaluate(project);
    }
}
