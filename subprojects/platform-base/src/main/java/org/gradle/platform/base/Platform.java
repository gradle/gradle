/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.platform.base;

import org.gradle.api.Incubating;
import org.gradle.api.Named;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;

/**
 * The platform or runtime that a binary is designed to run on.
 *
 * Examples: the JvmPlatform defines a java runtime, while the NativePlatform defines the Operating System and Architecture for a native app.
 */
@Incubating
public interface Platform extends Named {
    @Override
    @Input
    String getName();

    /**
     * Returns a human consumable name for this platform.
     *
     */
    @Internal
    String getDisplayName();
}
