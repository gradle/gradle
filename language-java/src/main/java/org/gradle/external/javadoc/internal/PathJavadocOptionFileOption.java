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

import com.google.common.collect.Lists;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class PathJavadocOptionFileOption extends AbstractListJavadocOptionFileOption<List<File>> {

    public PathJavadocOptionFileOption(String option, List<File> value, String joinBy) {
        super(option, value, joinBy);
    }

    @Override
    public void writeCollectionValue(JavadocOptionFileWriterContext writerContext) throws IOException {
        writerContext.writePathOption(option, value, joinBy);
    }

    @Override
    public PathJavadocOptionFileOption duplicate() {
        List<File> duplicateValue = Lists.newArrayList(value);
        return new PathJavadocOptionFileOption(option, duplicateValue, joinBy);
    }
}
