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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification;

import com.google.common.io.Files;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.HashFunction;

import java.io.File;
import java.io.IOException;

class ChecksumHasher implements FileHasher {

    private final HashFunction hashFunction;

    public ChecksumHasher(HashFunction hashFunction) {
        this.hashFunction = hashFunction;
    }

    @Override
    public HashCode hash(File file) {
        try {
            return hashFunction.hashBytes(Files.toByteArray(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public HashCode hash(File file, long length, long lastModified) {
        return hash(file);
    }

}
