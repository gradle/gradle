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
package org.gradle.api.internal.artifacts.dsl;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.capabilities.Capability;
import org.gradle.internal.Factory;
import org.gradle.internal.component.external.model.DefaultImmutableCapability;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.typeconversion.MapKey;
import org.gradle.internal.typeconversion.MapNotationConverter;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.internal.typeconversion.TypeConversionException;
import org.gradle.internal.typeconversion.TypedNotationConverter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CapabilityNotationParserFactory implements Factory<NotationParser<Object, Capability>> {
    private final static CapabilityNotationParser STRICT_CONVERTER = createSingletonConverter(true);
    private final static CapabilityNotationParser LENIENT_CONVERTER = createSingletonConverter(false);

    private final boolean versionIsRequired;

    public CapabilityNotationParserFactory(boolean versionIsRequired) {
        this.versionIsRequired = versionIsRequired;
    }

    private static CapabilityNotationParser createSingletonConverter(boolean strict) {
        NotationParser<Object, Capability> parser = NotationParserBuilder.toType(Capability.class)
            .converter(new StringNotationParser(strict))
            .converter(strict ? new StrictCapabilityMapNotationParser() : new LenientCapabilityMapNotationParser())
            .toComposite();
        return new CapabilityNotationParser() {
            @Override
            public Capability parseNotation(Object notation) throws TypeConversionException {
                return parser.parseNotation(notation);
            }

            @Override
            public void describe(DiagnosticsVisitor visitor) {
                parser.describe(visitor);
            }
        };
    }

    @Nonnull
    @Override
    public CapabilityNotationParser create() {
        // Currently the converter is stateless, doesn't need any external context, so for performance we return a singleton
        return versionIsRequired ? STRICT_CONVERTER : LENIENT_CONVERTER;
    }

    private static class StringNotationParser extends TypedNotationConverter<CharSequence, Capability> {
        private final boolean versionIsRequired;

        StringNotationParser(boolean versionIsRequired) {
            super(CharSequence.class);
            this.versionIsRequired = versionIsRequired;
        }

        @Override
        protected Capability parseType(CharSequence notation) {
            String stringNotation = notation.toString();
            String[] parts = stringNotation.split(":");
            if (parts.length != 3) {
                if (versionIsRequired || parts.length != 2) {
                    reportInvalidNotation(stringNotation);
                }
            }
            for (String part : parts) {
                if (StringUtils.isEmpty(part)) {
                    reportInvalidNotation(stringNotation);
                }
            }
            String version = parts.length == 3 ? parts[2] : null;
            return new DefaultImmutableCapability(parts[0], parts[1], version);
        }

        private static void reportInvalidNotation(String notation) {
            throw new InvalidUserDataException(
                "Invalid format for capability: '" + notation + "'. The correct notation is a 3-part group:name:version notation, "
                    + "e.g: 'org.group:capability:1.0'");
        }
    }

    private static class StrictCapabilityMapNotationParser extends MapNotationConverter<Capability> {
        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.candidate("Maps").example("[group: 'org.group', name: 'capability', version: '1.0']");
        }

        protected Capability parseMap(@MapKey("group") String group,
                                      @MapKey("name") String name,
                                      @MapKey("version") String version) {
            return new DefaultImmutableCapability(group, name, version);
        }
    }

    private static class LenientCapabilityMapNotationParser extends MapNotationConverter<Capability> {
        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.candidate("Maps").example("[group: 'org.group', name: 'capability', version: '1.0']");
        }

        protected Capability parseMap(@MapKey("group") String group,
                                      @MapKey("name") String name,
                                      @MapKey("version") @Nullable String version) {
            return new DefaultImmutableCapability(group, name, version);
        }
    }
}
