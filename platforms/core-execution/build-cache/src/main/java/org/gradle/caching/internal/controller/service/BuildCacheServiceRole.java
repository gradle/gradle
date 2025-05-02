/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.caching.internal.controller.service;

import org.gradle.internal.scan.UsedByScanPlugin;

import java.util.Locale;

@UsedByScanPlugin("values are expected (type is not linked), see BuildCacheStoreBuildOperationType and friends")
public enum BuildCacheServiceRole {
    LOCAL,
    REMOTE;

    private final String displayName;

    BuildCacheServiceRole() {
        this.displayName = name().toLowerCase(Locale.ROOT);
    }

    public String getDisplayName() {
        return displayName;
    }
}
