/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.snapshot;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.BiPredicate;

public interface PatternFilterStrategy<PATTERN> {
    PATTERN empty();
    boolean isEmpty(PATTERN pattern);
    BiPredicate<FileSystemLocationSnapshot, Iterable<String>> getAsSnapshotPredicate(PATTERN pattern);
    DirectoryWalkerPredicate getAsDirectoryWalkerPredicate(PATTERN pattern);

    interface DirectoryWalkerPredicate {
        boolean test(Path path, String name, boolean isDirectory, @Nullable BasicFileAttributes attrs, Iterable<String> relativePath);
    }
}
