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

package org.gradle.builtinit.projectspecs.internal.internal

import org.gradle.buildinit.projectspecs.InitProjectParameter
import org.gradle.buildinit.projectspecs.InitProjectSpec
import org.gradle.util.internal.GUtil
import org.gradle.util.internal.TextUtil

/**
 * A sample {@link InitProjectSpec} implementation for testing purposes.
 */
class TestInitProjectSpec implements InitProjectSpec {
    private final String name
    private final String type

    TestInitProjectSpec(String name, String type = null) {
        this.name = name
        this.type = type
    }

    @Override
    String getDisplayName() {
        return name
    }

    @Override
    String getType() {
        if (type) {
            return type
        } else {
            return TextUtil.camelToKebabCase(GUtil.toCamelCase(getDisplayName()))
        }
    }

    @Override
    List<InitProjectParameter<?>> getParameters() {
        return null
    }
}
