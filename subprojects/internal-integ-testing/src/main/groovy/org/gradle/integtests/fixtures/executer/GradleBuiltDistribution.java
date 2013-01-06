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

/**
 * Provides access to the Gradle distribution that was built.
 */
public class GradleBuiltDistribution implements GradleDistribution {

    private TestDirectoryProvider testDirectoryProvider;

    private IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext();

    public GradleBuiltDistribution(TestDirectoryProvider testWorkDirProvider) {
        this.testDirectoryProvider = testWorkDirProvider;
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

    public TestFile getGradleHomeDir() {
        return buildContext.getGradleHomeDir();
    }

    public String getVersion() {
        return buildContext.getVersion().getVersion();
    }

    public TestFile getBinDistribution() {
        return buildContext.getDistributionsDir().file(String.format("gradle-%s-bin.zip", getVersion()));
    }

    public GradleExecuter executer() {
        return new GradleContextualExecuter(testDirectoryProvider, getGradleHomeDir());
    }

}

