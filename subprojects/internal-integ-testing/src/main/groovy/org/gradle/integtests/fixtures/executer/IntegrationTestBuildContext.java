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

import org.gradle.api.JavaVersion;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.test.fixtures.file.TestDirectoryProvider;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.util.GradleVersion;
import org.gradle.util.VersionNumber;

import java.io.File;

/**
 * Provides values that are set during the build, or defaulted when not running in a build context (e.g. IDE).
 */
public class IntegrationTestBuildContext {

    private GradleExecuterDecorator executerDecorator;

    public TestFile getGradleHomeDir() {
        return file("integTest.gradleHomeDir", null);
    }

    public TestFile getSamplesDir() {
        return file("integTest.samplesdir", String.format("%s/samples", getGradleHomeDir()));
    }

    public TestFile getUserGuideOutputDir() {
        return file("integTest.userGuideOutputDir", "subprojects/docs/src/samples/userguideOutput");
    }

    public TestFile getUserGuideInfoDir() {
        return file("integTest.userGuideInfoDir", "subprojects/docs/build/src");
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

    public TestFile getTmpDir() {
        return file("integTest.tmpDir", "build/tmp");
    }

    public TestFile getNativeServicesDir() {
        return getGradleUserHomeDir().file("native");
    }

    public GradleVersion getVersion() {
        return GradleVersion.current();
    }

    public TestFile getFatToolingApiJar() {
        TestFile toolingApiShadedJarDir = file("integTest.toolingApiShadedJarDir", "subprojects/tooling-api/build/shaded-jar");
        TestFile fatToolingApiJar = new TestFile(toolingApiShadedJarDir, String.format("gradle-tooling-api-shaded-%s.jar", getVersion().getVersion()));

        if (!fatToolingApiJar.exists()) {
            throw new IllegalStateException(String.format("The fat Tooling API JAR file does not exist: %s", fatToolingApiJar.getAbsolutePath()));
        }

        return fatToolingApiJar;
    }

    public GradleExecuterDecorator getExecuterDecorator() {
        return executerDecorator;
    }

    public void setExecuterDecorator(GradleExecuterDecorator executerDecorator) {
        this.executerDecorator = executerDecorator;
    }

    public GradleDistribution distribution(String version) {
        return decorateDistribution(doCreateDistribution(version));
    }

    private GradleDistribution doCreateDistribution(String version) {
        if (version.equals(getVersion().getVersion())) {
            return new UnderDevelopmentGradleDistribution();
        }
        TestFile previousVersionDir = getGradleUserHomeDir().getParentFile().file("previousVersion");
        if(version.startsWith("#")){
            return new BuildServerGradleDistribution(version, previousVersionDir.file(version));
        }
        return new ReleasedGradleDistribution(version, previousVersionDir.file(version));
    }

    private GradleDistribution decorateDistribution(final GradleDistribution distribution) {
        return new GradleDistribution() {
            @Override
            public TestFile getGradleHomeDir() {
                return distribution.getGradleHomeDir();
            }

            @Override
            public TestFile getBinDistribution() {
                return distribution.getBinDistribution();
            }

            @Override
            public GradleVersion getVersion() {
                return distribution.getVersion();
            }

            @Override
            public GradleExecuter executer(TestDirectoryProvider testDirectoryProvider) {
                return decorateExecuter(distribution.executer(testDirectoryProvider));
            }

            @Override
            public boolean worksWith(Jvm jvm) {
                return distribution.worksWith(jvm);
            }

            @Override
            public boolean worksWith(OperatingSystem os) {
                return distribution.worksWith(os);
            }

            @Override
            public boolean isDaemonIdleTimeoutConfigurable() {
                return distribution.isDaemonIdleTimeoutConfigurable();
            }

            @Override
            public boolean isToolingApiSupported() {
                return distribution.isToolingApiSupported();
            }

            @Override
            public boolean isToolingApiTargetJvmSupported(JavaVersion javaVersion) {
                return distribution.isToolingApiTargetJvmSupported(javaVersion);
            }

            @Override
            public boolean isToolingApiNonAsciiOutputSupported() {
                return distribution.isToolingApiNonAsciiOutputSupported();
            }

            @Override
            public boolean isToolingApiLoggingInEmbeddedModeSupported() {
                return distribution.isToolingApiLoggingInEmbeddedModeSupported();
            }

            @Override
            public boolean isToolingApiDaemonBaseDirSupported() {
                return distribution.isToolingApiDaemonBaseDirSupported();
            }

            @Override
            public boolean isToolingApiEventsInEmbeddedModeSupported() {
                return distribution.isToolingApiEventsInEmbeddedModeSupported();
            }

            @Override
            public VersionNumber getArtifactCacheLayoutVersion() {
                return distribution.getArtifactCacheLayoutVersion();
            }

            @Override
            public boolean isOpenApiSupported() {
                return distribution.isOpenApiSupported();
            }

            @Override
            public boolean wrapperCanExecute(GradleVersion version) {
                return distribution.wrapperCanExecute(version);
            }

            @Override
            public boolean isSupportsSpacesInGradleAndJavaOpts() {
                return distribution.isSupportsSpacesInGradleAndJavaOpts();
            }

            @Override
            public boolean isFullySupportsIvyRepository() {
                return distribution.isFullySupportsIvyRepository();
            }

            @Override
            public boolean isWrapperSupportsGradleUserHomeCommandLineOption() {
                return distribution.isWrapperSupportsGradleUserHomeCommandLineOption();
            }
        };
    }

    private GradleExecuter decorateExecuter(GradleExecuter executer) {
        if (executerDecorator == null) {
            return executer;
        }
        return executerDecorator.decorate(executer);
    }

    private static TestFile file(String propertyName, String defaultFile) {
        String path = System.getProperty(propertyName, defaultFile);
        if (path == null) {
            throw new RuntimeException(String.format("You must set the '%s' property to run the integration tests. The default passed was: '%s'",
                    propertyName, defaultFile));
        }
        return new TestFile(new File(path));
    }

    public interface GradleExecuterDecorator {
        GradleExecuter decorate(GradleExecuter executer);
    }
}
