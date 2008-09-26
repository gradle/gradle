package org.gradle.invocation;

import org.gradle.StartParameter;
import org.gradle.api.internal.project.DefaultProjectRegistry;
import org.gradle.api.internal.project.IProjectRegistry;
import org.gradle.util.GradleVersion;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Test;

import java.io.File;

public class DefaultBuildTest {
    @Test
    public void usesGradleVersion() {
        DefaultBuild build = new DefaultBuild(null, null, null);
        assertThat(build.getGradleVersion(), equalTo(new GradleVersion().getVersion()));
    }

    @Test
    public void usesStartParameterForDirLocations() {
        StartParameter parameter = new StartParameter();
        parameter.setGradleHomeDir(new File("home"));
        parameter.setGradleUserHomeDir(new File("user"));

        DefaultBuild build = new DefaultBuild(null, parameter, null);

        assertThat(build.getGradleHomeDir(), equalTo(new File("home")));
        assertThat(build.getGradleUserHomeDir(), equalTo(new File("user")));
    }

    @Test
    public void createsADefaultProjectRegistry() {
        DefaultBuild build = new DefaultBuild(null, null, null);
        assertTrue(build.getProjectRegistry().getClass().equals(DefaultProjectRegistry.class));
    }
}
