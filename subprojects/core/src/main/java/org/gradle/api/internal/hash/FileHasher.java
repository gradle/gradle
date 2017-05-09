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
package org.gradle.api.internal.hash;

import com.google.common.hash.HashCode;
import org.gradle.api.file.FileTreeElement;
import org.gradle.internal.nativeintegration.filesystem.FileMetadataSnapshot;
import org.gradle.internal.resource.TextResource;

import java.io.File;
import java.io.InputStream;

public interface FileHasher {
    /**
     * Returns the hash of the given input stream.
     */
    HashCode hash(InputStream inputStream);

    /**
     * Returns the hash of the current content of the given resource. The provided resource must have content available.
     */
    HashCode hash(TextResource resource);

    /**
     * Returns the hash of the current content of the given file. The provided file must exist and be a file (rather than, say, a directory).
     */
    HashCode hash(File file);

    /**
     * Returns the hash of the current content of the given file, assuming the given file metadata. The provided file must exist and be a file (rather than, say, a directory).
     */
    HashCode hash(FileTreeElement fileDetails);

    /**
     * Returns the hash of the current content of the given file, assuming the given file metadata. The provided file must exist and be a file (rather than, say, a directory).
     */
    HashCode hash(File file, FileMetadataSnapshot fileDetails);
}
