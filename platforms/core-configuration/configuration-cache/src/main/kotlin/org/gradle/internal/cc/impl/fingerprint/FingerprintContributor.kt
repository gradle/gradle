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

package org.gradle.internal.cc.impl.fingerprint

import org.gradle.api.internal.provider.ConfigurationTimeBarrier
import org.gradle.api.internal.provider.DefaultConfigurationTimeBarrier
import org.gradle.configuration.internal.ConfigurationInputsTrackingRunner
import org.gradle.internal.cc.base.serialize.HostServiceProvider
import org.gradle.internal.cc.base.serialize.IsolateOwners
import org.gradle.internal.cc.base.serialize.service
import org.gradle.internal.cc.impl.ConfigurationCacheBuildTreeIO
import org.gradle.internal.cc.impl.ConfigurationCacheStateStore
import org.gradle.internal.cc.impl.CurrentStateStore
import org.gradle.internal.cc.impl.InstrumentedInputAccessListener
import org.gradle.internal.cc.impl.StateType
import org.gradle.internal.cc.impl.profileName
import org.gradle.internal.cc.impl.services.DeferredRootBuildGradle
import org.gradle.internal.configuration.inputs.InstrumentedInputs
import org.gradle.internal.extensions.core.get
import org.gradle.internal.serialize.graph.CloseableWriteContext
import org.gradle.internal.serialize.graph.IsolateOwner
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.util.function.Supplier

internal
interface FingerprintContributor {
    fun <T> contributeToFingerprint(
        action: () -> T
    ): T
}

@ServiceScope(Scope.BuildTree::class)
internal
class DefaultFingerprintContributor(
    private val cacheFingerprintController: ConfigurationCacheFingerprintController,
    private val configurationTimeBarrier: ConfigurationTimeBarrier,
    private val instrumentedInputAccessListener: InstrumentedInputAccessListener,
    private val currentStateStore: CurrentStateStore,
    private val deferredRootBuildGradle: DeferredRootBuildGradle
): FingerprintContributor, ConfigurationInputsTrackingRunner {

    private
    val host by lazy { deferredRootBuildGradle.gradle.services.get<HostServiceProvider>() }

    private
    val isolateOwnerHost: IsolateOwner by lazy { host.let(IsolateOwners::OwnerHost) }

    private
    val store by lazy { currentStateStore.store }

    private
    val cacheIO by lazy { host.service<ConfigurationCacheBuildTreeIO>() }

    override fun <T : Any> runTrackingConfigurationInputs(action: Supplier<T>): T {
        return contributeToFingerprint {
            action.get()
        }
    }
    override fun <T> contributeToFingerprint(
        action: () -> T
    ): T {
        prepareForWork(store::assignSpoolFile, ::cacheFingerprintWriteContextFor)
        try {
            return action()
        } finally {
            doneWithWork()
        }
    }

    private
    fun prepareForWork(stateFileAssigner: (StateType) -> ConfigurationCacheStateStore.StateFile, writeContextForOutputStream: (ConfigurationCacheStateStore.StateFile) -> CloseableWriteContext) {
        prepareConfigurationTimeBarrier()
        cacheFingerprintController.maybeStartCollectingFingerprint(
            stateFileAssigner,
            writeContextForOutputStream
        )
        InstrumentedInputs.setListener(instrumentedInputAccessListener)
    }

    private
    fun doneWithWork() {
        InstrumentedInputs.discardListener()
        cacheFingerprintController.stopCollectingFingerprint()
    }

    private
    fun prepareConfigurationTimeBarrier() {
        require(configurationTimeBarrier is DefaultConfigurationTimeBarrier)
        configurationTimeBarrier.prepare()
    }

    private
    fun cacheFingerprintWriteContextFor(
        stateFile: ConfigurationCacheStateStore.StateFile
    ): CloseableWriteContext {
        val (context, codecs) = cacheIO.writeContextFor("cacheFingerprintWriteContext", stateFile.stateType, stateFile.file::outputStream, stateFile::profileName)
        return context.apply {
            push(isolateOwnerHost, codecs.fingerprintTypesCodec())
        }
    }
}

