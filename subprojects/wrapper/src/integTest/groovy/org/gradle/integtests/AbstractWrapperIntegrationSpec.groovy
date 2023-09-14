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

package org.gradle.integtests

import org.apache.commons.io.FilenameUtils
import org.gradle.api.Action
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.InProcessGradleExecuter
import org.gradle.internal.Actions
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.keystore.TestKeyStore

class AbstractWrapperIntegrationSpec extends AbstractIntegrationSpec {
    public static final String NOT_EMBEDDED_REASON = "wrapperExecuter requires a real distribution"
    void installationIn(TestFile userHomeDir) {
        def distDir = userHomeDir.file("wrapper/dists/${FilenameUtils.getBaseName(distribution.binDistribution.absolutePath)}").assertIsDir()
        assert distDir.listFiles().length == 1
        distDir.listFiles()[0].file("gradle-${distribution.version.baseVersion.version}").assertIsDir()
    }

    void prepareWrapper(URI distributionUri = distribution.binDistribution.toURI(), Action<GradleExecuter> action = Actions.doNothing()) {
        def executer = new InProcessGradleExecuter(distribution, temporaryFolder)
        executer.beforeExecute(action)
        executer.withArguments("wrapper", "--gradle-distribution-url", distributionUri.toString()).run()
    }

    void prepareWrapper(URI distributionUri = distribution.binDistribution.toURI(), TestKeyStore keyStore) {
        def executer = new InProcessGradleExecuter(distribution, temporaryFolder)
        executer.withArguments(
            "wrapper",
            "--gradle-distribution-url",
            distributionUri.toString(),
        )
        keyStore.trustStoreArguments.each {
            executer.withArgument(it)
        }
        executer.run()
    }

    GradleExecuter getWrapperExecuter() {
        executer.requireOwnGradleUserHomeDir()
        executer.requireIsolatedDaemons()
        executer.beforeExecute {
            executer.usingExecutable("gradlew")
        }
        return executer
    }
}
