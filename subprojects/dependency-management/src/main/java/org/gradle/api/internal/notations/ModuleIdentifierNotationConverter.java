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

package org.gradle.api.internal.notations;

import com.google.common.collect.Lists;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.typeconversion.TypedNotationConverter;
import org.gradle.internal.typeconversion.UnsupportedNotationException;
import org.gradle.util.GUtil;

import java.util.List;

import static org.gradle.api.internal.artifacts.DefaultModuleIdentifier.newId;

public class ModuleIdentifierNotationConverter extends TypedNotationConverter<String, ModuleIdentifier> {
    private final static List<Character> INVALID_SPEC_CHARS = Lists.newArrayList('*', '[', ']', '(', ')', ',', '+');

    public ModuleIdentifierNotationConverter() {
        super(String.class);
    }

    /**
     * Empty String for either group or module name is not allowed.
     */
    protected ModuleIdentifier parseType(String notation) {
        assert notation != null;
        String[] split = notation.split(":");
        if (split.length != 2) {
            throw new UnsupportedNotationException(notation);
        }
        String group = validate(split[0].trim(), notation);
        String name = validate(split[1].trim(), notation);
        return newId(group, name);
    }

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

    @Override
    public void describe(DiagnosticsVisitor visitor) {
        visitor.candidate("String describing the module in 'group:name' format").example("'org.gradle:gradle-core'.");
    }
}
