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

package org.gradle.api.internal.jvm;

import org.gradle.api.Nullable;
import org.gradle.platform.base.internal.BinaryNamingScheme;
import org.gradle.util.GUtil;

import java.util.Collections;
import java.util.List;

class ClassDirectoryBinaryNamingScheme implements BinaryNamingScheme {
    private final String baseName;
    private final String collapsedName;

    public ClassDirectoryBinaryNamingScheme(String baseName) {
        this.baseName = baseName;
        this.collapsedName = collapseMain(this.baseName);
    }

    private static String collapseMain(String name) {
        return name.equals("main") ? "" : name;
    }

    public String getDescription() {
        return String.format("classes '%s'", baseName);
    }

    public List<String> getVariantDimensions() {
        return Collections.emptyList();
    }

    public String getLifecycleTaskName() {
        return getTaskName(null, "classes");
    }

    public String getTaskName(@Nullable String verb) {
        return getTaskName(verb, null);
    }

    public String getTaskName(@Nullable String verb, @Nullable String target) {
        String name = baseName;
        if (target != null) {
            name = collapsedName;
        }
        return GUtil.toLowerCamelCase(String.format("%s %s %s", nullToEmpty(verb), name, nullToEmpty(target)));
    }

    private String nullToEmpty(String input) {
        return input == null ? "" : input;
    }

    public String getOutputDirectoryBase() {
        return baseName;
    }
}
