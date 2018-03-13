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
import org.gradle.api.capabilities.CapabilityDescriptor;
import org.gradle.internal.Factory;
import org.gradle.internal.component.external.model.ImmutableCapability;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.typeconversion.MapKey;
import org.gradle.internal.typeconversion.MapNotationConverter;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.internal.typeconversion.TypedNotationConverter;

import javax.annotation.Nullable;

public class CapabilityNotationParserFactory implements Factory<NotationParser<Object, CapabilityDescriptor>> {
    private final static NotationParser<Object, CapabilityDescriptor> SINGLETON_CONVERTER = createSingletonConverter();

    private static NotationParser<Object, CapabilityDescriptor> createSingletonConverter() {
        return NotationParserBuilder.toType(CapabilityDescriptor.class)
            .converter(new StringNotationParser())
            .converter(new CapabilityMapNotationParser())
            .toComposite();
    }

    @Nullable
    @Override
    public NotationParser<Object, CapabilityDescriptor> create() {
        // Currently the converter is stateless, doesn't need any external context, so for performance we return a singleton
        return SINGLETON_CONVERTER;
    }

    private static class StringNotationParser extends TypedNotationConverter<String, CapabilityDescriptor> {

        StringNotationParser() {
            super(String.class);
        }

        @Override
        protected CapabilityDescriptor parseType(String notation) {
            String[] parts = notation.split(":");
            if (parts.length != 3) {
                reportInvalidNotation(notation);
            }
            for (String part : parts) {
                if (StringUtils.isEmpty(part)) {
                    reportInvalidNotation(notation);
                }
            }
            return new ImmutableCapability(parts[0], parts[1], parts[2]);
        }

        private static void reportInvalidNotation(String notation) {
            throw new InvalidUserDataException(
                "Invalid format for capability: '" + notation + "'. The correct notation is a 3-part group:name:version notation, "
                    + "e.g: 'org.group:capability:1.0'");
        }
    }

    private static class CapabilityMapNotationParser extends MapNotationConverter<CapabilityDescriptor> {
        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.candidate("Maps").example("[group: 'org.group', name: 'capability', version: '1.0']");
        }

        protected CapabilityDescriptor parseMap(@MapKey("group") String group,
                                                @MapKey("name") String name,
                                                @MapKey("version") String version) {
            return new ImmutableCapability(group, name, version);
        }
    }
}
