/*
 * Copyright 2012 the original author or authors.
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

class CharSequenceNotationParser implements NotationConverter<String, String> {
    @Override
    public void convert(String notation, NotationConvertResult<? super String> result) throws TypeConversionException {
        result.converted(notation);
    }

    @Override
    public void describe(DiagnosticsVisitor visitor) {
        visitor.candidate("String or CharSequence instances.");
    }
}
