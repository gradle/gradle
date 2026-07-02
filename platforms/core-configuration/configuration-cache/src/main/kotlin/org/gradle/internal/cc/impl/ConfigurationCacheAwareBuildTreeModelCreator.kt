/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.cc.impl

import org.gradle.internal.buildtree.BuildTreeModelAction
import org.gradle.internal.buildtree.BuildTreeModelCreator
import org.gradle.internal.buildtree.BuildTreeModelCreatorResult
import org.gradle.internal.cc.impl.models.BuildTreeModel


class ConfigurationCacheAwareBuildTreeModelCreator(
    private val delegate: BuildTreeModelCreator,
    private val cache: BuildTreeConfigurationCache
) : BuildTreeModelCreator {
    override fun beforeTasks(action: BuildTreeModelAction<*>): BuildTreeModelCreatorResult<Void> {
        // maybePrepareModel runs the delegate only on a cache miss; on a hit the failures stay empty.
        var configurationFailures = emptyList<Throwable>()
        var modelBuilderFailures = emptyList<Throwable>()

        cache.maybePrepareModel {
            val delegateResult = delegate.beforeTasks(action)
            configurationFailures = delegateResult.configurationFailures
            modelBuilderFailures = delegateResult.modelBuilderFailures
        }

        return discardEntryIfFailed(BuildTreeModelCreatorResult<Void>(null, configurationFailures, modelBuilderFailures))
    }

    override fun <T : Any> fromBuildModel(action: BuildTreeModelAction<out T>): BuildTreeModelCreatorResult<T> {
        // loadOrCreateModel runs the delegate only on a cache miss; on a hit the model is loaded and the failures stay empty.
        var configurationFailures = emptyList<Throwable>()
        var modelBuilderFailures = emptyList<Throwable>()

        val model: T? = cache.loadOrCreateModel {
            val delegateResult = delegate.fromBuildModel(action)
            configurationFailures = delegateResult.configurationFailures
            modelBuilderFailures = delegateResult.modelBuilderFailures
            val builtModel = delegateResult.model
            if (builtModel == null) BuildTreeModel.NullModel else BuildTreeModel.Model(builtModel)
        }.result()

        return discardEntryIfFailed(BuildTreeModelCreatorResult(model, configurationFailures, modelBuilderFailures))
    }

    private fun <T : Any> discardEntryIfFailed(result: BuildTreeModelCreatorResult<T>): BuildTreeModelCreatorResult<T> {
        if (result.hasFailures()) {
            // The action produced partial models with failures, so the entry must not be reused: discard it so the
            // next build re-runs the action and re-reports the failures.
            cache.requestEntryDiscard()
        }
        return result
    }
}
