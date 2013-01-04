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

package org.gradle.integtests.fixtures.executer;

import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.test.fixtures.file.TestDirectoryProvider;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.util.GradleVersion;

import java.io.File;

/**
 * Provides access to a Gradle distribution for integration testing.
 */
public class GradleDistribution implements BasicGradleDistribution {
    private static final TestFile USER_HOME_DIR;
    private static final TestFile GRADLE_HOME_DIR;
    private static final TestFile SAMPLES_DIR;
    private static final TestFile USER_GUIDE_OUTPUT_DIR;
    private static final TestFile USER_GUIDE_INFO_DIR;
    private static final TestFile DISTS_DIR;
    private static final TestFile LIBS_REPO;
    private static final TestFile DAEMON_BASE_DIR;

    private TestFile userHome;
    private boolean usingIsolatedDaemons;
    private TestDirectoryProvider testWorkDirProvider;

    static {
        USER_HOME_DIR = file("integTest.gradleUserHomeDir", "intTestHomeDir").file("worker-1");
        GRADLE_HOME_DIR = file("integTest.gradleHomeDir", null);
        SAMPLES_DIR = file("integTest.samplesdir", String.format("%s/samples", GRADLE_HOME_DIR));
        USER_GUIDE_OUTPUT_DIR = file("integTest.userGuideOutputDir",
                "subprojects/docs/src/samples/userguideOutput");
        USER_GUIDE_INFO_DIR = file("integTest.userGuideInfoDir", "subprojects/docs/build/src");
        DISTS_DIR = file("integTest.distsDir", "build/distributions");
        LIBS_REPO = file("integTest.libsRepo", "build/repo");
        DAEMON_BASE_DIR = file("org.gradle.integtest.daemon.registry", "build/daemon");
    }

    public GradleDistribution(TestDirectoryProvider testWorkDirProvider) {
        this.userHome = USER_HOME_DIR;
        this.testWorkDirProvider = testWorkDirProvider;
    }

    @Override
    public String toString() {
        return String.format("Gradle %s", GradleVersion.current().getVersion());
    }

    public boolean worksWith(Jvm jvm) {
        // Works with anything >= Java 5
        return jvm.getJavaVersion().isJava5Compatible();
    }

    public boolean worksWith(OperatingSystem os) {
        return true;
    }

    public boolean isDaemonSupported() {
        return true;
    }

    public boolean isDaemonIdleTimeoutConfigurable() {
        return true;
    }

    public boolean isOpenApiSupported() {
        return true;
    }

    public boolean isToolingApiSupported() {
        return true;
    }

    public int getArtifactCacheLayoutVersion() {
        return 23;
    }

    public boolean wrapperCanExecute(String version) {
        // Current wrapper works with anything > 0.8
        return GradleVersion.version(version).compareTo(GradleVersion.version("0.8")) > 0;
    }

    public TestFile getDaemonBaseDir() {
        if (usingIsolatedDaemons) {
            return getTestWorkDir().file("daemon");
        } else {
            return DAEMON_BASE_DIR;
        }
    }

    public void requireOwnUserHomeDir() {
        userHome = getTestWorkDir().file("user-home");
    }

    public boolean isUsingIsolatedDaemons() {
        return usingIsolatedDaemons;
    }

    public void requireIsolatedDaemons() {
        this.usingIsolatedDaemons = true;
    }

    private static TestFile file(String propertyName, String defaultFile) {
        String path = System.getProperty(propertyName, defaultFile);
        if (path == null) {
            throw new RuntimeException(String.format("You must set the '%s' property to run the integration tests. The default passed was: '%s'",
                    propertyName, defaultFile));
        }
        return new TestFile(new File(path));
    }

    /**
     * The user home dir used for the current test. This is usually shared with other tests unless
     * {@link #requireOwnUserHomeDir()} is called.
     */
    public TestFile getUserHomeDir() {
        return userHome;
    }

    /**
     * The distribution for the current test. This is usually shared with other tests.
     */
    public TestFile getGradleHomeDir() {
        return GRADLE_HOME_DIR;
    }

    public String getVersion() {
        return GradleVersion.current().getVersion();
    }

    public TestFile getBinDistribution() {
        return getDistributionsDir().file(String.format("gradle-%s-bin.zip", getVersion()));
    }

    /**
     * The samples from the distribution. These are usually shared with other tests.
     */
    public TestFile getSamplesDir() {
        return SAMPLES_DIR;
    }

    public TestFile getUserGuideInfoDir() {
        return USER_GUIDE_INFO_DIR;
    }

    public TestFile getUserGuideOutputDir() {
        return USER_GUIDE_OUTPUT_DIR;
    }

    /**
     * The directory containing the distribution Zips
     */
    public TestFile getDistributionsDir() {
        return DISTS_DIR;
    }

    public TestFile getLibsRepo() {
        return LIBS_REPO;
    }

    public TestFile getPreviousVersionsDir() {
        return USER_HOME_DIR.getParentFile().file("previousVersion");
    }

    /**
     * Returns true if the given file is either part of the distributions, samples, or test files.
     */
    public boolean isFileUnderTest(File file) {
        return GRADLE_HOME_DIR.isSelfOrDescendent(file)
                || SAMPLES_DIR.isSelfOrDescendent(file)
                || getTestWorkDir().isSelfOrDescendent(file)
                || getUserHomeDir().isSelfOrDescendent(file);
    }

    private TestFile getTestWorkDir() {
        return testWorkDirProvider.getTestDirectory();
    }

    /**
     * Returns a previous version of Gradle.
     *
     * @param version The Gradle version
     * @return An executer
     */
    public BasicGradleDistribution previousVersion(String version) {
        if (version.equals(this.getVersion())) {
            return this;
        }
        return new PreviousGradleVersionExecuter(version, getPreviousVersionsDir().file(version));
    }

    public GradleDistributionExecuter executer() {
        return new GradleDistributionExecuter(this, testWorkDirProvider);
    }


}

