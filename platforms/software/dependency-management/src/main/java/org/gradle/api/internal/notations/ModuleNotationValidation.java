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
package org.gradle.api.internal.notations;

import com.google.common.collect.Lists;
import org.gradle.internal.typeconversion.UnsupportedNotationException;
import org.gradle.util.internal.GUtil;

import java.util.List;

public abstract class ModuleNotationValidation {
    private final static List<Character> INVALID_SPEC_CHARS = Lists.newArrayList('*', '[', ']', '(', ')', ',');

    public static String validate(String part, String notation) {
        if (!GUtil.isTrue(part)) {
            throw new UnsupportedNotationException(notation);
        }
        for (char c : INVALID_SPEC_CHARS) {
            if (part.indexOf(c) != -1) {
                throw new UnsupportedNotationException(notation);
            }
        }
        return part;
    }

    public static String validate(String part) {
        return validate(part, part);
    }
}
