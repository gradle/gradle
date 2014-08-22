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

import java.io.File;
import java.io.IOException;

/**
 * A {@link org.gradle.external.javadoc.JavadocOptionFileOption} whose value is a file.
 */
public class FileJavadocOptionFileOption extends AbstractJavadocOptionFileOption<File> {
    protected FileJavadocOptionFileOption(String option) {
        super(option);
    }

    protected FileJavadocOptionFileOption(String option, File value) {
        super(option, value);
    }

    public void write(JavadocOptionFileWriterContext writerContext) throws IOException {
        if (value != null) {
            writerContext.writeValueOption(option, value.getAbsolutePath());
        }
    }
}
