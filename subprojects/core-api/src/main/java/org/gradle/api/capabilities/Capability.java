/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.capabilities;

import javax.annotation.Nullable;

/**
 * A capability represents the content or API of a variant's artifacts. A variant often has a single capability,
 * representing the entire content of its artifacts. However, a variant may have multiple capabilities, where each
 * capability could represent a subset of the variant's content.
 *
 * <p>Capabilities are versioned, since a variant's content changes between versions. Multiple variants with the
 * same capability may not exist in a dependency graph simultaneously.</p>
 *
 * @since 4.7
 */
public interface Capability {
    String getGroup();
    String getName();
    @Nullable
    String getVersion();
}
