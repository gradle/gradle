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

package org.gradle.cache.internal.streams;

import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultValueStore<T> implements ValueStore<T>, Closeable {
    private final File dir;
    private final String baseName;
    private final Writer<T> writer;
    private final Reader<T> reader;
    private final AtomicInteger counter = new AtomicInteger();
    private final List<Sink<T>> sinks = new CopyOnWriteArrayList<>();
    private final BlockingQueue<Sink<T>> availableSinks = new LinkedBlockingDeque<>();
    private final ConcurrentMap<Integer, Source<T>> availableSources = new ConcurrentHashMap<>();

    public DefaultValueStore(
        File dir,
        String baseName,
        Writer<T> writer,
        Reader<T> reader
    ) {
        this.dir = dir;
        this.baseName = baseName;
        this.writer = writer;
        this.reader = reader;
        try {
            Files.createDirectories(dir.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static <T> DefaultValueStore<T> encoding(
        File dir,
        String baseName,
        Serializer<T> serializer
    ) {
        return new DefaultValueStore<>(dir,
            baseName,
            serializer::write,
            serializer::read
        );
    }

    @Override
    public BlockAddress write(T value) {
        Sink<T> sink = allocateSink();
        try {
            return sink.write(value);
        } finally {
            releaseSink(sink);
        }
    }

    @Override
    public T read(BlockAddress blockAddress) {
        try {
            Source<T> source = availableSources.remove(blockAddress.fileId);
            if (source == null) {
                source = new Source<>(file(blockAddress.fileId), reader);
            }
            try {
                return source.read(blockAddress);
            } finally {
                if (availableSources.putIfAbsent(blockAddress.fileId, source) != null) {
                    // Could not retain
                    source.close();
                }
            }
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            CompositeStoppable.stoppable().add(sinks).add(availableSources.values()).stop();
        } finally {
            sinks.clear();
            availableSinks.clear();
            availableSources.clear();
        }
    }

    private Sink<T> allocateSink() {
        Sink<T> sink = availableSinks.poll();
        if (sink != null) {
            return sink;
        }
        int id = counter.incrementAndGet();
        File file = file(id);
        sink = new Sink<>(id, writer, file);
        sinks.add(sink);
        return sink;
    }

    void releaseSink(Sink<T> sink) {
        if (!availableSinks.offer(sink)) {
            try {
                sink.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private File file(int id) {
        return new File(dir, baseName + "-" + id + ".bin");
    }

    private static class BlockInputStream extends InputStream {
        private final RandomAccessFile file;
        private long remaining;

        public BlockInputStream(RandomAccessFile file, long remaining) {
            this.file = file;
            this.remaining = remaining;
        }

        @Override
        public long skip(long count) throws IOException {
            int toSkip = (int) Math.min(count, remaining);
            if (toSkip > 0) {
                file.seek(file.getFilePointer() + toSkip);
                remaining -= toSkip;
            }
            return toSkip;
        }

        @Override
        public int read() throws IOException {
            throw new UnsupportedOperationException("Should be using buffering.");
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining == 0) {
                return -1;
            }
            int toRead = (int) Math.min(length, remaining);
            if (toRead == 0) {
                return 0;
            }
            int read = file.read(buffer, offset, toRead);
            if (read < 0) {
                throw new IllegalStateException("Unexpected file length.");
            }
            remaining -= read;
            return read;
        }
    }

    private static class Source<T> implements Closeable {
        private final RandomAccessFile file;
        private final Reader<T> reader;
        private final KryoBackedDecoder decoder;

        public Source(File file, Reader<T> reader) throws FileNotFoundException {
            this.file = new RandomAccessFile(file, "r");
            this.reader = reader;
            this.decoder = new KryoBackedDecoder(new BlockInputStream(this.file, 0));
        }

        public T read(BlockAddress blockAddress) throws Exception {
            file.seek(blockAddress.pos);
            decoder.restart(new BlockInputStream(file, blockAddress.length));
            return reader.read(decoder);
        }

        @Override
        public void close() throws IOException {
            file.close();
        }
    }

    private static class Sink<T> implements Closeable {
        final int id;
        final Writer<T> writer;
        final long startOffset;
        final OutputStream outputStream;
        final KryoBackedEncoder encoder;

        public Sink(int id, Writer<T> writer, File file) {
            this.id = id;
            this.writer = writer;
            this.startOffset = file.length();
            try {
                outputStream = new FileOutputStream(file, true);
            } catch (FileNotFoundException e) {
                throw new UncheckedIOException(e);
            }
            this.encoder = new KryoBackedEncoder(outputStream);
        }

        BlockAddress write(T value) {
            long startPos = encoder.getWritePosition();
            try {
                writer.write(encoder, value);
            } catch (Exception e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
            // Flush in case a read of this block needs to happen. Ideally, should either defer flush until the read or read directly from the write buffer
            encoder.flush();
            long length = encoder.getWritePosition() - startPos;
            return new BlockAddress(id, startPos + startOffset, length);
        }

        @Override
        public void close() throws IOException {
            encoder.flush();
            outputStream.close();
        }
    }
}
