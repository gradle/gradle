/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.tooling.internal.protocol;

import org.gradle.api.Incubating;
import org.gradle.api.NonNullApi;

import javax.annotation.Nullable;

/**
 * DO NOT CHANGE THIS INTERFACE. It is part of the cross-version protocol.
 *
 * Interface for describing test assertion failures that contain detailed information about the expected and actual file contents.
 *
 * @since 8.3
 */
@Incubating
@NonNullApi
public interface InternalFileComparisonTestAssertionFailure extends InternalTestAssertionFailure {

    /**
     * Returns the expected file contents.
     *
     * @return the expected file contents or {@code null} if the test framework doesn't supply detailed information on assertion failures
     */
    @Nullable
    byte[] getExpectedContent();

    /**
     * Returns the actual file contents.
     *
     * @return the actual file contents or {@code null} if the test framework doesn't supply detailed information on assertion failures
     */
    @Nullable
    byte[] getActualContent();
}
