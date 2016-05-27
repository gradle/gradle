/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.file.collections;

import org.gradle.test.fixtures.file.TestFile;

import java.util.Arrays;

/**
 * A version of TestFile which always returns the list of children in the same order.
 */
public class StableTestFile extends TestFile {
    public static StableTestFile root(TestFile file) {
        return new StableTestFile(file);
    }

    private StableTestFile(TestFile file) {
        super(file);
    }

    @Override
    public StableTestFile[] listFiles() {
        TestFile[] files = super.listFiles();
        Arrays.sort(files);
        StableTestFile[] stableFiles = new StableTestFile[files.length];
        for (int i = 0; i < files.length; i++) {
            TestFile file = files[i];
            stableFiles[i] = new StableTestFile(file);
        }
        return stableFiles;
    }

    @Override
    public StableTestFile createDir() {
        return new StableTestFile(super.createDir());
    }

    @Override
    public StableTestFile createDir(Object path) {
        return new StableTestFile(super.createDir(path));
    }

    @Override
    public StableTestFile createFile() {
        return new StableTestFile(super.createFile());
    }

    @Override
    public StableTestFile createFile(Object path) {
        return new StableTestFile(super.createFile(path));
    }
}
