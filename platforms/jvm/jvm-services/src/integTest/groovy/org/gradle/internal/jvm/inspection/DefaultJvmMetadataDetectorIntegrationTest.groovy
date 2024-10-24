/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.jvm.inspection

import org.gradle.api.internal.file.TestFiles
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.SystemProperties
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.toolchain.internal.InstallationLocation
import org.gradle.process.internal.DefaultExecHandleBuilder
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.util.TestUtil

import java.util.concurrent.Executors

class DefaultJvmMetadataDetectorIntegrationTest extends AbstractIntegrationSpec {

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "works on real installation"() {
        when:
        def detector = new DefaultJvmMetadataDetector(
                () -> new DefaultExecHandleBuilder(TestUtil.objectFactory(), TestFiles.pathToFileResolver(), Executors.newCachedThreadPool()),
                TestFiles.tmpDirTemporaryFileProvider(new File(SystemProperties.getInstance().getJavaIoTmpDir()))
        )
        Jvm jvm = AvailableJavaHomes.differentJdk //the detector has special handling for the current JVM
        def javaHome = InstallationLocation.userDefined(jvm.getJavaHome(), "test")
        def metadata = detector.getMetadata(javaHome)

        then:
        metadata.javaHome == jvm.javaHome.toPath()
        metadata.languageVersion == jvm.javaVersion
    }

}
