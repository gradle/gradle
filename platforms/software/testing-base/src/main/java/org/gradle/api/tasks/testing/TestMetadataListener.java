/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.tasks.testing;

import org.gradle.api.Incubating;
import org.gradle.internal.DeprecatedInGradleScope;
import org.gradle.internal.service.scopes.EventScope;
import org.gradle.internal.service.scopes.Scope;
import org.jspecify.annotations.NullMarked;

/**
 * Listens to the metadata events like reporting key-value pairs or file attachments during test execution.
 *
 * @since 9.4.0
 */
@EventScope(Scope.Build.class)
@NullMarked
@Incubating
@DeprecatedInGradleScope
public interface TestMetadataListener {

    /**
     * Fired when during test execution anything is printed to standard output or error
     *
     * @param testDescriptor describes the test
     * @param metadataEvent the event that contains the metadata
     * @since 9.4.0
     */
    @Incubating
    void onMetadata(TestDescriptor testDescriptor, TestMetadataEvent metadataEvent);
}
