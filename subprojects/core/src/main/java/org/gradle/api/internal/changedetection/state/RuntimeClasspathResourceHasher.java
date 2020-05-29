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

package org.gradle.api.internal.changedetection.state;

import com.google.common.io.ByteStreams;
import org.gradle.api.internal.file.archive.ZipEntry;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.hash.HashingOutputStream;
import org.gradle.internal.snapshot.RegularFileSnapshot;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * Hashes contents of resources files and {@link ZipEntry}s) in runtime classpath entries.
 *
 * Currently, we take the unmodified content into account but we could be smarter at some point.
 */
public class RuntimeClasspathResourceHasher implements ResourceHasher {

    @Nullable
    @Override
    public HashCode hash(RegularFileSnapshot fileSnapshot) {
        return fileSnapshot.getHash();
    }

    @Override
    public HashCode hash(ZipEntryContext zipEntryContext) throws IOException {
        HashingOutputStream hasher = Hashing.primitiveStreamHasher();
        ByteStreams.copy(zipEntryContext.getEntry().getInputStream(), hasher);
        return hasher.hash();
    }

    @Override
    public void appendConfigurationToHasher(Hasher hasher) {
        hasher.putString(getClass().getName());
    }
}
