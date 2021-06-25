/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.internal.fingerprint.LineEndingSensitivity;
import org.gradle.internal.fingerprint.hashing.RegularFileSnapshotContext;
import org.gradle.internal.fingerprint.hashing.ResourceHasher;
import org.gradle.internal.fingerprint.hashing.ZipEntryContext;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.snapshot.RegularFileSnapshot;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.function.Predicate;

/**
 * A {@link ResourceHasher} that ignores line endings while hashing the file.  It detects whether a file is text or binary and only
 * normalizes line endings for text files.  If a file is detected to be binary, we fall back to the existing non-normalized hash.
 */
public class LineEndingAwareResourceHasher implements ResourceHasher {
    private final ResourceHasher delegate;
    private final LineEndingSensitivity lineEndingSensitivity;
    private final StreamHasher streamHasher;

    public LineEndingAwareResourceHasher(ResourceHasher delegate, LineEndingSensitivity lineEndingSensitivity, StreamHasher streamHasher) {
        this.delegate = delegate;
        this.lineEndingSensitivity = lineEndingSensitivity;
        this.streamHasher = streamHasher;
    }

    @Override
    public void appendConfigurationToHasher(Hasher hasher) {
        delegate.appendConfigurationToHasher(hasher);
        hasher.putString(getClass().getName());
        hasher.putString(LineEndingNormalizingInputStream.class.getName());
        hasher.putString(lineEndingSensitivity.name());
    }

    @Nullable
    @Override
    public HashCode hash(RegularFileSnapshotContext snapshotContext) {
        return lineEndingSensitivity.isCandidate(snapshotContext.getSnapshot()) ?
            hashContent(snapshotContext.getSnapshot()) :
            delegate.hash(snapshotContext);
    }

    @Nullable
    @Override
    public HashCode hash(ZipEntryContext zipEntryContext) throws IOException {
        return lineEndingSensitivity.isCandidate(zipEntryContext) ?
            hashContent(zipEntryContext) :
            delegate.hash(zipEntryContext);
    }

    @Nullable
    private HashCode hashContent(RegularFileSnapshot snapshot) {
        NormalizedContentInfo normalizedContentInfo = collectContentInfo(new File(snapshot.getAbsolutePath()));
        return normalizedContentInfo.getContentType() == FileContentType.TEXT ?
            normalizedContentInfo.getHash() :
            snapshot.getHash();
    }

    @Nullable
    private HashCode hashContent(ZipEntryContext zipEntryContext) throws IOException {
        NormalizedContentInfo normalizedContentInfo = zipEntryContext.getEntry().withInputStream(this::collectContentInfo);
        return normalizedContentInfo.getContentType() == FileContentType.TEXT ?
            normalizedContentInfo.getHash() :
            delegate.hash(zipEntryContext);
    }


    public NormalizedContentInfo collectContentInfo(InputStream inputStream) {
        FileContentTypeDetectingInputStream contentTypeDetectingInputStream = new FileContentTypeDetectingInputStream(inputStream);
        ShortCircuitingInputStream<FileContentTypeDetectingInputStream> shortCircuitingInputStream = new ShortCircuitingInputStream<>(
            contentTypeDetectingInputStream,
            stream -> stream.getContentType() == FileContentType.BINARY
        );
        InputStream normalizedInputStream = new LineEndingNormalizingInputStream(shortCircuitingInputStream);

        try {
            HashCode hashCode = streamHasher.hash(normalizedInputStream);
            return new NormalizedContentInfo(hashCode, contentTypeDetectingInputStream.getContentType(), shortCircuitingInputStream.isShortCircuited());
        } finally {
            try {
                contentTypeDetectingInputStream.close();
            } catch (IOException ignored) {
                // Ignored
            }

            try {
                shortCircuitingInputStream.close();
            } catch (IOException ignored) {
                // Ignored
            }

            try {
                normalizedInputStream.close();
            } catch (IOException ignored) {
                // Ignored
            }
        }
    }

    public NormalizedContentInfo collectContentInfo(File file) {
        FileInputStream inputStream;
        try {
            inputStream = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(String.format("Failed to create MD5 hash for file '%s' as it does not exist.", file), e);
        }
        try {
            return collectContentInfo(inputStream);
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                // Ignored
            }
        }
    }

    private static class NormalizedContentInfo {
        private final HashCode hash;
        private final FileContentType contentType;
        private final boolean incompleteHash;

        public NormalizedContentInfo(HashCode hash, FileContentType contentType, boolean incompleteHash) {
            this.hash = hash;
            this.contentType = contentType;
            this.incompleteHash = incompleteHash;
        }

        public FileContentType getContentType() {
            return contentType;
        }

        public HashCode getHash() {
            // If reading of the input stream was short circuited because we detected a binary file,
            // we should never use the hash as it is incomplete.
            if (incompleteHash) {
                throw new IllegalStateException();
            } else {
                return hash;
            }
        }
    }

    /**
     * This InputStream stops the hashing once a binary file is detected.
     */
    private static class ShortCircuitingInputStream<T extends InputStream> extends FilterInputStream {
        private static final int EOF = -1;

        private final T inputStream;
        private final Predicate<T> shortCircuitCondition;
        private boolean shortCircuited;

        public ShortCircuitingInputStream(T inputStream, Predicate<T> shortCircuitCondition) {
            super(inputStream);
            this.inputStream = inputStream;
            this.shortCircuitCondition = shortCircuitCondition;
        }

        @Override
        public int read() throws IOException {
            if (shortCircuitCondition.test(inputStream)) {
                shortCircuited = true;
                return EOF;
            } else {
                return super.read();
            }
        }

        @Override
        public int read(byte[] b) throws IOException {
            if (shortCircuitCondition.test(inputStream)) {
                shortCircuited = true;
                return EOF;
            } else {
                return super.read(b);
            }
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (shortCircuitCondition.test(inputStream)) {
                shortCircuited = true;
                return EOF;
            } else {
                return super.read(b, off, len);
            }
        }

        private boolean isShortCircuited() {
            return shortCircuited;
        }
    }
}
