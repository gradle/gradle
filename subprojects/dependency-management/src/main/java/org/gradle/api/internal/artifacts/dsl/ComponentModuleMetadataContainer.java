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

import com.google.common.base.Joiner;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ComponentModuleMetadataDetails;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.notations.ModuleIdentiferNotationParser;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Maps.newHashMap;
import static java.lang.String.format;

public class ComponentModuleMetadataContainer implements ModuleReplacementsData {

    private final Map<ModuleIdentifier, ModuleIdentifier> replacements = newHashMap();

    public ComponentModuleMetadataDetails module(final Object sourceModule) {
        final NotationParser<Object, ModuleIdentifier> parser = parser();
        final ModuleIdentifier source = parser.parseNotation(sourceModule);
        return new ComponentModuleMetadataDetails() {
            public void replacedBy(final Object targetModule) {
                ModuleIdentifier target = parser.parseNotation(targetModule);
                detectCycles(replacements, source, target);
                replacements.put(source, target);
            }

            public ModuleIdentifier getId() {
                return source;
            }

            public ModuleIdentifier getReplacedBy() {
                return replacements.get(source);
            }
        };
    }

    public ModuleIdentifier getReplacementFor(ModuleIdentifier sourceModule) {
        return replacements.get(sourceModule);
    }

    private static void detectCycles(Map<ModuleIdentifier, ModuleIdentifier> replacements, ModuleIdentifier source, ModuleIdentifier target) {
        if (source.equals(target)) {
            throw new InvalidUserDataException(String.format("Cannot declare module replacement that replaces self: %s->%s", source, target));
        }

        ModuleIdentifier m = replacements.get(target);
        if (m == null) {
            //target does not exist in the map, there's no cycle for sure
            return;
        }
        Set<ModuleIdentifier> visited = new LinkedHashSet<ModuleIdentifier>();
        visited.add(source);
        visited.add(target);

        while(m != null) {
            if (!visited.add(m)) {
                //module was already visited, there is a cycle
                throw new InvalidUserDataException(
                        format("Cannot declare module replacement %s->%s because it introduces a cycle: %s",
                                source, target, Joiner.on("->").join(visited) + "->" + source));
            }
            m = replacements.get(m);
        }
    }

    private static NotationParser<Object, ModuleIdentifier> parser() {
        return NotationParserBuilder
                .toType(ModuleIdentifier.class)
                .parser(new ModuleIdentiferNotationParser())
                .toComposite();
    }
}
