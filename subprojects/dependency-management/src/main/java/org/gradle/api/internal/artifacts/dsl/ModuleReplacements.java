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

package org.gradle.api.internal.artifacts.dsl;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.notations.ModuleIdentiferNotationParser;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;

import java.util.Map;

import static com.google.common.collect.Maps.newHashMap;

public class ModuleReplacements implements ModuleReplacementsData {

    private final Map<ModuleIdentifier, ModuleIdentifier> replacements = newHashMap();

    public ComponentModuleDetails module(final Object sourceModule) {
        return new ComponentModuleDetails() {
            public void replacedBy(final Object targetModule) {
                NotationParser<Object, ModuleIdentifier> parser = parser();
                replacements.put(parser.parseNotation(sourceModule), parser.parseNotation(targetModule));
            }
        };
    }

    private static NotationParser<Object, ModuleIdentifier> parser() {
        return NotationParserBuilder
                    .toType(ModuleIdentifier.class)
                    .parser(new ModuleIdentiferNotationParser())
                    .toComposite();
    }

    public ModuleIdentifier getReplacementFor(ModuleIdentifier sourceModule) {
        return replacements.get(sourceModule);
    }
}
