/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.internal.resources;

import com.google.common.io.Files;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.resources.TextResource;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;

public class StringBackedTextResource implements TextResource {
    private final TemporaryFileProvider tempFileProvider;
    private final String string;

    public StringBackedTextResource(TemporaryFileProvider tempFileProvider, String string) {
        this.tempFileProvider = tempFileProvider;
        this.string = string;
    }

    public String asString() {
        return string;
    }

    public Reader asReader() {
        return new StringReader(string);
    }

    public File asFile() {
        File file = tempFileProvider.createTemporaryFile("string", ".txt", "resource");
        try {
            Files.write(string, file, Charset.defaultCharset());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    public TaskDependency getBuildDependencies() {
        return new DefaultTaskDependency();
    }

    public Object getInputProperties() {
        return asString();
    }

    public Object getInputFiles() {
        return null;
    }
}
