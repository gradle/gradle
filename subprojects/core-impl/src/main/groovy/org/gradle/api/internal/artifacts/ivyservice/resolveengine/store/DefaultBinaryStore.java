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

import org.gradle.api.internal.cache.BinaryStore;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.io.RandomAccessFileInputStream;
import org.gradle.messaging.serialize.Decoder;
import org.gradle.messaging.serialize.kryo.KryoBackedDecoder;
import org.gradle.messaging.serialize.kryo.KryoBackedEncoder;

import java.io.*;

import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

class DefaultBinaryStore implements BinaryStore, Closeable {
    private File file;
    private KryoBackedEncoder encoder;
    private int offset = -1;

    public DefaultBinaryStore(File file) {
        this.file = file;
    }

    public void write(WriteAction write) {
        if (encoder == null) {
            try {
                encoder = new KryoBackedEncoder(new FileOutputStream(file));
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

    public String toString() {
        return "Binary store in " + file;
    }

    public BinaryData done() {
        try {
            if (encoder != null) {
                encoder.flush();
            }
            return new SimpleBinaryData(file, offset, diagnose());
        } finally {
            offset = -1;
        }
    }

    public void close() {
        try {
            if (encoder != null) {
                encoder.close();
            }
        } finally {
            file.delete();
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

    private static class SimpleBinaryData implements BinaryStore.BinaryData {
        private final int offset;
        private final File inputFile;
        private final String sourceDescription;

        private Decoder decoder;
        private CompositeStoppable resources;

        public SimpleBinaryData(File inputFile, int offset, String sourceDescription) {
            this.inputFile = inputFile;
            this.offset = offset;
            this.sourceDescription = sourceDescription;
        }

        public <T> T read(BinaryStore.ReadAction<T> readAction) {
            try {
                if (decoder == null) {
                    RandomAccessFile randomAccess = new RandomAccessFile(inputFile, "r");
                    randomAccess.seek(offset);
                    decoder = new KryoBackedDecoder(new RandomAccessFileInputStream(randomAccess));
                    resources = new CompositeStoppable().add(randomAccess, decoder);
                }
                return readAction.read(decoder);
            } catch (Exception e) {
                throw new RuntimeException("Problems reading data from " + sourceDescription, e);
            }
        }

        public void close() {
            try {
                if (resources != null) {
                    resources.stop();
                }
            } catch (Exception e) {
                throw new RuntimeException("Problems cleaning resources of " + sourceDescription, e);
            } finally {
                decoder = null;
                resources = null;
            }
        }

        public String toString() {
            return sourceDescription;
        }
    }
}
