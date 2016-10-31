/*
 * Copyright 2016 the original author or authors.
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

public class ExecuterDecoratingGradleDistribution implements GradleDistribution {
    private final GradleDistribution distribution;
    private final GradleExecuterDecorator executerDecorator;

    public ExecuterDecoratingGradleDistribution(GradleDistribution distribution, GradleExecuterDecorator decorator) {
        this.distribution = distribution;
        this.executerDecorator = decorator;
    }

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
    public GradleExecuter executer(TestDirectoryProvider testDirectoryProvider, IntegrationTestBuildContext buildContext) {
        return decorateExecuter(distribution.executer(testDirectoryProvider, buildContext));
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
    public boolean isToolingApiLocksBuildActionClasses() {
        return distribution.isToolingApiLocksBuildActionClasses();
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

    private GradleExecuter decorateExecuter(GradleExecuter executer) {
        if (executerDecorator == null) {
            return executer;
        }
        return executerDecorator.decorate(executer);
    }

}
