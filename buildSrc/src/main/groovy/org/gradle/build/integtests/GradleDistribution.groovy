package org.gradle.build.integtests

public interface GradleDistribution {
    File getGradleHomeDir()

    File getSamplesDir()

    File getUserGuideInfoDir()

    File getUserGuideOutputDir()
}
