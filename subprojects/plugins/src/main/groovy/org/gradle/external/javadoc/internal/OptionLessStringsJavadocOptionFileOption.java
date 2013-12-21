/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.external.javadoc.internal;

import org.gradle.external.javadoc.OptionLessJavadocOptionFileOption;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class OptionLessStringsJavadocOptionFileOption implements OptionLessJavadocOptionFileOption<List<String>> {
    private List<String> value;

    public OptionLessStringsJavadocOptionFileOption() {
        value = new ArrayList<String>();
    }

    public OptionLessStringsJavadocOptionFileOption(List<String> value) {
        this.value = value;
    }

    public List<String> getValue() {
        return value;
    }

    public void setValue(List<String> value) {
        if (value == null) {
            this.value.clear();
        } else {
            this.value = value;
        }
    }

    public void write(JavadocOptionFileWriterContext writerContext) throws IOException {
        if (value != null && !value.isEmpty()) {
            for (String singleValue : value) {
                writerContext.writeValue(singleValue);
                writerContext.newLine();
            }
        }
    }
}
