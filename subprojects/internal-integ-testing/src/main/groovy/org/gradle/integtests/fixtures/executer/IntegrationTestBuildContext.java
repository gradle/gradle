/*
 * Copyright 2012 the original author or authors.
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

import com.google.common.base.CaseFormat;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.util.GradleVersion;

import javax.annotation.Nullable;
import java.io.File;

/**
 * Provides values that are set during the build, or defaulted when not running in a build context (e.g. IDE).
 */
public class IntegrationTestBuildContext {
    // Collect this early, as the process' current directory can change during embedded test execution
    public static final TestFile TEST_DIR = new TestFile(new File(".").toURI());
    public static final IntegrationTestBuildContext INSTANCE = new IntegrationTestBuildContext();

    public TestFile getGradleHomeDir() {
        return file("integTest.gradleHomeDir", null);
    }

    public TestFile getSamplesDir() {
        String hintForMissingSamples = String.format("Run 'gradle %s:copySamples'.", getCurrentSubprojectName());
        return file("integTest.samplesdir", String.format("%s/samples", getGradleHomeDir())).assertIsDir(hintForMissingSamples);
    }

    public TestFile getDistributionsDir() {
        return file("integTest.distsDir", "build/distributions");
    }

    public TestFile getLibsRepo() {
        return file("integTest.libsRepo", "build/repo");
    }

    public TestFile getDaemonBaseDir() {
        return file("org.gradle.integtest.daemon.registry", "build/daemon");
    }

    public TestFile getGradleUserHomeDir() {
        return file("integTest.gradleUserHomeDir", "intTestHomeDir").file("worker-1");
    }

    @Nullable
    public TestFile getGradleGeneratedApiJarCacheDir() {
        return optionalFile("integTest.gradleGeneratedApiJarCacheDir");
    }

    public TestFile getTmpDir() {
        return file("integTest.tmpDir", "build/tmp");
    }

    public TestFile getNativeServicesDir() {
        return getGradleUserHomeDir().file("native");
    }

    public GradleVersion getVersion() {
        return GradleVersion.current();
    }

    public String getCurrentSubprojectName() {
        return CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, getGradleHomeDir().getParentFile().getParentFile().getName());
    }

    /**
     * The timestamped version used in the docs and the bin and all zips. This should be different to {@link GradleVersion#getVersion()}.
     * Note that the binary distribution used for testing (binZip and intTestImage) has {@link GradleVersion#getVersion()} as version.
     *
     * @return timestamped version
     */
    public GradleVersion getDistZipVersion() {
        return GradleVersion.version(System.getProperty("integTest.distZipVersion", GradleVersion.current().getVersion()));
    }

    public TestFile getFatToolingApiJar() {
        TestFile toolingApiShadedJarDir = file("integTest.toolingApiShadedJarDir", "subprojects/tooling-api/build/shaded-jar");
        TestFile fatToolingApiJar = new TestFile(toolingApiShadedJarDir, String.format("gradle-tooling-api-shaded-%s.jar", getVersion().getBaseVersion().getVersion()));

        if (!fatToolingApiJar.exists()) {
            throw new IllegalStateException(String.format("The fat Tooling API JAR file does not exist: %s", fatToolingApiJar.getAbsolutePath()));
        }

        return fatToolingApiJar;
    }

    public GradleDistribution distribution(String version) {
        if (version.equals(getVersion().getVersion())) {
            return new UnderDevelopmentGradleDistribution();
        }
        TestFile previousVersionDir = getGradleUserHomeDir().getParentFile().file("previousVersion");
        if (version.startsWith("#")) {
            return new BuildServerGradleDistribution(version, previousVersionDir.file(version));
        }

        if (LocallyBuiltGradleDistribution.isLocallyBuiltVersion(version)) {
            return new LocallyBuiltGradleDistribution(version);
        }
        return new ReleasedGradleDistribution(version, previousVersionDir.file(version));
    }

    protected static TestFile file(String propertyName, String defaultPath) {
        TestFile testFile = optionalFile(propertyName);
        if (testFile != null) {
            return testFile;
        }
        if (defaultPath == null) {
            throw new RuntimeException("You must set the '" + propertyName + "' property to run the integration tests.");
        }
        return testFile(defaultPath);
    }

    @Nullable
    private static TestFile optionalFile(String propertyName) {
        String path = System.getProperty(propertyName);
        // MODULE_WORKING_DIR doesn't seem to work correctly and MODULE_DIR seems to be in `.idea/modules/<path-to-subproject>`
        // See https://youtrack.jetbrains.com/issue/IDEA-194910
        return path != null ? new TestFile(new File(path.replace(".idea/modules/", ""))) : null;
    }

    private static TestFile testFile(String path) {
        File file = new File(path);
        return file.isAbsolute()
            ? new TestFile(file)
            : new TestFile(TEST_DIR.file(path).getAbsoluteFile());
    }

}
