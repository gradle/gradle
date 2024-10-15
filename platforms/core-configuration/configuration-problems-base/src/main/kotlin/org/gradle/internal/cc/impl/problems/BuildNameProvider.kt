/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.cc.impl.problems

import org.gradle.api.initialization.Settings
import org.gradle.api.internal.SettingsInternal
import org.gradle.internal.InternalBuildAdapter
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope

/**
 * A utility class that provides the name of the build.
 */
@ServiceScope(Scope.BuildTree::class)
class BuildNameProvider(private val listenerManager: ListenerManager) : InternalBuildAdapter(), Stoppable {

    init {
        listenerManager.addListener(this)
    }

    override fun stop() {
        listenerManager.removeListener(this)
    }

    private
    var rootBuildName: String? = null

    // TODO: This doesn't work well with CC and it never did in the previous solution.
    // The issue is that settingsEvaluated relies on the BuildListener which is not supported in CC. So we can not throw in case it is null.
    // the tests that break if you do are in ConfigurationCacheDependencyResolutionIntegrationTest
    // a nicer way to do this would be to use buildStateRegistry.rootBuild.projects.rootProject.name but that also doesn't work with CC :(
    // one of the tests that fail if you try this is org.gradle.integtests.resolve.catalog.TomlDependenciesExtensionIntegrationTest.should throw an error if 'from' is called multiple times
    fun buildName(): String? {
        return rootBuildName
    }

    override fun settingsEvaluated(settings: Settings) {
        if ((settings as SettingsInternal).gradle.isRootBuild) {
            rootBuildName = settings.rootProject.name
        }
    }
}
