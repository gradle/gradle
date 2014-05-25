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
import org.gradle.util.GUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class EnumFromCharSequenceNotationParser<T extends Enum> implements ValueAwareNotationParser<T> {
    private final Class<? extends T> type;

    public EnumFromCharSequenceNotationParser(Class<? extends T> enumType) {
        assert enumType.isEnum() : "resultingType must be enum";
        this.type = enumType;
    }

    public T parseNotation(CharSequence notation) throws UnsupportedNotationException, TypeConversionException {
        final String enumString = notation.toString();
        List<? extends T> enumConstants = Arrays.asList(type.getEnumConstants());
        T match = CollectionUtils.findFirst(enumConstants, new Spec<T>() {
            public boolean isSatisfiedBy(T enumValue) {
                return enumValue.name().equalsIgnoreCase(enumString);
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
    }

    public void describe(Collection<String> candidateFormats) {
        List<String> values = new ArrayList<String>();
        describeValues(values);
        candidateFormats.add(String.format("One of the following values: %s", GUtil.toString(values)));
    }

    public void describeValues(Collection<String> collector) {
        final Enum[] enumConstants = type.getEnumConstants();
        for (Enum enumConstant : enumConstants) {
            collector.add(enumConstant.name());
        }
    }
}