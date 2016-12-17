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

import com.google.common.base.Charsets;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.archive.ZipFileTree;
import org.gradle.api.internal.file.collections.FileTreeAdapter;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.api.internal.tasks.compile.ApiClassExtractor;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.resource.TextResource;
import org.gradle.util.internal.Java9ClassReader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

public class JvmClassHasher implements FileHasher {
    private static final byte[] SIGNATURE = Hashing.md5().hashString(JvmClassHasher.class.getName(), Charsets.UTF_8).asBytes();
    private final FileHasher delegate;

    public JvmClassHasher(FileHasher hasher) {
        this.delegate = hasher;
    }

    @Override
    public HashCode hash(TextResource resource) {
        return delegate.hash(resource);
    }

    @Override
    public HashCode hash(final File file) {
        String fileName = file.getName();
        if (fileName.endsWith(".class")) {
            return hashClassFile(file);

        } else if (fileName.endsWith(".jar")) {
            return hashJarFile(file);
        }
        return delegate.hash(file);
    }

    private HashCode hashClassFile(File file) {
        try {
            byte[] src = Files.toByteArray(file);
            Hasher hasher = createHasher();
            hashClassBytes(hasher, src);
            return hasher.hash();
        } catch (IOException e) {
            return delegate.hash(file);
        }
    }

    private static void hashClassBytes(Hasher hasher, byte[] classBytes) {
        // Use the ABI as the hash
        ApiClassExtractor extractor = new ApiClassExtractor(Collections.<String>emptySet());
        Java9ClassReader reader = new Java9ClassReader(classBytes);
        if (extractor.shouldExtractApiClassFrom(reader)) {
            byte[] signature = extractor.extractApiClassFrom(reader);
            /*
            Hasher tmp = createHasher();
            tmp.putBytes(signature);
            System.out.println(tmp.hash());
            */
            hasher.putBytes(signature);
        }
    }

    private static HashCode hashJarFile(File file) {
        ZipFileTree zipTree = new ZipFileTree(file, null, null, null);
        final Hasher hasher = createHasher();
        FileTreeAdapter adapter = new FileTreeAdapter(zipTree);
        adapter.visit(new HashingJarVisitor(hasher));
        HashCode hash = hasher.hash();
        //System.err.println(file.getName() + " = " + hash);
        return hash;
    }

    private static Hasher createHasher() {
        final Hasher hasher = Hashing.md5().newHasher();
        hasher.putBytes(SIGNATURE);
        return hasher;
    }

    @Override
    public HashCode hash(FileTreeElement fileDetails) {
        return hash(fileDetails.getFile());
    }

    private static class HashingJarVisitor implements FileVisitor {
        private final Hasher hasher;

        public HashingJarVisitor(Hasher hasher) {
            this.hasher = hasher;
        }

        @Override
        public void visitDir(FileVisitDetails dirDetails) {

        }

        @Override
        public void visitFile(FileVisitDetails fileDetails) {
            InputStream inputStream = fileDetails.open();
            byte[] src;
            try {
                src = ByteStreams.toByteArray(inputStream);
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            } finally {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
            if (fileDetails.getName().endsWith(".class")) {
                // System.out.print("Class = " + fileDetails.getName() + " : ");
                hashClassBytes(hasher, src);
            } else {
                // TODO: Excluding resources is not a good idea for release
                // because we cannot make the difference between using a compiler
                // that cares about them or not (javac vs APT vs groovy ...)
                //System.out.println("Regular file = " + fileDetails.getName());

                //hasher.putBytes(src);
            }

        }
    }
}
