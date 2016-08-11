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

import com.google.common.base.Charsets;
import com.google.common.hash.Funnels;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import org.gradle.api.UncheckedIOException;

import java.io.File;
import java.io.IOException;

public class DefaultHasher implements Hasher {
    private static final byte[] SIGNATURE = Hashing.md5().hashString(DefaultHasher.class.getName(), Charsets.UTF_8).asBytes();

    public HashCode hash(File file) {
        try {
            com.google.common.hash.Hasher hasher = Hashing.md5().newHasher();
            hasher.putBytes(SIGNATURE);
            Files.copy(file, Funnels.asOutputStream(hasher));
            return hasher.hash();
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Failed to create MD5 hash for file '%s'.", file), e);
        }
    }
}
