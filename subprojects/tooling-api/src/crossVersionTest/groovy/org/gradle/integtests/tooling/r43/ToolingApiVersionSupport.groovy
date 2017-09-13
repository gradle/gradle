/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.tooling.r43

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.r18.NullAction
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.util.GradleVersion

abstract class ToolingApiVersionSupport extends ToolingApiSpecification {
    def output = new ByteArrayOutputStream()

    // AbstractConsumerConnection.getVersionDetail was introduced in 1.2
    def minProviderVersionDetail = GradleVersion.version('1.2')

    def currentVersionMessage(GradleVersion version, GradleVersion lowerBound) {
        if (version >= lowerBound) {
            return "You are currently using ${version.version}. "
        } else {
            return ''
        }
    }
    def currentProviderMessage(String version) {
        return currentVersionMessage(GradleVersion.version(version), minProviderVersionDetail)
    }

    String providerUnsupportedMessage(String version) {
        return "Support for Gradle older than 1.2 has been removed. ${currentProviderMessage(version)}You should upgrade your Gradle to version 1.2 or later."
    }

    String providerDeprecationMessage(String version) {
        return "Support for Gradle older than 2.6 is deprecated and will be removed in 5.0. You are currently using ${version}. You should upgrade your Gradle."
    }

    // since 1.0
    def build() {
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.standardError = output
            build.run()
        }
    }

    // since 1.0
    def getModel() {
        withConnection { ProjectConnection connection ->
            def model = connection.model(EclipseProject)
            model.standardError = output
            model.get()
        }
    }

    // since 1.8
    def buildAction() {
        withConnection { ProjectConnection connection ->
            def action = connection.action(new NullAction())
            action.standardError = output
            action.run()
        }
    }

    // since 2.6
    def testExecution() {
        withConnection { ProjectConnection connection ->
            def launcher = connection.newTestLauncher().withJvmTestClasses("class")
            launcher.standardError = output
            launcher.run()
        }
    }
}
