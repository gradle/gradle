/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.store;

import org.gradle.cache.internal.BinaryStore;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.file.RandomAccessFileInputStream;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.kryo.StringDeduplicatingKryoBackedDecoder;
import org.gradle.internal.serialize.kryo.StringDeduplicatingKryoBackedEncoder;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;

import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

class DefaultBinaryStore implements BinaryStore, Closeable {
    private File file;
    private StringDeduplicatingKryoBackedEncoder encoder;
    private long offset = -1;

    public DefaultBinaryStore(File file) {
        this.file = file;
    }

    @Override
    public void write(WriteAction write) {
        if (encoder == null) {
            try {
                encoder = new StringDeduplicatingKryoBackedEncoder(new FileOutputStream(file));
            } catch (FileNotFoundException e) {
                throw throwAsUncheckedException(e);
            }
        }
        if (offset == -1) {
            offset = encoder.getWritePosition();
            if (offset == Integer.MAX_VALUE) {
                throw new IllegalStateException("Unable to write to binary store. "
                        + "The bytes offset has reached a point where using it is unsafe. Please report this error.");
            }
        }
        try {
            write.write(encoder);
        } catch (Exception e) {
            throw new RuntimeException("Problems writing to " + diagnose(), e);
        }
    }

    private String diagnose() {
        return toString() + " (exist: " + file.exists() + ")";
    }

    @Override
    public String toString() {
        return "Binary store in " + file;
    }

    @Override
    public BinaryData done() {
        try {
            if (encoder != null) {
                encoder.done();
                encoder.flush();
            }
            return new SimpleBinaryData(file, offset);
        } finally {
            offset = -1;
        }
    }

    @Override
    public void close() {
        try {
            if (encoder != null) {
                encoder.close();
            }
        } finally {
            if (file != null) {
                file.delete();
            }
            encoder = null;
            file = null;
        }
    }

    File getFile() {
        return file;
    }

    long getSize() {
        return file.length();
    }

    public boolean isInUse() {
        return offset != -1;
    }

    private static class SimpleBinaryData implements BinaryStore.BinaryData {
        private final long offset;
        private final File inputFile;

        private Decoder decoder;
        private CompositeStoppable resources;

        public SimpleBinaryData(File inputFile, long offset) {
            this.inputFile = inputFile;
            this.offset = offset;
        }

        @Override
        public <T> T read(BinaryStore.ReadAction<T> readAction) {
            try {
                if (decoder == null) {
                    RandomAccessFile randomAccess = new RandomAccessFile(inputFile, "r");
                    randomAccess.seek(offset);
                    decoder = new StringDeduplicatingKryoBackedDecoder(new RandomAccessFileInputStream(randomAccess));
                    resources = new CompositeStoppable().add(randomAccess, decoder);
                }
                return readAction.read(decoder);
            } catch (Exception e) {
                throw new RuntimeException("Problems reading data from " + toString(), e);
            }
        }

        @Override
        public void close() {
            try {
                if (resources != null) {
                    resources.stop();
                }
            } catch (Exception e) {
                throw new RuntimeException("Problems cleaning resources of " + toString(), e);
            } finally {
                decoder = null;
                resources = null;
            }
        }

        @Override
        public String toString() {
            return "Binary store in " + inputFile + " offset " + offset + " exists? " + inputFile.exists();
        }
    }
}
