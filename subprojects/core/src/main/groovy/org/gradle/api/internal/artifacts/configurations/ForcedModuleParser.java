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

package org.gradle.api.internal.artifacts.configurations;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.DefaultResolvedModuleId;
import org.gradle.api.internal.notations.*;
import org.gradle.util.ConfigureUtil;

import java.util.*;

import static java.util.Arrays.asList;

/**
 * by Szczepan Faber, created at: 10/11/11
 */
public class ForcedModuleParser implements NotationParser<Set<ModuleIdentifier>> {

    private DefaultNotationParser delegate = new NotationParserBuilder()
            .resultingType(ModuleIdentifier.class)
            .stringParser(new ForcedModuleStringParser())
            .mapParser(new ForcedModuleMapParser())
            .invalidNotationMessage(
                            "Input notation cannot be used to form the forced module.\n"
                            + "Notation only supports following types/formats:\n"
                            + "  1. instances of ModuleIdentifier\n"
                            + "  2. Strings (actually CharSequences), e.g. 'org.gradle:gradle-core:1.0'\n"
                            + "  3. Maps, e.g. [group: 'org.gradle', name:'gradle-core', version: '1.0']\n"
                            + "  4. A Collection or array of above (nested collections/arrays will be flattened)\n"
            )
            .build();

    public boolean canParse(Object notation) {
        return delegate.canParse(notation); //TODO SF - add coverage or segregate interfaces
    }

    public Set<ModuleIdentifier> parseNotation(Object notation) {
        assert notation != null : "notation cannot be null";
        Collection<ModuleIdentifier> parsed = delegate.parseNotation(notation);
        return new LinkedHashSet<ModuleIdentifier>(parsed);
    }

    static class ForcedModuleMapParser extends MapNotationParser<ModuleIdentifier> {
        protected ModuleIdentifier parseMap(Map notation) {
            ModuleIdentifier out = new DefaultResolvedModuleId(null, null, null);
            List<String> mandatoryKeys = asList("group", "name", "version");
            try {
                ConfigureUtil.configureByMap(notation, out, mandatoryKeys);
            } catch (ConfigureUtil.IncompleteInputException e) {
                throw new DefaultNotationParser.InvalidNotationFormat(
                          "Invalid format: " + notation + ". Missing mandatory key(s): " + e.getMissingKeys() + "\n"
                        + "The correct notation is a map with keys: " + mandatoryKeys + ", for example: [group: 'org.gradle', name:'gradle-core', version: '1.0']", e);
            }
            return out;
        }
    }

    static class ForcedModuleStringParser extends StringNotationParser<ModuleIdentifier> {
        public ModuleIdentifier parseString(String notation) {
            String[] split = notation.split(":");
            if (split.length != 3) {
                throw new DefaultNotationParser.InvalidNotationFormat(
                    "Invalid format: '" + notation + "'. The Correct notation is a 3-part group:name:version notation,"
                    + "e.g: org.gradle:gradle-core:1.0");
            }
            final String group = split[0];
            final String name = split[1];
            final String version = split[2];
            return identifier(group, name, version);
        }
    }

    static ModuleIdentifier identifier(final String group, final String name, final String version) {
        return new DefaultResolvedModuleId(group, name, version);
    }
}
