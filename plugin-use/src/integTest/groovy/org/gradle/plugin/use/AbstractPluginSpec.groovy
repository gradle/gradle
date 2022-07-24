/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.plugin.use


import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.gradle.test.fixtures.server.http.MavenHttpPluginRepository
import org.intellij.lang.annotations.Language
import org.junit.Rule

abstract class AbstractPluginSpec extends AbstractIntegrationSpec {

    protected static final String PLUGIN_ID = "org.myplugin"
    protected static final String VERSION = "1.0"
    protected static final String GROUP = "my"
    protected static final String ARTIFACT = "plugin"
    protected static final String USE = "plugins { id '$PLUGIN_ID' version '$VERSION' }"
    protected static final String USE_KOTLIN = "plugins { id(\"$PLUGIN_ID\") version \"$VERSION\" }"

    @Rule
    MavenHttpPluginRepository pluginRepo = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)
    PluginBuilder pluginBuilder = new PluginBuilder(file(ARTIFACT))

    def setup() {
        requireOwnGradleUserHomeDir()
    }

    MavenHttpModule publishPlugin(
        @Language(value = "groovy", prefix = "void apply(org.gradle.api.Project project) {\n", suffix = "\n}") String impl
    ) {
        pluginBuilder.with {
            addPlugin(impl, PLUGIN_ID)
            publishAs(GROUP, ARTIFACT, VERSION, pluginRepo, executer).allowAll().pluginModule as MavenHttpModule
        }
    }

    MavenHttpModule publishSettingPlugin(
        @Language(value = "groovy", prefix = "void apply(org.gradle.api.initialization.Settings settings) {\n", suffix = "\n}") String impl
    ) {
        pluginBuilder.with {
            addSettingsPlugin(impl, PLUGIN_ID)
            publishAs(GROUP, ARTIFACT, VERSION, pluginRepo, executer).allowAll().pluginModule as MavenHttpModule
        }
    }

}
