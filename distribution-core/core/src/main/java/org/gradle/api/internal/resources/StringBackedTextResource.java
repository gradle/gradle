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
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.resources.ResourceException;
import org.gradle.api.resources.internal.TextResourceInternal;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;

public class StringBackedTextResource implements TextResourceInternal {
    private final TemporaryFileProvider tempFileProvider;
    private final String string;

    public StringBackedTextResource(TemporaryFileProvider tempFileProvider, String string) {
        this.tempFileProvider = tempFileProvider;
        this.string = string;
    }

    @Override
    public String getDisplayName() {
        return "text resource";
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public String asString() {
        return string;
    }

    @Override
    public Reader asReader() {
        return new StringReader(string);
    }

    @Override
    public File asFile(String charset) {
        File file = tempFileProvider.createTemporaryFile("string", ".txt", "resource");
        try {
            Files.asCharSink(file, Charset.forName(charset)).write(string);
        } catch (IOException e) {
            throw new ResourceException("Could not write " + getDisplayName() + " content to " + file + ".", e);
        }
        return file;
    }

    @Override
    public File asFile() {
        return asFile(Charset.defaultCharset().name());
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return TaskDependencyInternal.EMPTY;
    }

    @Override
    public Object getInputProperties() {
        return string;
    }

    @Override
    public FileCollection getInputFiles() {
        return null;
    }
}
