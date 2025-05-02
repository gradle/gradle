/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.internal.exceptions.DiagnosticsVisitor;

public class IntegerFromCharSequenceNotationConverter implements NotationConverter<CharSequence, Integer> {

    @Override
    public void convert(CharSequence notation, NotationConvertResult<? super Integer> result) throws TypeConversionException {
        try {
            result.converted(Integer.valueOf(notation.toString()));
        } catch (NumberFormatException ex) {
            throw new TypeConversionException(String.format("Cannot convert string value '%s' to an integer.", notation), ex);
        }
    }

    @Override
    public void describe(DiagnosticsVisitor visitor) {
        visitor.candidate("An integer.");
    }
}
