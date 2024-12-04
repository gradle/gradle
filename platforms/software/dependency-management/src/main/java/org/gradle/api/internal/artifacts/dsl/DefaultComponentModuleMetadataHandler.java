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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ComponentModuleMetadataDetails;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ComponentModuleMetadataHandlerInternal;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.notations.ModuleIdentifierNotationConverter;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static java.lang.String.format;

public class DefaultComponentModuleMetadataHandler implements ComponentModuleMetadataHandlerInternal  {

    private final Map<ModuleIdentifier, ImmutableModuleReplacements.Replacement> replacements = new HashMap<>();
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;

    public DefaultComponentModuleMetadataHandler(ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        this.moduleIdentifierFactory = moduleIdentifierFactory;
    }

    @Override
    public void module(Object moduleNotation, Action<? super ComponentModuleMetadataDetails> rule) {
        rule.execute(module(moduleNotation));
    }

    @Override
    public ImmutableModuleReplacements getModuleReplacements() {
        return new ImmutableModuleReplacements(ImmutableMap.copyOf(replacements));
    }

    @VisibleForTesting
    public ComponentModuleMetadataDetails module(final Object sourceModule) {
        final NotationParser<Object, ModuleIdentifier> parser = parser(moduleIdentifierFactory);
        final ModuleIdentifier source = parser.parseNotation(sourceModule);
        return new ComponentModuleMetadataDetails() {
            @Override
            public void replacedBy(Object moduleNotation) {
                replacedBy(moduleNotation, null);
            }

            @Override
            public void replacedBy(final Object targetModule, @Nullable String reason) {
                ModuleIdentifier target = parser.parseNotation(targetModule);
                detectCycles(replacements, source, target);
                replacements.put(source, new ImmutableModuleReplacements.Replacement(target, reason));
            }

            @Override
            public ModuleIdentifier getId() {
                return source;
            }

            @Override
            public ModuleIdentifier getReplacedBy() {
                return unwrap(replacements.get(source));
            }
        };
    }

    private static void detectCycles(Map<ModuleIdentifier, ImmutableModuleReplacements.Replacement> replacements, ModuleIdentifier source, ModuleIdentifier target) {
        if (source.equals(target)) {
            throw new InvalidUserDataException(String.format("Cannot declare module replacement that replaces self: %s->%s", source, target));
        }

        ModuleIdentifier m = unwrap(replacements.get(target));
        if (m == null) {
            //target does not exist in the map, there's no cycle for sure
            return;
        }
        Set<ModuleIdentifier> visited = new LinkedHashSet<>();
        visited.add(source);
        visited.add(target);

        while(m != null) {
            if (!visited.add(m)) {
                //module was already visited, there is a cycle
                throw new InvalidUserDataException(
                        format("Cannot declare module replacement %s->%s because it introduces a cycle: %s",
                                source, target, Joiner.on("->").join(visited) + "->" + source));
            }
            m = unwrap(replacements.get(m));
        }
    }

    @Nullable
    private static ModuleIdentifier unwrap(@Nullable ImmutableModuleReplacements.Replacement replacement) {
        return replacement == null ? null : replacement.getTarget();
    }

    private static NotationParser<Object, ModuleIdentifier> parser(ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        return NotationParserBuilder
                .toType(ModuleIdentifier.class)
                .fromCharSequence(new ModuleIdentifierNotationConverter(moduleIdentifierFactory))
                .toComposite();
    }
}
