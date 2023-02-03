/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.buildinit.plugins.internal.modifiers;

import com.google.common.base.Joiner;

import java.util.Locale;

class Names {
    static String displayNameFor(Enum<?> value) {
        String[] parts = value.name().toLowerCase(Locale.US).split("_");
        return Joiner.on(' ').join(parts);
    }

    static String idFor(Enum<?> value) {
        String[] parts = value.name().toLowerCase(Locale.US).split("_");
        return Joiner.on('-').join(parts);
    }
}
