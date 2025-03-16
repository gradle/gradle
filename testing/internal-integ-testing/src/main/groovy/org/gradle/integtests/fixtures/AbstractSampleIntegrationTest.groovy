/*
 * Copyright 2018 the original author or authors.
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

import org.junit.Assume

abstract class AbstractSampleIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        executer.withRepositoryMirrors()
    }

    def configureExecuterForToolchains(String... versions) {
        def jdks = AvailableJavaHomes.getJdks(versions)
        Assume.assumeTrue(versions.length == jdks.size())
        executer.beforeExecute {
            withArgument("-Porg.gradle.java.installations.paths=" + jdks.collect { it.javaHome.absolutePath }.join(","))
        }
    }
}
