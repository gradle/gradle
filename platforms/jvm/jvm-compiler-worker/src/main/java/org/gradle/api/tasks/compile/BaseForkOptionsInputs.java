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

package org.gradle.api.tasks.compile;

import org.gradle.api.Incubating;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.jspecify.annotations.NullMarked;

/**
 * The lazy properties behind the eager getters and setters of {@link BaseForkOptions}.
 *
 * @since 10.0.0
 */
@Incubating
@NullMarked
public interface BaseForkOptionsInputs {

    /**
     * The initial heap size for the compiler process.
     *
     * @see BaseForkOptions#getMemoryInitialSize()
     * @since 10.0.0
     */
    Property<String> getMemoryInitialSize();

    /**
     * The maximum heap size for the compiler process.
     *
     * @see BaseForkOptions#getMemoryMaximumSize()
     * @since 10.0.0
     */
    Property<String> getMemoryMaximumSize();

    /**
     * Any additional JVM arguments for the compiler process.
     *
     * @see BaseForkOptions#getJvmArgs()
     * @since 10.0.0
     */
    ListProperty<String> getJvmArgs();
}
