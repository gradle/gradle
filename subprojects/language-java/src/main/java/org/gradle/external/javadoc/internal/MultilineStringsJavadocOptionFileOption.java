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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MultilineStringsJavadocOptionFileOption extends AbstractListJavadocOptionFileOption<List<String>> {

    // We should never attempt to join strings so if you see this, there's a problem
    private static final String JOIN_BY = "Not Used!";

    protected MultilineStringsJavadocOptionFileOption(String option) {
        super(option, new ArrayList<String>(), JOIN_BY);
    }

    protected MultilineStringsJavadocOptionFileOption(String option, List<String> value) {
        super(option, value, JOIN_BY);
    }

    @Override
    public void writeCollectionValue(JavadocOptionFileWriterContext writerContext) throws IOException {
        if (value != null && !value.isEmpty()) {
            writerContext.writeMultilineValuesOption(option, value);
        }
    }
}
