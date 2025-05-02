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

package org.gradle.caching.internal.tasks;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class DirectFileFileAccessor extends AbstractFileAccessor {

    public DirectFileFileAccessor(DirectoryProvider directoryProvider) {
        super(directoryProvider);
    }

    @Override
    protected InputStream openInput(Path path) throws IOException {
        return Files.newInputStream(path);
    }

    @Override
    protected OutputStream openOutput(Path path) throws IOException {
        return Files.newOutputStream(path);
    }
}
