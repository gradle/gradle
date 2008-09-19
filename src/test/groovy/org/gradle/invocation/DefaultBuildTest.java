package org.gradle.invocation;

import org.gradle.StartParameter;
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

        DefaultBuild build = new DefaultBuild(null, null, parameter);

        assertThat(build.getGradleHomeDir(), equalTo(new File("home")));
        assertThat(build.getGradleUserHomeDir(), equalTo(new File("user")));
    }

}
