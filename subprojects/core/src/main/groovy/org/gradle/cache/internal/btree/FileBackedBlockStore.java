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

import com.google.common.io.CountingInputStream;
import com.google.common.io.CountingOutputStream;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.io.RandomAccessFileInputStream;
import org.gradle.internal.io.RandomAccessFileOutputStream;

import java.io.*;

public class FileBackedBlockStore implements BlockStore {
    private RandomAccessFile file;
    private final File cacheFile;
    private long nextBlock;
    private Factory factory;
    private long currentFileSize;

    public FileBackedBlockStore(File cacheFile) {
        this.cacheFile = cacheFile;
    }

    @Override
    public String toString() {
        return "cache '" + cacheFile + "'";
    }

    public void open(Runnable runnable, Factory factory) {
        this.factory = factory;
        try {
            cacheFile.getParentFile().mkdirs();
            file = new RandomAccessFile(cacheFile, "rw");
            currentFileSize = file.length();
            nextBlock = currentFileSize;
            if (currentFileSize == 0) {
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
            currentFileSize = 0;
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

            CountingOutputStream countingOutputStream = new CountingOutputStream(new BufferedOutputStream(
                new RandomAccessFileOutputStream(file)));
            DataOutputStream outputStream = new DataOutputStream(countingOutputStream);

            BlockPayload payload = getPayload();

            // Write header
            outputStream.writeByte(BLOCK_MARKER);
            outputStream.writeByte(payload.getType());
            outputStream.writeInt(payloadSize);
            long finalSize = pos + HEADER_SIZE + TAIL_SIZE + payloadSize;

            // Write body
            payload.write(outputStream);

            // Write count
            outputStream.writeLong(countingOutputStream.getCount());
            outputStream.close();

            // Pad
            if (currentFileSize < finalSize) {
                file.setLength(finalSize);
                currentFileSize = finalSize;
            }
        }

        public void read() throws Exception {
            long pos = getPos().getPos();
            assert pos >= 0;
            if (pos + HEADER_SIZE >= currentFileSize) {
                throw blockCorruptedException();
            }
            file.seek(pos);

            CountingInputStream countingInputStream = new CountingInputStream(new BufferedInputStream(
                    new RandomAccessFileInputStream(file)));
            DataInputStream inputStream = new DataInputStream(countingInputStream);

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
            if (pos + HEADER_SIZE + TAIL_SIZE + payloadSize > currentFileSize) {
                throw blockCorruptedException();
            }
            payload.read(inputStream);

            // Read and verify count
            long actualCount = countingInputStream.getCount();
            long count = inputStream.readLong();
            if (actualCount != count) {
                throw blockCorruptedException();
            }
            inputStream.close();
        }

        public RuntimeException blockCorruptedException() {
            return new CorruptedCacheException(String.format("Corrupted %s found in %s.", this,
                    FileBackedBlockStore.this));
        }
    }
}
