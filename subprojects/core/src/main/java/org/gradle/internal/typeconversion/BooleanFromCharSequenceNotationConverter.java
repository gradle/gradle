/*
 * Copyright 2023 the original author or authors.
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

public class BooleanFromCharSequenceNotationConverter implements NotationConverter<CharSequence, Boolean> {

    @Override
    public void convert(CharSequence notation, NotationConvertResult<? super Boolean> result) throws TypeConversionException {
        try {
            result.converted(Boolean.valueOf(notation.toString()));
        } catch (Exception e) {
            throw new TypeConversionException(String.format("Cannot convert string value '%s' to a Boolean.", notation), e);
        }
    }

    @Override
    public void describe(DiagnosticsVisitor visitor) {
        visitor.candidate("A Boolean.");
    }
}
