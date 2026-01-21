/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.ivyservice.CacheExpirationControl
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ChangingValueDependencyResolutionListener
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.collections.FileCollectionObservationListener
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.properties.GradlePropertiesListener
import org.gradle.api.internal.properties.GradlePropertyScope
import org.gradle.api.internal.provider.ValueSourceProviderFactory
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.groovy.scripts.internal.ScriptSourceListener
import org.gradle.internal.buildoption.FeatureFlag
import org.gradle.internal.buildoption.FeatureFlagListener
import org.gradle.internal.cc.impl.CoupledProjectsListener
import org.gradle.internal.cc.impl.UndeclaredBuildInputListener
import org.gradle.internal.cc.impl.services.ConfigurationCacheEnvironment
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.WorkInputListener
import org.gradle.internal.execution.WorkInputListeners
import org.gradle.internal.properties.InputBehavior
import org.gradle.internal.resource.local.FileResourceListener
import org.gradle.internal.scripts.ScriptExecutionListener
import org.gradle.internal.scripts.ScriptFileResolvedListener
import org.gradle.internal.scripts.ScriptFileResolverListeners
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.tooling.provider.model.internal.ToolingModelProjectDependencyListener
import java.io.Closeable
import java.io.File
import java.util.EnumSet

/**
 * A dispatcher for various fingerprint-related events.
 * It forwards the events into the [ConfigurationCacheFingerprintWriter] if the latter is active (== registered itself within this class).
 *
 * **DO NOT USE LISTENERMANAGER TO SEND EVENTS TO THIS CLASS!**.
 * See the implementation comments for the justification.
 */
