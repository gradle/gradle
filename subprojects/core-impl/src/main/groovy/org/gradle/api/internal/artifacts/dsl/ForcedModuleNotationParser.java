/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.IllegalDependencyNotation;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.api.internal.notations.NotationParserBuilder;
import org.gradle.api.internal.notations.api.NotationParser;
import org.gradle.api.internal.notations.api.TopLevelNotationParser;
import org.gradle.api.internal.notations.parsers.MapKey;
import org.gradle.api.internal.notations.parsers.MapNotationParser;
import org.gradle.api.internal.notations.parsers.TypedNotationParser;

import java.util.Collection;
import java.util.Set;

/**
 * by Szczepan Faber, created at: 10/11/11
 */
public class ForcedModuleNotationParser implements TopLevelNotationParser, NotationParser<Set<ModuleVersionSelector>> {

    private NotationParser<Set<ModuleVersionSelector>> delegate = new NotationParserBuilder<ModuleVersionSelector>()
            .resultingType(ModuleVersionSelector.class)
            .parser(new ForcedModuleStringParser())
            .parser(new ForcedModuleMapParser())
            .toFlatteningComposite();

    public Set<ModuleVersionSelector> parseNotation(Object notation) {
        assert notation != null : "notation cannot be null";
        return delegate.parseNotation(notation);
    }

    public void describe(Collection<String> candidateFormats) {
        delegate.describe(candidateFormats);
    }

    static class ForcedModuleMapParser extends MapNotationParser<ModuleVersionSelector> {
        @Override
        public void describe(Collection<String> candidateFormats) {
            candidateFormats.add("Maps, e.g. [group: 'org.gradle', name:'gradle-core', version: '1.0'].");
        }

        protected ModuleVersionSelector parseMap(@MapKey("group") String group, @MapKey("name") String name, @MapKey("version") String version) {
            return selector(group, name, version);
        }
    }

    static class ForcedModuleStringParser extends TypedNotationParser<CharSequence, ModuleVersionSelector> {

        public ForcedModuleStringParser() {
            super(CharSequence.class);
        }

        @Override
        public void describe(Collection<String> candidateFormats) {
            candidateFormats.add("Strings/CharSequences, e.g. 'org.gradle:gradle-core:1.0'.");
        }

        public ModuleVersionSelector parseType(CharSequence notation) {
            ParsedModuleStringNotation parsed;
            try {
                parsed = new ParsedModuleStringNotation(notation.toString(), null);
            } catch (IllegalDependencyNotation e) {
                throw new InvalidUserDataException(
                    "Invalid format: '" + notation + "'. The Correct notation is a 3-part group:name:version notation,"
                    + "e.g: 'org.gradle:gradle-core:1.0'");
            }

            if (parsed.getGroup() == null || parsed.getName() == null || parsed.getVersion() == null) {
                throw new InvalidUserDataException(
                    "Invalid format: '" + notation + "'. Group, name and version cannot be empty. Correct example: "
                    + "'org.gradle:gradle-core:1.0'");
            }
            return selector(parsed.getGroup(), parsed.getName(), parsed.getVersion());
        }
    }

    static ModuleVersionSelector selector(final String group, final String name, final String version) {
        return new DefaultModuleVersionSelector(group, name, version);
    }
}
