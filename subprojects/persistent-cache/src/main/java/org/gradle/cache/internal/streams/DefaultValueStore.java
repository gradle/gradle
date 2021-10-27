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
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.FlushableEncoder;
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
import java.util.function.Function;

public class DefaultValueStore<T> implements ValueStore<T>, Closeable {
    private final File dir;
    private final String baseName;
    private final Function<OutputStream, Writer<T>> writerFactory;
    private final Function<InputStream, Reader<T>> readerFactory;
    private final AtomicInteger counter = new AtomicInteger();
    private final List<Sink<T>> sinks = new CopyOnWriteArrayList<>();
    private final BlockingQueue<Sink<T>> availableSinks = new LinkedBlockingDeque<>();
    private final ConcurrentMap<Integer, Source<T>> availableSources = new ConcurrentHashMap<>();

    public DefaultValueStore(
        File dir,
        String baseName,
        // stream is not buffered
        Function<OutputStream, Writer<T>> writerFactory,
        // stream is not buffered
        Function<InputStream, Reader<T>> readerFactory
    ) {
        this.dir = dir;
        this.baseName = baseName;
        this.writerFactory = writerFactory;
        this.readerFactory = readerFactory;
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
            outputStream -> {
                FlushableEncoder encoder = new KryoBackedEncoder(outputStream);
                return value -> {
                    serializer.write(encoder, value);
                    encoder.flush();
                };
            },
            inputStream -> {
                Decoder decoder = new KryoBackedDecoder(inputStream);
                return () -> serializer.read(decoder);
            });
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
                source = new Source<>(blockAddress.fileId, file(blockAddress.fileId));
            }
            try {
                InputStream stream = source.stream(blockAddress);
                return readerFactory.apply(stream).read();
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
        long currentPos = file.length();
        ByteCountingOutputStream outputStream;
        try {
            outputStream = new ByteCountingOutputStream(new FileOutputStream(file, true), currentPos);
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
        }
        Writer<T> writer = writerFactory.apply(outputStream);
        sink = new Sink<>(id, writer, outputStream);
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

    private static class ByteCountingOutputStream extends OutputStream {
        private final OutputStream delegate;
        private long count;

        public ByteCountingOutputStream(OutputStream delegate, long currentOffset) {
            this.delegate = delegate;
            this.count = currentOffset;
        }

        @Override
        public void write(int b) throws IOException {
            throw new UnsupportedOperationException("Should be using buffering.");
        }

        @Override
        public void write(byte[] buffer) throws IOException {
            delegate.write(buffer);
            count += buffer.length;
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            delegate.write(buffer, offset, length);
            count += length;
        }

        public void closeDelegate() throws IOException {
            delegate.close();
        }
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
            file.seek(file.getFilePointer() + count);
            return count;
        }

        @Override
        public int read() throws IOException {
            throw new UnsupportedOperationException("Should be using buffering.");
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = (int) Math.min(length, remaining);
            if (count == 0) {
                return -1;
            }
            int nread = file.read(buffer, offset, count);
            remaining -= nread;
            return nread;
        }
    }

    private static class Source<T> implements Closeable {
        private final int id;
        private final RandomAccessFile file;

        public Source(int id, File file) throws FileNotFoundException {
            this.id = id;
            this.file = new RandomAccessFile(file, "r");
        }

        public InputStream stream(BlockAddress blockAddress) throws IOException {
            file.seek(blockAddress.pos);
            return new BlockInputStream(file, blockAddress.length);
        }

        @Override
        public void close() throws IOException {
            file.close();
        }
    }

    private static class Sink<T> implements Closeable {
        final int id;
        final Writer<T> writer;
        final ByteCountingOutputStream outputStream;

        public Sink(int id, Writer<T> writer, ByteCountingOutputStream outputStream) {
            this.id = id;
            this.writer = writer;
            this.outputStream = outputStream;
        }

        BlockAddress write(T value) {
            long pos = outputStream.count;
            try {
                writer.write(value);
            } catch (Exception e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
            long length = outputStream.count - pos;
            return new BlockAddress(id, pos, length);
        }

        @Override
        public void close() throws IOException {
            outputStream.closeDelegate();
        }
    }
}
