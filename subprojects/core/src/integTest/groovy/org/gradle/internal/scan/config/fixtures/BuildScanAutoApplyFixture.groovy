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

package org.gradle.internal.scan.config.fixtures

import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.plugin.management.internal.autoapply.DefaultAutoAppliedPluginRegistry
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.plugin.PluginBuilder

import static org.gradle.test.fixtures.plugin.PluginBuilder.packageName

class BuildScanAutoApplyFixture {

    public static final String BUILD_SCAN_PLUGIN_ID = DefaultAutoAppliedPluginRegistry.BUILD_SCAN_PLUGIN_ID.id
    public static final String PUBLISHING_BUILD_SCAN_MESSAGE_PREFIX = 'PUBLISHING BUILD SCAN v'
    public static final String DUMMY_BUILD_SCAN_PLUGIN_IMPL_CLASS = 'DummyBuildScanPlugin'
    public static final String FULLY_QUALIFIED_DUMMY_BUILD_SCAN_PLUGIN_IMPL_CLASS = "${packageName}.${DUMMY_BUILD_SCAN_PLUGIN_IMPL_CLASS}"
    private final TestFile projectDir
    private final MavenFileRepository mavenRepo

    BuildScanAutoApplyFixture(TestFile projectDir, MavenFileRepository mavenRepo) {
        this.projectDir = projectDir
        this.mavenRepo = mavenRepo
    }

    String pluginManagement() {
        """
            pluginManagement {
                repositories {
                    maven { url '${mavenRepo.uri}' }
                }
            }
        """
    }

    void publishDummyBuildScanPlugin(String version, GradleExecuter executer) {
        def builder = new PluginBuilder(projectDir.file('plugin-' + version))
        builder.addPlugin("""
            def gradle = project.gradle
            
            org.gradle.internal.scan.config.BuildScanPluginMetadata buildScanPluginMetadata = { "${version}" } as org.gradle.internal.scan.config.BuildScanPluginMetadata
            gradle.services.get(org.gradle.internal.scan.config.BuildScanConfigProvider).collect(buildScanPluginMetadata)
            
            gradle.buildFinished {
                println '${PUBLISHING_BUILD_SCAN_MESSAGE_PREFIX}${version}'
            }
""", BUILD_SCAN_PLUGIN_ID, DUMMY_BUILD_SCAN_PLUGIN_IMPL_CLASS)
        builder.publishAs("com.gradle:build-scan-plugin:${version}", mavenRepo, executer)
    }
}
