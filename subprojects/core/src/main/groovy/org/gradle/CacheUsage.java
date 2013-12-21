/*
 * Copyright 2009 the original author or authors.
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
package org.gradle;

import org.gradle.api.InvalidUserDataException;

/**
 * <p>{@code CacheUsage} specifies how compiled scripts should be cached.</p>
 *
 * @deprecated This enum has been deprecated. Use {@link StartParameter#isRerunTasks()} and {@link StartParameter#isRecompileScripts()} instead.
 */
@Deprecated
public enum CacheUsage {
    ON, REBUILD;

    public static CacheUsage fromString(String usagestr) {
        try {
            return valueOf(usagestr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidUserDataException(String.format("Unknown cache usage '%s' specified.", usagestr));
        }
    }
}
