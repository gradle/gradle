/*
 * Copyright 2017 the original author or authors.
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

import com.google.common.collect.Lists;

import java.io.IOException;
import java.util.List;

public class MultilineMultiValueJavadocOptionFileOption extends AbstractListJavadocOptionFileOption<List<List<String>>> {
    protected MultilineMultiValueJavadocOptionFileOption(String option, List<List<String>> value, String joinBy) {
        super(option, value, joinBy);
    }

    @Override
    public JavadocOptionFileOptionInternal<List<List<String>>> duplicate() {
        List<List<String>> copyValues = Lists.newArrayList();
        for (List<String> occurrence : getValue()) {
            copyValues.add(Lists.newArrayList(occurrence));
        }
        return new MultilineMultiValueJavadocOptionFileOption(option, copyValues, joinBy);
    }

    @Override
    protected void writeCollectionValue(JavadocOptionFileWriterContext writerContext) throws IOException {
        for (List<String> occurrence : getValue()) {
            writerContext.writeOption(option);
            for (String value : occurrence) {
                writerContext.writeValue(value);
                writerContext.write(joinBy);
            }
            writerContext.newLine();
        }

    }
}
