package org.gradle.integtests;

public interface GradleDistribution {
    TestFile getGradleHomeDir();

    TestFile getSamplesDir();

    TestFile getUserGuideInfoDir();

    TestFile getUserGuideOutputDir();

    /**
     * Returns a scratch-pad directory for the current test.
     */
    TestFile getTestDir();
}
