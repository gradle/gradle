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

import static org.hamcrest.CoreMatchers.notNullValue
import static org.hamcrest.CoreMatchers.nullValue
import static org.hamcrest.MatcherAssert.assertThat


class ConfigurationCacheBuildOperationsFixture {

    final BuildOperationTreeQueries operations

    ConfigurationCacheBuildOperationsFixture(BuildOperationTreeQueries operations) {
        this.operations = operations
    }

    void assertStateLoaded() {
        def load = loadOperation()
        assertThat(load, notNullValue())
        assertThat(load.failure, nullValue())
        assertThat(storeOperation(), nullValue())
    }

    void assertStateLoadFailed() {
        def load = loadOperation()
        assertThat(load, notNullValue())
        assertThat(load.failure, notNullValue())
        assertThat(storeOperation(), nullValue())
    }

    void assertStateStored(boolean expectLoad = true) {
        def store = storeOperation()
        assertThat(store, notNullValue())
        assertThat(store.failure, nullValue())
        assertThat(loadOperation(), expectLoad ? notNullValue() : nullValue())
    }

    void assertStateStoreFailed() {
        assertThat(loadOperation(), nullValue())
        def store = storeOperation()
        assertThat(store, notNullValue())
        assertThat(store.failure, notNullValue())
    }

    void assertNoConfigurationCache() {
        assertThat(loadOperation(), nullValue())
        assertThat(storeOperation(), nullValue())
    }

    private BuildOperationRecord loadOperation() {
        operations.firstMatchingRegex("Load (configuration cache|instant execution) state")
    }

    private BuildOperationRecord storeOperation() {
        operations.firstMatchingRegex("Store (configuration cache|instant execution) state.*")
    }
}
