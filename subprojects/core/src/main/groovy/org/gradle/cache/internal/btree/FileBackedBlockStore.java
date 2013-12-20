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
package org.gradle.cache.internal.btree;

import org.gradle.api.UncheckedIOException;
import org.gradle.internal.io.RandomAccessFileInputStream;
import org.gradle.internal.io.RandomAccessFileOutputStream;

import java.io.*;
import java.util.zip.CRC32;

public class FileBackedBlockStore implements BlockStore {
    private RandomAccessFile file;
    private final File cacheFile;
    private long nextBlock;
    private Factory factory;

    public FileBackedBlockStore(File cacheFile) {
        this.cacheFile = cacheFile;
    }

    @Override
    public String toString() {
        return String.format("cache '%s'", cacheFile);
    }

    public void open(Runnable runnable, Factory factory) {
        this.factory = factory;
        try {
            cacheFile.getParentFile().mkdirs();
            file = new RandomAccessFile(cacheFile, "rw");
            nextBlock = file.length();
            if (file.length() == 0) {
                runnable.run();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void close() {
        try {
            file.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void clear() {
        try {
            file.setLength(0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        nextBlock = 0;
    }

    public void attach(BlockPayload block) {
        if (block.getBlock() == null) {
            block.setBlock(new BlockImpl(block));
        }
    }

    public void remove(BlockPayload block) {
        BlockImpl blockImpl = (BlockImpl) block.getBlock();
        blockImpl.detach();
    }

    public void flush() {
    }

    public <T extends BlockPayload> T readFirst(Class<T> payloadType) {
        return read(new BlockPointer(0), payloadType);
    }

    public <T extends BlockPayload> T read(BlockPointer pos, Class<T> payloadType) {
        assert !pos.isNull();
        try {
            T payload = payloadType.cast(factory.create(payloadType));
            BlockImpl block = new BlockImpl(payload, pos);
            block.read();
            return payload;
        } catch (CorruptedCacheException e) {
            throw e;
        } catch (Exception e) {
            throw new UncheckedIOException(e);
        }
    }

    public void write(BlockPayload block) {
        BlockImpl blockImpl = (BlockImpl) block.getBlock();
        try {
            blockImpl.write();
        } catch (CorruptedCacheException e) {
            throw e;
        } catch (Exception e) {
            throw new UncheckedIOException(e);
        }
    }

    private long alloc(long length) {
        long pos = nextBlock;
        nextBlock += length;
        return pos;
    }

    private final class BlockImpl extends Block {
        private static final int HEADER_SIZE = 2 + INT_SIZE;
        private static final int TAIL_SIZE = LONG_SIZE;
        static final int BLOCK_MARKER = 0xCC;

        private BlockPointer pos;
        private int payloadSize;

        private BlockImpl(BlockPayload payload, BlockPointer pos) {
            this(payload);
            setPos(pos);
        }

        public BlockImpl(BlockPayload payload) {
            super(payload);
            pos = null;
            payloadSize = -1;
        }

        @Override
        public boolean hasPos() {
            return pos != null;
        }

        @Override
        public BlockPointer getPos() {
            if (pos == null) {
                pos = new BlockPointer(alloc(getSize()));
            }
            return pos;
        }

        @Override
        public void setPos(BlockPointer pos) {
            assert this.pos == null && !pos.isNull();
            this.pos = pos;
        }

        public int getSize() {
            if (payloadSize < 0) {
                payloadSize = getPayload().getSize();
            }
            return payloadSize + HEADER_SIZE + TAIL_SIZE;
        }

        @Override
        public void setSize(int size) {
            int newPayloadSize = size - HEADER_SIZE - TAIL_SIZE;
            assert newPayloadSize >= payloadSize;
            payloadSize = newPayloadSize;
        }

        public void write() throws Exception {
            long pos = getPos().getPos();
            file.seek(pos);

            Crc32OutputStream checkSumOutputStream = new Crc32OutputStream(new BufferedOutputStream(
                    new RandomAccessFileOutputStream(file)));
            DataOutputStream outputStream = new DataOutputStream(checkSumOutputStream);

            BlockPayload payload = getPayload();

            // Write header
            outputStream.writeByte(BLOCK_MARKER);
            outputStream.writeByte(payload.getType());
            outputStream.writeInt(payloadSize);
            long finalSize = pos + HEADER_SIZE + TAIL_SIZE + payloadSize;

            // Write body
            payload.write(outputStream);

            // Write checksum
            outputStream.writeLong(checkSumOutputStream.checksum.getValue());
            outputStream.close();

            // Pad
            if (file.length() < finalSize) {
                file.setLength(finalSize);
            }
        }

        public void read() throws Exception {
            long pos = getPos().getPos();
            assert pos >= 0;
            if (pos + HEADER_SIZE >= file.length()) {
                throw blockCorruptedException();
            }
            file.seek(pos);

            Crc32InputStream checkSumInputStream = new Crc32InputStream(new BufferedInputStream(
                    new RandomAccessFileInputStream(file)));
            DataInputStream inputStream = new DataInputStream(checkSumInputStream);

            BlockPayload payload = getPayload();

            // Read header
            byte type = inputStream.readByte();
            if (type != (byte) BLOCK_MARKER) {
                throw blockCorruptedException();
            }
            type = inputStream.readByte();
            if (type != (byte) payload.getType()) {
                throw blockCorruptedException();
            }

            // Read body
            payloadSize = inputStream.readInt();
            if (pos + HEADER_SIZE + TAIL_SIZE + payloadSize > file.length()) {
                throw blockCorruptedException();
            }
            payload.read(inputStream);

            // Read and verify checksum
            long actualChecksum = checkSumInputStream.checksum.getValue();
            long checksum = inputStream.readLong();
            if (actualChecksum != checksum) {
                throw blockCorruptedException();
            }
            inputStream.close();
        }

        public RuntimeException blockCorruptedException() {
            return new CorruptedCacheException(String.format("Corrupted %s found in %s.", this,
                    FileBackedBlockStore.this));
        }
    }

    private static class Crc32InputStream extends FilterInputStream {
        private final CRC32 checksum;

        private Crc32InputStream(InputStream inputStream) {
            super(inputStream);
            checksum = new CRC32();
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b >= 0) {
                checksum.update(b);
            }
            return b;
        }

        @Override
        public int read(byte[] bytes) throws IOException {
            int count = in.read(bytes);
            if (count > 0) {
                checksum.update(bytes, 0, count);
            }
            return count;
        }

        @Override
        public int read(byte[] bytes, int offset, int max) throws IOException {
            int count = in.read(bytes, offset, max);
            if (count > 0) {
                checksum.update(bytes, offset, count);
            }
            return count;
        }
    }

    private static class Crc32OutputStream extends FilterOutputStream {
        private final CRC32 checksum;

        private Crc32OutputStream(OutputStream outputStream) {
            super(outputStream);
            this.checksum = new CRC32();
        }

        @Override
        public void write(int b) throws IOException {
            checksum.update(b);
            out.write(b);
        }

        @Override
        public void write(byte[] bytes) throws IOException {
            checksum.update(bytes);
            out.write(bytes);
        }

        @Override
        public void write(byte[] bytes, int offset, int count) throws IOException {
            checksum.update(bytes, offset, count);
            out.write(bytes, offset, count);
        }
    }
}
