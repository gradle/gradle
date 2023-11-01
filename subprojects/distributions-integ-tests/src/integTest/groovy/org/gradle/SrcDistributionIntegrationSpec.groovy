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

package org.gradle

import org.apache.tools.ant.taskdefs.Expand
import org.gradle.api.logging.configuration.WarningMode
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.AntUtil
import org.gradle.util.internal.ToBeImplemented

import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.gradlePluginRepositoryMirrorUrl
import static org.gradle.test.fixtures.server.http.MavenHttpPluginRepository.PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY

class SrcDistributionIntegrationSpec extends DistributionIntegrationSpec {

    @Override
    String getDistributionLabel() {
        "src"
    }

    @Override
    int getMaxDistributionSizeBytes() {
        return 51 * 1024 * 1024
    }

    @Override
    int getLibJarsCount() {
        0
    }

    @Requires(UnitTestPreconditions.NotWindows)
    @ToBeFixedForConfigurationCache
    def sourceZipContents() {
        given:
        TestFile contentsDir = unpackDistribution()

        expect:
        !contentsDir.file(".git").exists()

        when:
        executer.with {
            inDirectory(contentsDir)
            usingExecutable('gradlew')
            withArgument("--no-configuration-cache") // TODO:configuration-cache remove me
            withTasks(':distributions-full:binDistributionZip')
            withArgument("-D${PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY}=${gradlePluginRepositoryMirrorUrl()}")
            withArgument("-Porg.gradle.java.installations.paths=${Jvm.current().javaHome.absolutePath}")
            withEnvironmentVars([BUILD_BRANCH: System.getProperty("gradleBuildBranch"), BUILD_COMMIT_ID: System.getProperty("gradleBuildCommitId")])
            withWarningMode(WarningMode.None)
            noDeprecationChecks()
        }.run()

        then:
        File binZip = contentsDir.file("subprojects/distributions-full/build/distributions").listFiles().find() { it.name.endsWith("-bin.zip") }
        binZip.exists()

        when:
        Expand unpack = new Expand()
        unpack.src = binZip
        unpack.dest = contentsDir.file('build/distributions/unzip')
        AntUtil.execute(unpack)

        then:
        TestFile unpackedRoot = new TestFile(contentsDir.file('build/distributions/unzip').listFiles().first())
        unpackedRoot.file("bin/gradle").exists()
    }

    @ToBeImplemented("https://github.com/gradle/gradle/issues/21114")
    @Requires(UnitTestPreconditions.NotWindows)
    def "source distribution must contain generated sources"() {
        given:
        TestFile contentsDir = unpackDistribution()

        when:
        def generatedSourceName = "/org/gradle/kotlin/dsl/KotlinDependencyExtensions.kt"
        def foundGeneratedSources = false
        contentsDir.eachFileRecurse {
            if (it.absolutePath.endsWith(generatedSourceName)) {
                foundGeneratedSources = true
            }
        }

        then:
        // TODO: remove negation when fixed
        !foundGeneratedSources
    }

}
