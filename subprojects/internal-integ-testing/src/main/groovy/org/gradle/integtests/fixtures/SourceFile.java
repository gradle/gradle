/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.integtests.fixtures;

import com.google.common.base.Joiner;
import org.gradle.test.fixtures.file.TestFile;

public class SourceFile {
    private final String path;
    private final String name;
    private final String content;

    public SourceFile(String path, String name, String content) {
        this.content = content;
        this.path = path;
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public String getContent() {
        return content;
    }

    public TestFile writeToDir(TestFile base) {
        TestFile file = base.file(path, name);
        writeToFile(file);
        return file;
    }

    public void writeToFile(TestFile file) {
        if (file.exists()) {
            file.write("");
        }
        file.write(content);
    }

    public String withPath(String basePath) {
        return Joiner.on('/').join(basePath, path, name);
    }
}
