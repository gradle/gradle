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
package org.gradle.api.internal.changedetection.state;

import com.google.common.hash.Hasher;
import org.gradle.api.internal.tasks.compile.ApiClassExtractor;
import org.gradle.internal.nativeintegration.filesystem.FileType;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.internal.Java9ClassReader;

import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class AbiExtractingClasspathContentHasher implements ClasspathContentHasher {
    @Override
    public void updateHash(FileDetails fileDetails, Hasher hasher, byte[] content) {
        String name = fileDetails.getName();
        // ignore non-class files
        if (fileDetails.getType() == FileType.RegularFile && name.endsWith(".class")) {
            try {
                hashClassBytes(hasher, content);
            } catch (Exception e) {
                hasher.putBytes(content);
                DeprecationLogger.nagUserWith("Malformed class file [" + name + "] found on compile classpath, which means that this class will cause a compile error if referenced in a source file. Gradle 5.0 will no longer allow malformed classes on compile classpath.");
            }
        }
    }

    @Override
    public void updateHash(ZipFile zipFile, ZipEntry zipEntry, Hasher hasher, byte[] content) {
        String name = zipEntry.getName();
        // ignore non-class files
        if (!zipEntry.isDirectory() && name.endsWith(".class")) {
            try {
                hashClassBytes(hasher, content);
            } catch (Exception e) {
                hasher.putBytes(content);
                DeprecationLogger.nagUserWith("Malformed class file [" + name + "] in jar [" + zipFile.getName() + "] found on classpath, which means that this class will cause a compile error if referenced in a source file. Gradle 5.0 will no longer allow malformed classes on compile classpath.");
            }
        }
    }

    private void hashClassBytes(Hasher hasher, byte[] classBytes) {
        // Use the ABI as the hash
        ApiClassExtractor extractor = new ApiClassExtractor(Collections.<String>emptySet());
        Java9ClassReader reader = new Java9ClassReader(classBytes);
        if (extractor.shouldExtractApiClassFrom(reader)) {
            byte[] signature = extractor.extractApiClassFrom(reader);
            if (signature != null) {
                hasher.putBytes(signature);
            }
        }
    }
}
