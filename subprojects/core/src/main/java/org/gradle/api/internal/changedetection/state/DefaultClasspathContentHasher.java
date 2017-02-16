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

import com.google.common.hash.Hasher;

import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class DefaultClasspathContentHasher implements ClasspathContentHasher {

    @Override
    public boolean updateHash(FileDetails fileDetails, Hasher hasher, byte[] content) {
        return hashContent(hasher, content);
    }

    @Override
    public void updateHash(ZipFile zipFile, ZipEntry zipEntry, Hasher hasher, byte[] content) {
        hashContent(hasher, content);
    }

    private boolean hashContent(Hasher hasher, byte[] content) {
        // TODO: Deeper analysis of .class files for runtime
        // TODO: Sort entries in META-INF/ignore some entries
        // TODO: Sort entries in .properties/ignore some entries
        hasher.putBytes(content);
        return true;
    }
}
