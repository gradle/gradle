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
package org.gradle.integtests;

import org.apache.commons.io.FileUtils;
import org.gradle.api.UncheckedIOException;

import java.io.File;
import java.io.IOException;
import java.util.Formatter;
import java.util.List;

public class TestFile {
    private final File file;

    public TestFile(File file) {
        this.file = file.getAbsoluteFile();
    }

    public TestFile writelns(String... lines) {
        Formatter formatter = new Formatter();
        for (String line : lines) {
            formatter.format("%s%n", line);
        }
        return write(formatter);
    }

    public TestFile write(Object content) {
        try {
            FileUtils.writeStringToFile(file, content.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    public void touch() {
        try {
            FileUtils.touch(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public File asFile() {
        return file;
    }

    @Override
    public String toString() {
        return file.getPath();
    }

    public TestFile writelns(List<String> lines) {
        Formatter formatter = new Formatter();
        for (String line : lines) {
            formatter.format("%s%n", line);
        }
        return write(formatter);
    }
}
