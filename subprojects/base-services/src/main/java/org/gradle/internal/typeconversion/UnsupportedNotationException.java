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
package org.gradle.internal.typeconversion;

import org.gradle.util.GUtil;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Formatter;

public class UnsupportedNotationException extends TypeConversionException {
    private final Object notation;

    public UnsupportedNotationException(Object notation) {
        super("Could not convert " + notation);
        this.notation = notation;
    }

    public UnsupportedNotationException(Object notation, String failure, @Nullable String resolution, Collection<String> candidateTypes) {
        super(format(failure, resolution, candidateTypes));
        this.notation = notation;
    }

    private static String format(String failure, String resolution, Collection<String> formats) {
        Formatter message = new Formatter();
        message.format("%s%n", failure);
        message.format("The following types/formats are supported:");
        for (String format : formats) {
            message.format("%n  - %s", format);
        }
        if (GUtil.isTrue(resolution)) {
            message.format("%n%n%s", resolution);
        }
        return message.toString();
    }

    public Object getNotation() {
        return notation;
    }
}
