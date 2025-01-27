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

package org.gradle.api.problems.deprecation;


import org.gradle.api.problems.AdditionalData;

import javax.annotation.Nullable;

/**
 * Additional data shipped with a deprecation.
 * <p>
 * This data is accessible, after reporting the deprecation, to consumers of problem events.
 *
 * @since 8.13
 */
public interface DeprecationData extends AdditionalData {
    @Nullable
    DeprecatedVersion getRemovedIn();

    @Nullable
    String getReplacedBy();

    @Nullable
    String getBecause();
}
