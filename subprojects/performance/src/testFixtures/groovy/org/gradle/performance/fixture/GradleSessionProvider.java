package org.gradle.performance.fixture;

import org.gradle.test.fixtures.file.TestDirectoryProvider;

public class GradleSessionProvider {

    private final TestDirectoryProvider testDirectoryProvider;

    public GradleSessionProvider(TestDirectoryProvider testDirectoryProvider) {
        this.testDirectoryProvider = testDirectoryProvider;
    }

    public GradleSession session(GradleInvocationSpec buildSpec) {
        if (buildSpec.isUseToolingApi()) {
            return new ToolingApiBackedGradleSession(buildSpec, testDirectoryProvider);
        } else {
            return new GradleExecuterBackedSession(buildSpec, testDirectoryProvider);
        }

    }

}
