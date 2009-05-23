package org.gradle.integtests;

import java.io.File;

public interface GradleDistribution {
    File getGradleHomeDir();

    File getSamplesDir();

    File getUserGuideInfoDir();

    File getUserGuideOutputDir();

    /**
     * Returns a scratch-pad directory for the current test.
     */
    TestFile getTestDir();
}
