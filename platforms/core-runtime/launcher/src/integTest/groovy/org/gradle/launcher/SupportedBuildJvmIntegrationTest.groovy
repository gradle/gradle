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

package org.gradle.launcher

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.buildconfiguration.fixture.DaemonJvmPropertiesFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Issue

/**
 * Tests that the Gradle daemon can be started with certain JDKs.
 * <p>
 * Should only contain tests not related to java versions. See {@link SupportedBuildJvmVersionIntegrationTest}.
 */
class SupportedBuildJvmIntegrationTest extends AbstractIntegrationSpec implements DaemonJvmPropertiesFixture, JavaToolchainFixture {

    @Requires(UnitTestPreconditions.Symlinks)
    def "can start Gradle with a JDK that contains symlinks"() {
        // Zulu sets their Java distribution up like this
        def installedJdk = Jvm.current().javaHome
        def symlinkedJdk = file("symlink-jdk")
        installedJdk.listFiles().each {
            symlinkedJdk.file(it.name).createLink(it)
        }
        propertiesFile.writeProperties("org.gradle.java.home": symlinkedJdk.canonicalPath)

        expect:
        succeeds("help")
    }

    // This test deletes a JDK installation while the daemon is running.
    // This is difficult to setup on Windows since you can't delete files
    // that are in use.
    @Requires(UnitTestPreconditions.NotWindows)
    @Issue("https://github.com/gradle/gradle/issues/16816")
    def "can successful start after a running daemon's JDK has been removed"() {
        def installedJdk = Jvm.current()
        def jdkToRemove = file("removed-jdk")
        jdkToRemove.mkdir()
        new TestFile(installedJdk.javaHome).copyTo(jdkToRemove)

        // start one JVM with jdk to remove
        executer.withJavaHome(jdkToRemove.absolutePath)
        succeeds("help")

        when:
        // remove the JDK
        jdkToRemove.deleteDir()
        // don't ask for the removed JDK now
        executer.withJvm(installedJdk)
        then:
        // try to start another build
        succeeds("help")
    }
}
