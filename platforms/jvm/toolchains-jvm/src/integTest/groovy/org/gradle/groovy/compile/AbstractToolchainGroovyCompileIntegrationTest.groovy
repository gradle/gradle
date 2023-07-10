/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.groovy.compile

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.testing.fixture.GroovyCoverage
import org.junit.Assume


abstract class AbstractToolchainGroovyCompileIntegrationTest extends ApiGroovyCompilerIntegrationSpec implements JavaToolchainFixture {

    Jvm jdk

    def setup() {
        jdk = computeJdkForTest()
        Assume.assumeNotNull(jdk)
        Assume.assumeTrue(GroovyCoverage.supportsJavaVersion(version, jdk.javaVersion))

        executer.beforeExecute {
            withInstallations(jdk)
        }
    }

    abstract Jvm computeJdkForTest()

    @Override
    String compilerConfiguration() {
        javaPluginToolchainVersion(jdk)
    }

    @Override
    String annotationProcessorExtraSetup() {
        compilerConfiguration()
    }
}
