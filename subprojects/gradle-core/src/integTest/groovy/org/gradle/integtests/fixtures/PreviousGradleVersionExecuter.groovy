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

public class PreviousGradleVersionExecuter extends AbstractGradleExecuter implements BasicGradleDistribution {
    private final GradleDistribution dist
    def final String version

    PreviousGradleVersionExecuter(GradleDistribution dist, String version) {
        this.dist = dist
        this.version = version
    }

    def String toString() {
        "Gradle $version"
    }

    @Override
    boolean worksWith(Jvm jvm) {
        return version == '0.9-rc-1' ? jvm.isJava6Compatible() : jvm.isJava5Compatible()
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
        def zipFile = dist.userHomeDir.parentFile.file("gradle-$version-bin.zip")
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
        def matcher = version =~ /(\d+\.\d+).*/
        assert matcher.matches()
        def majorVersion = matcher.group(1) as BigDecimal

        if (version == '0.9' || majorVersion > 0.9) {
            return new URL("http://gradle.artifactoryonline.com/gradle/distributions//gradle-$version-bin.zip")
        } else {
            return new URL("http://dist.codehaus.org/gradle/gradle-$version-bin.zip")
        }
    }

    def TestFile getGradleHomeDir() {
        return findGradleHome()
    }

    private TestFile findGradleHome() {
        // maybe download and unzip distribution
        TestFile versionsDir = dist.distributionsDir.parentFile.file('previousVersions')
        TestFile gradleHome = versionsDir.file("gradle-$version")
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
