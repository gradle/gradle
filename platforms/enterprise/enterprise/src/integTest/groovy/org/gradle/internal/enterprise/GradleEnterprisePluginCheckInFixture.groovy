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

package org.gradle.internal.enterprise

import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.plugin.management.internal.autoapply.AutoAppliedDevelocityPlugin
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.MavenFileRepository

class GradleEnterprisePluginCheckInFixture extends BaseBuildScanPluginCheckInFixture {

    GradleEnterprisePluginCheckInFixture(TestFile projectDir, MavenFileRepository mavenRepo, GradleExecuter pluginBuildExecuter) {
        super(
            projectDir,
            mavenRepo,
            pluginBuildExecuter,
            AutoAppliedDevelocityPlugin.GRADLE_ENTERPRISE_PLUGIN_ID.id,
            'com.gradle.enterprise.gradleplugin',
            'GradleEnterprisePlugin',
            AutoAppliedDevelocityPlugin.GRADLE_ENTERPRISE_PLUGIN_ARTIFACT_NAME
        )
    }

}
