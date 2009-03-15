package org.gradle.integtests

public interface GradleDistribution {
    File getGradleHomeDir()

    File getSamplesDir()

    File getUserGuideInfoDir()

    File getUserGuideOutputDir()
}
