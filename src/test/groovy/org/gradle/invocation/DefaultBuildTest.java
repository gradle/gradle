package org.gradle.invocation;

import org.gradle.StartParameter;
import org.gradle.execution.DefaultTaskExecuter;
import org.gradle.api.internal.project.DefaultProjectRegistry;
import org.gradle.util.GradleVersion;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class DefaultBuildTest {
    @Test
    public void usesGradleVersion() {
        DefaultBuild build = new DefaultBuild(null, null);
        assertThat(build.getGradleVersion(), equalTo(new GradleVersion().getVersion()));
    }

    @Test
    public void usesStartParameterForDirLocations() throws IOException {
        StartParameter parameter = new StartParameter();
        parameter.setGradleHomeDir(new File("home"));
        parameter.setGradleUserHomeDir(new File("user"));

        DefaultBuild build = new DefaultBuild(parameter, null);

        assertThat(build.getGradleHomeDir(), equalTo(new File("home").getCanonicalFile()));
        assertThat(build.getGradleUserHomeDir(), equalTo(new File("user").getCanonicalFile()));
    }

    @Test
    public void createsADefaultProjectRegistry() {
        DefaultBuild build = new DefaultBuild(null, null);
        assertTrue(build.getProjectRegistry().getClass().equals(DefaultProjectRegistry.class));
    }

    @Test
    public void createsATaskGraph() {
        DefaultBuild build = new DefaultBuild(null, null);
        assertTrue(build.getTaskGraph().getClass().equals(DefaultTaskExecuter.class));
    }
}
