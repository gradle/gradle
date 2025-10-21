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

package org.gradle.integtests.fixtures.configurationcache

import org.gradle.integtests.fixtures.BuildOperationTreeQueries
import org.gradle.internal.operations.trace.BuildOperationRecord

import javax.annotation.Nullable

class ConfigurationCacheBuildOperationsFixture {

    final BuildOperationTreeQueries operations

    ConfigurationCacheBuildOperationsFixture(BuildOperationTreeQueries operations) {
        this.operations = operations
    }

    /**
     * A successful "CC hit"
     */
    void assertStateLoaded() {
        assertStorePhaseSkipped()
        assertLoadPhaseSuccessful()
    }

    /**
     * A "CC hit" with errors happening during load phase
     */
    void assertStateLoadFailed() {
        assertStorePhaseSkipped()
        assertLoadPhaseFailed()
    }

    /**
     * A successful "CC miss" with load-after-store behavior
     */
    void assertStateStored() {
        assertStorePhaseSuccessful()
        assertLoadPhaseSuccessful()
    }

    /**
     * A "CC miss" that was aborted after encountering errors before or during store phase
     */
    void assertStateStoreFailed() {
        assertStorePhaseFailed()
        assertLoadPhaseSkipped()
    }

    /**
     * A "CC miss" where the state was discarded after encountering errors before or during store phase while when CC problems are warnings.
     */
    void assertStateStoreDiscarded() {
        assertStorePhaseSuccessful()
        assertLoadPhaseSkipped()
    }

    /**
     * A "CC miss" that stores successfully, but fails during load with an error
     */
    void assertStateStoredAndFailedOnLoad() {
        assertStorePhaseSuccessful()
        assertLoadPhaseFailed()
    }

    void assertModelStored() {
        def modelStore = modelStoreOperation()
        assert modelStore != null && modelStore.failure == null
        assert modelLoadOperation() == null
    }

    void assertModelStoreFailed() {
        def modelStore = modelStoreOperation()
        assert modelStore != null && modelStore.failure != null
        assert modelLoadOperation() == null
    }

    void assertModelLoaded() {
        def modelLoad = modelLoadOperation()
        assert modelLoad != null && modelLoad.failure == null
        assert modelStoreOperation() == null
    }

    void assertNoConfigurationCache() {
        assertNoWorkGraphOperations()
        assertNoModelOperations()
    }

    void assertNoWorkGraphOperations() {
        assert workGraphStoreOperation() == null
        assert workGraphLoadOperation() == null
    }

    void assertNoModelOperations() {
        assert modelStoreOperation() == null
        assert modelLoadOperation() == null
    }

    void assertStorePhaseSuccessful() {
        def store = workGraphStoreOperation()
        assert store != null
        assert store.failure == null
    }

    void assertLoadPhaseSuccessful() {
        def load = workGraphLoadOperation()
        assert load != null
        assert load.failure == null
    }

    void assertStorePhaseFailed() {
        def store = workGraphStoreOperation()
        assert store != null
        assert store.failure != null
    }

    void assertLoadPhaseFailed() {
        def load = workGraphLoadOperation()
        assert load != null
        assert load.failure != null
    }

    void assertStorePhaseSkipped() {
        assert workGraphStoreOperation() == null
    }

    void assertLoadPhaseSkipped() {
        assert workGraphLoadOperation() == null
    }

    @Nullable
    private BuildOperationRecord workGraphStoreOperation() {
        operations.singleOrNone("Store configuration cache state")
    }

    @Nullable
    private BuildOperationRecord workGraphLoadOperation() {
        operations.singleOrNone("Load configuration cache state")
    }

    @Nullable
    private BuildOperationRecord modelStoreOperation() {
        operations.singleOrNone("Store model in configuration cache")
    }

    @Nullable
    private BuildOperationRecord modelLoadOperation() {
        operations.singleOrNone("Load model from configuration cache")
    }
}
