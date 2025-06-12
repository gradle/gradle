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

package org.gradle.api.plugins.internal;

import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.jspecify.annotations.NullMarked;

/**
 * Internal counterpart to {@link JavaPluginExtension}.
 */
@NullMarked
public interface JavaPluginExtensionInternal extends JavaPluginExtension {

    /**
     * Provider API counterpart for {@link #disableAutoTargetJvm()} and {@link #getAutoTargetJvmDisabled()}.
     * <p>
     * This should eventually become public.
     *
     * @return True if auto-target JVM feature is enabled. False if it is disabled.
     */
    Property<Boolean> getAutoTargetJvm();

}
