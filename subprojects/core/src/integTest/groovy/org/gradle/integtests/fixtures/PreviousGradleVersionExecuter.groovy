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
package org.gradle.integtests.fixtures

import org.gradle.util.Jvm
import org.gradle.util.TestFile
import org.gradle.util.GradleVersion
import org.gradle.util.DistributionLocator
import org.gradle.util.OperatingSystem

public class PreviousGradleVersionExecuter extends AbstractGradleExecuter implements BasicGradleDistribution {
    private final GradleDistribution dist
    def final GradleVersion version

    PreviousGradleVersionExecuter(GradleDistribution dist, String version) {
        this.dist = dist
        this.version = GradleVersion.version(version)
    }

    def String toString() {
        version.toString()
    }

    String getVersion() {
        return version.version
    }

    boolean worksWith(Jvm jvm) {
        // 0.9-rc-1 was broken for Java 5
        return version == GradleVersion.version('0.9-rc-1') ? jvm.isJava6Compatible() : jvm.isJava5Compatible()
    }

    boolean daemonSupported() {
        if (OperatingSystem.current().isWindows()) {
            // On windows, daemon is ok for anything > 1.0-milestone-3
            return version > GradleVersion.version('1.0-milestone-3')
        } else {
            // Daemon is ok for anything >= 0.9
            return version >= GradleVersion.version('0.9')
        }
    }

    boolean wrapperCanExecute(String version) {
        if (version == '0.8') {
            return false
        }
        if (version == '0.9.1') {
            // 0.9.1 couldn't handle anything with a timestamp whose timezone was behind GMT
            return version.matches('.*+\\d{4}')
        }
        return true
    }

    protected ExecutionResult doRun() {
        ForkingGradleExecuter executer = new ForkingGradleExecuter(gradleHomeDir)
        executer.inDirectory(dist.testDir)
        copyTo(executer)
        return executer.run()
    }

    GradleExecuter executer() {
        this
    }

    TestFile getBinDistribution() {
        def zipFile = dist.userHomeDir.parentFile.file("gradle-$version.version-bin.zip")
        if (!zipFile.isFile()) {
            try {
                URL url = binDistributionUrl
                System.out.println("downloading $url");
                zipFile.copyFrom(url)
            } catch (Throwable t) {
                zipFile.delete()
                throw t
            }
        }
        return zipFile
    }

    private URL getBinDistributionUrl() {
        return new URL(new DistributionLocator().getDistributionFor(version))
    }

    def TestFile getGradleHomeDir() {
        return findGradleHome()
    }

    private TestFile findGradleHome() {
        // maybe download and unzip distribution
        TestFile versionsDir = dist.distributionsDir.parentFile.file('previousVersions')
        TestFile gradleHome = versionsDir.file("gradle-$version.version")
        TestFile markerFile = gradleHome.file('ok.txt')
        if (!markerFile.isFile()) {
            TestFile zipFile = binDistribution
            zipFile.usingNativeTools().unzipTo(versionsDir)
            markerFile.touch()
        }
        return gradleHome
    }

    protected ExecutionFailure doRunWithFailure() {
        throw new UnsupportedOperationException();
    }
}
