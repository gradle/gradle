/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class NativeServicesIntegrationTest extends AbstractIntegrationSpec {

    def "native services libs are unpacked to gradle user home dir"() {
        given:
        def nativeDir = new File(executer.gradleUserHomeDir, "native")

        when:
        executer.withArguments("-q").run()

        then:
        nativeDir.directory
    }

    @Requires(adhoc = { NativeServicesIntegrationTest.isMountedNoexec('/tmp') })
    def "gradle runs with a tmp dir mounted with noexec option"() {
        given:
        NativeServicesTestFixture.initialize()
        executer.requireGradleDistribution().withNoExplicitTmpDir()
        when:
        executer.withBuildJvmOpts("-Djava.io.tmpdir=/tmp")

        then:
        executer.run()
    }

    public static boolean isMountedNoexec(String dir) {
        if (TestPrecondition.NOT_LINUX) {
            return false;
        }
        def out = new StringBuffer()
        'mount'.execute().waitForProcessOutput(out, System.err);
        return out.readLines().find {it.startsWith("tmpfs on $dir type tmpfs") && it.contains('noexec')}!= null;
    }

}