@ServiceScope(Scope.BuildTree::class)
internal class ConfigurationCacheFingerprintEventHandler(
    private val workInputListeners: WorkInputListeners,
    private val scriptFileResolverListeners: ScriptFileResolverListeners
) :
// For these listeners this class is the only "real" implementation.
// Event sources get our instance through ServiceRegistry.
    ChangingValueDependencyResolutionListener,
    ConfigurationCacheEnvironment.Listener,
    CoupledProjectsListener,
    FeatureFlagListener,
    FileCollectionObservationListener,
    FileResourceListener,
    GradlePropertiesListener,
    ScriptExecutionListener,
    ScriptSourceListener,
    ToolingModelProjectDependencyListener,
    UndeclaredBuildInputListener,
    ValueSourceProviderFactory.ComputationListener,
    ValueSourceProviderFactory.ValueListener,

    // These listeners have to be registered separately:
    ScriptFileResolvedListener, // This class is the single listener, but lives in a shorter scope, so cannot be injected there.
    WorkInputListener,

    // Interfaces not involved with event dispatch.
    Closeable {

    // IMPORTANT: Why doesn't this class use ListenerManager as the transport?
    // Broadcasting an event (calling a method on a listener) through LM involves holding a lock.
    // Not taking contention into account, that lock may cause deadlocks with other locks involved in processing events.
    // ListenerManager also forbids reentrancy (emitting an event while processing an event of the same type).
    // Some CC fingerprinting events are inherently reentrant, e.g. handling ValueSource.obtain() call may trigger another ValueSource to be obtained.
    // And last but not least, the versatility of the ListenerManager has a cost of 8-10 times more expensive broadcast compared to direct method call.
    // As for most of the events this is the only implementation, the price of the versatility isn't justified.
    @Volatile
    private var delegate: ConfigurationCacheFingerprintWriter? = null

    fun clearDelegate(delegateToClear: ConfigurationCacheFingerprintWriter) {
        // Clearing the delegate before setting it indicates some violation of the usage pattern, even if it is benign.
        check(delegate == delegateToClear) { "Unexpected delegate $delegate when trying to clear $delegateToClear" }
        delegate = null
    }

    fun setDelegate(delegate: ConfigurationCacheFingerprintWriter) {
        // This check should fire even if we're overriding the delegate with itself.
        check(this.delegate == null) { "Cannot overwrite existing delegate, should be cleared first" }
        this.delegate = delegate
    }

    init {
        workInputListeners.addListener(this)
        scriptFileResolverListeners.addListener(this)
    }

    override fun close() {
        workInputListeners.removeListener(this)
        scriptFileResolverListeners.removeListener(this)
    }

    override fun <T : Any, P : ValueSourceParameters> valueObtained(
        obtainedValue: ValueSourceProviderFactory.ValueListener.ObtainedValue<T, P>,
        source: ValueSource<T, P>
    ) {
        delegate?.valueObtained(obtainedValue, source)
    }

    override fun beforeValueObtained() {
        delegate?.beforeValueObtained()
    }

    override fun afterValueObtained() {
        delegate?.afterValueObtained()
    }

    override fun onExecute(work: UnitOfWork, relevantBehaviors: EnumSet<InputBehavior>) {
        delegate?.onExecute(work, relevantBehaviors)
    }

    override fun onScriptClassLoaded(source: ScriptSource, scriptClass: Class<*>) {
        delegate?.onScriptClassLoaded(source)
    }

    override fun systemPropertyRead(key: String, value: Any?, consumer: String?) {
        delegate?.systemPropertyRead(key, value, consumer)
    }

    override fun systemPropertyChanged(key: Any, value: Any?, consumer: String?) {
        delegate?.systemPropertyChanged(key, value, consumer)
    }

    override fun systemPropertyRemoved(key: Any, consumer: String?) {
        delegate?.systemPropertyRemoved(key)
    }

    override fun systemPropertiesCleared(consumer: String?) {
        delegate?.systemPropertiesCleared()
    }

    override fun envVariableRead(key: String, value: String?, consumer: String?) {
        delegate?.envVariableRead(key, value, consumer)
    }

    override fun fileOpened(file: File, consumer: String?) {
        delegate?.fileOpened(file, consumer)
    }

    override fun fileObserved(file: File, consumer: String?) {
        delegate?.fileObserved(file)
    }

    override fun fileObserved(file: File) {
        delegate?.fileObserved(file)
    }

    override fun fileSystemEntryObserved(file: File, consumer: String?) {
        delegate?.fileSystemEntryObserved(file, consumer)
    }

    override fun directoryChildrenObserved(directory: File, consumer: String?) {
        delegate?.directoryChildrenObserved(directory, consumer)
    }

    override fun directoryChildrenObserved(file: File) {
        delegate?.directoryChildrenObserved(file)
    }

    override fun startParameterProjectPropertiesObserved() {
        delegate?.startParameterProjectPropertiesObserved()
    }

    override fun onDynamicVersionSelection(
        requested: ModuleComponentSelector,
        expiry: CacheExpirationControl.Expiry,
        versions: Set<ModuleVersionIdentifier>
    ) {
        delegate?.onDynamicVersionSelection(requested, expiry, versions)
    }

    override fun onChangingModuleResolve(moduleId: ModuleComponentIdentifier, expiry: CacheExpirationControl.Expiry) {
        delegate?.onChangingModuleResolve(moduleId, expiry)
    }

    override fun onProjectReference(referrer: ProjectState, target: ProjectState) {
        delegate?.onProjectReference(referrer, target)
    }

    override fun onToolingModelDependency(consumer: ProjectState, target: ProjectState) {
        delegate?.onToolingModelDependency(consumer, target)
    }

    override fun onScriptFileResolved(scriptFile: File) {
        delegate?.onScriptFileResolved(scriptFile)
    }

    override fun flagRead(flag: FeatureFlag) {
        delegate?.flagRead(flag)
    }

    override fun fileCollectionObserved(fileCollection: FileCollectionInternal) {
        delegate?.fileCollectionObserved(fileCollection)
    }

    override fun scriptSourceObserved(scriptSource: ScriptSource) {
        delegate?.scriptSourceObserved(scriptSource)
    }

    override fun onGradlePropertiesLoaded(propertyScope: GradlePropertyScope, propertiesDir: File) {
        delegate?.onGradlePropertiesLoaded(propertyScope, propertiesDir)
    }

    override fun onGradlePropertyAccess(propertyScope: GradlePropertyScope, propertyName: String, propertyValue: Any?) {
        delegate?.onGradlePropertyAccess(propertyScope, propertyName, propertyValue)
    }

    override fun onGradlePropertiesByPrefix(
        propertyScope: GradlePropertyScope,
        prefix: String,
        snapshot: Map<String, String>
    ) {
        delegate?.onGradlePropertiesByPrefix(propertyScope, prefix, snapshot)
    }

    override fun systemPropertiesPrefixedBy(prefix: String, snapshot: Map<String, String?>) {
        delegate?.systemPropertiesPrefixedBy(prefix, snapshot)
    }

    override fun systemProperty(name: String, value: String?) {
        delegate?.systemProperty(name, value)
    }

    override fun envVariablesPrefixedBy(prefix: String, snapshot: Map<String, String?>) {
        delegate?.envVariablesPrefixedBy(prefix, snapshot)
    }

    override fun envVariable(name: String, value: String?) {
        delegate?.envVariable(name, value)
    }
}
