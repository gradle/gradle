/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.typeconversion;

import org.gradle.api.specs.Spec;
import org.gradle.util.CollectionUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class EnumFromStringNotationParser<T extends Object> extends TypedNotationParser<CharSequence, T> {

    private final Class<T> type;

    public EnumFromStringNotationParser(Class<T> enumType){
        super(CharSequence.class);
        assert enumType.isEnum() : "resultingType must be enum";
        this.type = enumType;
    }

    @Override
    protected T parseType(CharSequence notation) {
        if(type.isEnum()) {
            final String enumString = notation.toString();
            List<T> enumConstants = Arrays.asList(type.getEnumConstants());
            T match = CollectionUtils.findFirst(enumConstants, new Spec<T>() {
                public boolean isSatisfiedBy(T enumValue) {
                    return ((Enum)enumValue).name().equalsIgnoreCase(enumString);
                }
            });
            if (match == null) {
                throw new TypeConversionException(
                        String.format("Cannot coerce string value '%s' to an enum value of type '%s' (valid case insensitive values: %s)",
                                enumString, type.getName(), CollectionUtils.toStringList(Arrays.asList(type.getEnumConstants()))
                        )
                );
            } else {
                return match;
            }
        }else{
            throw new UnsupportedNotationException("type must be an Enum");
        }

    }

    public void describe(Collection<String> candidateFormats) {
        if (type.isEnum()) {
            final Enum[] enumConstants = (Enum[]) type.getEnumConstants();
            for (Enum enumConstant : enumConstants) {
                candidateFormats.add(enumConstant.name());
            }
        }
    }
}