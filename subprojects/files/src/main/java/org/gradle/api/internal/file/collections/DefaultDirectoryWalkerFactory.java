/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.api.JavaVersion;
import org.gradle.api.internal.file.collections.jdk7.Jdk7DirectoryWalker;
import org.gradle.internal.Factory;
import org.gradle.internal.nativeintegration.services.FileSystems;
import org.gradle.internal.nativeplatform.filesystem.FileSystem;

public class DefaultDirectoryWalkerFactory implements Factory<DirectoryWalker> {
    private final JavaVersion javaVersion;
    private final FileSystem fileSystem;
    private DirectoryWalker instance;

    public DefaultDirectoryWalkerFactory(JavaVersion javaVersion, FileSystem fileSystem) {
        this.javaVersion = javaVersion;
        this.fileSystem = fileSystem;
        reset();
    }

    DefaultDirectoryWalkerFactory() {
        this(JavaVersion.current(), FileSystems.getDefault());
    }

    @Override
    public DirectoryWalker create() {
        return instance;
    }

    private void reset() {
        this.instance = createInstance();
    }

    private DirectoryWalker createInstance() {
        return new Jdk7DirectoryWalker(fileSystem);
    }
}
