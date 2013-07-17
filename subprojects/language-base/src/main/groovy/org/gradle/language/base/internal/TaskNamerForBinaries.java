/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.language.base.internal;

import org.gradle.api.Nullable;
import org.gradle.util.GUtil;

public class TaskNamerForBinaries implements BinaryNamingScheme {
    private final String baseName;
    private String shortName;

    public TaskNamerForBinaries(String baseName) {
        this.baseName = baseName;
        this.shortName = baseName;
    }

    public TaskNamerForBinaries withMainCollapsed() {
        shortName = baseName.equals("main") ? "" : baseName;
        return this;
    }

    public String getLifecycleTaskName() {
        return baseName;
    }

    public String getOutputDirectoryBase() {
        return baseName;
    }

    public String getTaskName(@Nullable String verb) {
        return getTaskName(verb, null);
    }

    public String getTaskName(@Nullable String verb, @Nullable String target) {
        if (verb == null && target == null) {
            return GUtil.toLowerCamelCase(baseName);
        }
        if (verb == null) {
            return GUtil.toLowerCamelCase(String.format("%s %s", shortName, target));
        }
        if (target == null) {
            return GUtil.toLowerCamelCase(String.format("%s %s", verb, baseName));
        }
        return GUtil.toLowerCamelCase(String.format("%s %s %s", verb, shortName, target));
    }
}
