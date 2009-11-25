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
package org.gradle.cache;

import org.apache.commons.collections.map.LRUMap;
import org.gradle.api.UncheckedIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.zip.CRC32;

// todo - reuse existing data block in put() if there is space
// todo - reuse empty blocks
// todo - stream serialised value to file
// todo - handle hash collisions
// todo - don't store null links to child blocks in leaf index blocks

// todo - align block boundaries
// todo - concurrency control
// todo - remove the check-sum from each block
// todo - merge small values into a single data block
// todo - discard on corrupt file
public class BTreePersistentIndexedCache<K, V> implements PersistentIndexedCache<K, V> {
    private static final Logger LOGGER = LoggerFactory.getLogger(BTreePersistentIndexedCache.class);
    private static final int LONG_SIZE = 8;
    private static final int INT_SIZE = 4;
    private static final int BLOCK_MARKER = 0xCC;
    private final File cacheFile;
    private final PersistentCache backingCache;
    private final Serializer<V> serializer;
    private final int maxIndexChildNodes;
    private final int minIndexChildNodes;
    private final Set<Block> dirty = new LinkedHashSet<Block>();
    private final Map<Long, IndexBlock> indexBlockCache = new LRUMap(100);
    private RandomAccessFile file;
    private HeaderBlock header;
    private long nextBlock;

    public BTreePersistentIndexedCache(PersistentCache backingCache, Serializer<V> serializer) {
        this(backingCache, serializer, 512);
    }

    public BTreePersistentIndexedCache(PersistentCache backingCache, Serializer<V> serializer, int maxIndexChildNodes) {
        this.backingCache = backingCache;
        this.serializer = serializer;
        this.maxIndexChildNodes = maxIndexChildNodes;
        this.minIndexChildNodes = maxIndexChildNodes / 2;
        cacheFile = new File(backingCache.getBaseDir(), "cache.bin");
        try {
            open();
        } catch (Exception e) {
            throw new UncheckedIOException(String.format("Could not open %s.", this), e);
        }
    }

    @Override
    public String toString() {
        return String.format("cache '%s'", cacheFile);
    }

    private void open() throws Exception {
        file = new RandomAccessFile(cacheFile, "rw");
        try {
            doOpen();
        } catch (CorruptedCacheException e) {
            rebuild();
        }
    }

    private void doOpen() throws Exception {
        header = new HeaderBlock();
        if (file.length() == 0) {
            nextBlock = header.getActualSize();
            newRootBlock();
            flush();
            backingCache.update();
        } else {
            nextBlock = file.length();
        }
        header.read();
    }

    public V get(K key) {
        try {
            try {
                DataBlock block = header.getRoot().get(key);
                if (block != null) {
                    return block.value;
                }
                return null;
            } catch (CorruptedCacheException e) {
                rebuild();
                return null;
            }
        } catch (Exception e) {
            throw new UncheckedIOException(String.format("Could not read entry '%s' from %s.", key, this), e);
        }
    }

    public void put(K key, V value) {
        try {
            String keyString = key.toString();
            Lookup lookup = header.getRoot().find(keyString);
            DataBlock block = new DataBlock(keyString, value);
            lookup.indexBlock.put(key, block.getPos());
            flush();
        } catch (Exception e) {
            throw new UncheckedIOException(String.format("Could not add entry '%s' to %s.", key, this), e);
        }
    }

    private void flush() throws Exception {
        Iterator<Block> iterator = dirty.iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            iterator.remove();
            block.write();
        }
    }

    private IndexBlock newRootBlock() throws IOException {
        IndexBlock block = new IndexBlock();
        header.setRoot(block);
        return block;
    }

    private IndexBlock load(long pos, IndexBlock parent, int index) throws Exception {
        IndexBlock block = indexBlockCache.get(pos);
        if (block == null) {
            block = new IndexBlock(pos);
            block.read();
            indexBlockCache.put(pos, block);
        }
        block.parent = parent;
        block.parentEntryIndex = index;
        return block;
    }

    public void remove(K key) {
        try {
            Lookup lookup = header.getRoot().find(key.toString());
            if (lookup.entry == null) {
                return;
            }
            lookup.indexBlock.remove(lookup.entry);
            flush();
        } catch (Exception e) {
            throw new UncheckedIOException(String.format("Could not remove entry '%s' from %s.", key, this), e);
        }
    }

    public void reset() {
        try {
            file.close();
            open();
        } catch (Exception e) {
            throw new UncheckedIOException(e);
        }
    }

    private void rebuild() throws Exception {
        LOGGER.warn(String.format("%s is corrupt. Discarding.", this));
        file.setLength(0);
        doOpen();
    }

    public void verify() {
        try {
            doVerify();
        } catch (Exception e) {
            throw new UncheckedIOException(String.format("Some problems were found when checking the integrity of %s.",
                    this), e);
        }
    }

    private void doVerify() throws Exception {
        List<Block> blocks = new ArrayList<Block>();

        HeaderBlock header = new HeaderBlock();
        header.read();
        blocks.add(header);
        verifyTree(header.getRoot(), "", blocks, Long.MAX_VALUE);

        Collections.sort(blocks, new Comparator<Block>() {
            public int compare(Block block, Block block1) {
                if (block.getPos() > block1.getPos()) {
                    return 1;
                }
                if (block.getPos() < block1.getPos()) {
                    return -1;
                }
                return 0;
            }
        });

        for (int i = 0; i < blocks.size() - 1; i++) {
            Block b1 = blocks.get(i);
            Block b2 = blocks.get(i + 1);
            if (b1.getPos() + b1.getActualSize() > b2.getPos()) {
                throw new IOException(String.format("%s overlaps with %s", b1, b2));
            }
        }
    }

    private void verifyTree(IndexBlock current, String prefix, Collection<Block> blocks, long maxValue)
            throws Exception {
        current.read();
        blocks.add(current);

        if (!prefix.equals("") && current.entries.size() < maxIndexChildNodes / 2) {
            throw new IOException(String.format("Too few entries found in %s", current));
        }
        if (current.entries.size() > maxIndexChildNodes) {
            throw new IOException(String.format("Too many entries found in %s", current));
        }

        boolean isLeaf = current.entries.size() == 0 || current.entries.get(0).childPos == 0;
        if (isLeaf ^ current.tailPos == 0) {
            throw new IOException(String.format("Mismatched leaf/tail-node in %s", current));
        }

        long min = Long.MIN_VALUE;
        for (IndexEntry entry : current.entries) {
            if (isLeaf ^ entry.childPos == 0) {
                throw new IOException(String.format("Mismatched leaf/non-leaf entry in %s", current));
            }
            if (entry.hashCode >= maxValue || entry.hashCode <= min) {
                throw new IOException(String.format("Out-of-order key in %s", current));
            }
            min = entry.hashCode;
            if (entry.childPos != 0) {
                IndexBlock child = new IndexBlock(entry.childPos);
                verifyTree(child, "   " + prefix, blocks, entry.hashCode);
            }
            DataBlock block = new DataBlock(entry.pos);
            block.read();
            blocks.add(block);
        }
        if (current.tailPos != 0) {
            IndexBlock tail = new IndexBlock(current.tailPos);
            verifyTree(tail, "   " + prefix, blocks, maxValue);
        }
    }

    private abstract class Block {
        private static final int HEADER_SIZE = 2 + INT_SIZE;
        private static final int TAIL_SIZE = LONG_SIZE;
        long pos = -1;

        public long getPos() {
            if (pos < 0) {
                pos = nextBlock;
                nextBlock += getActualSize();
            }
            return pos;
        }

        @Override
        public String toString() {
            return String.format("%s %d", getClass().getSimpleName(), pos);
        }

        protected int getActualSize() {
            return getSize() + HEADER_SIZE + TAIL_SIZE;
        }

        protected abstract int getSize();

        protected abstract int getType();

        void read() throws Exception {
            assert pos >= 0;
            if (pos + HEADER_SIZE >= file.length()) {
                throw corruptedException();
            }
            file.seek(pos);

            Crc32InputStream checksumInputStream = new Crc32InputStream(new BufferedInputStream(
                    new RandomAccessFileInputStream(file)));
            DataInputStream instr = new DataInputStream(checksumInputStream);

            // Read header
            byte type = instr.readByte();
            if (type != (byte) BLOCK_MARKER) {
                throw corruptedException();
            }
            type = instr.readByte();
            if (type != (byte) getType()) {
                throw corruptedException();
            }

            // Read body
            int size = instr.readInt();
            if (pos + HEADER_SIZE + TAIL_SIZE + size > file.length()) {
                throw corruptedException();
            }
            doRead(instr);

            // Read and verify checksum
            long actualChecksum = checksumInputStream.checksum.getValue();
            long checksum = instr.readLong();
            if (actualChecksum != checksum) {
                throw corruptedException();
            }
            instr.close();
        }

        private RuntimeException corruptedException() {
            return new CorruptedCacheException(String.format("Corrupted %s found in %s.", this, BTreePersistentIndexedCache.this));
        }

        protected abstract void doRead(DataInputStream inputStream) throws Exception;

        void write() throws Exception {
            long pos = getPos();
            file.seek(pos);

            Crc32OutputStream checksumOutputStream = new Crc32OutputStream(new BufferedOutputStream(
                    new RandomAccessFileOutputStream(file)));
            DataOutputStream outstr = new DataOutputStream(checksumOutputStream);

            // Write header
            outstr.writeByte(BLOCK_MARKER);
            outstr.writeByte(getType());
            int size = getSize();
            outstr.writeInt(size);
            long finalSize = pos + HEADER_SIZE + TAIL_SIZE + size;

            // Write body
            doWrite(outstr);

            // Write checksum
            outstr.writeLong(checksumOutputStream.checksum.getValue());
            outstr.close();

            // Pad
            if (file.length() < finalSize) {
                file.setLength(finalSize);
            }
        }

        protected abstract void doWrite(DataOutputStream outputStream) throws Exception;
    }

    private class HeaderBlock extends Block {
        private long rootPos;

        private HeaderBlock() {
            pos = 0;
            rootPos = -1;
        }

        @Override
        protected int getType() {
            return 0x55;
        }

        @Override
        protected int getSize() {
            return LONG_SIZE;
        }

        @Override
        protected void doRead(DataInputStream instr) throws Exception {
            rootPos = instr.readLong();
        }

        @Override
        protected void doWrite(DataOutputStream outstr) throws Exception {
            outstr.writeLong(rootPos);
        }

        public void setRoot(IndexBlock root) {
            this.rootPos = root.getPos();
            root.parent = null;
            dirty.add(this);
        }

        public IndexBlock getRoot() throws Exception {
            return load(rootPos, null, 0);
        }
    }

    private class IndexBlock extends Block {
        private final List<IndexEntry> entries = new ArrayList<IndexEntry>();
        private IndexBlock parent;
        private int parentEntryIndex;
        private long tailPos;

        public IndexBlock() {
            dirty.add(this);
        }

        public IndexBlock(long pos) {
            this.pos = pos;
        }

        @Override
        protected int getType() {
            return 0x77;
        }

        @Override
        protected int getSize() {
            return INT_SIZE + LONG_SIZE + (3 * LONG_SIZE) * maxIndexChildNodes;
        }

        public void doRead(DataInputStream instr) throws IOException {
            int count = instr.readInt();
            entries.clear();
            for (int i = 0; i < count; i++) {
                IndexEntry entry = new IndexEntry();
                entry.hashCode = instr.readLong();
                entry.pos = instr.readLong();
                entry.childPos = instr.readLong();
                entries.add(entry);
            }
            tailPos = instr.readLong();
        }

        public void doWrite(DataOutputStream outstr) throws IOException {
            outstr.writeInt(entries.size());
            for (IndexEntry entry : entries) {
                outstr.writeLong(entry.hashCode);
                outstr.writeLong(entry.pos);
                outstr.writeLong(entry.childPos);
            }
            outstr.writeLong(tailPos);
        }

        public void put(K key, long pos) throws IOException {
            long hashCode = key.toString().hashCode();
            int index = Collections.binarySearch(entries, new IndexEntry(hashCode));
            IndexEntry entry;
            if (index >= 0) {
                entry = entries.get(index);
            } else {
                assert tailPos == 0;
                entry = new IndexEntry();
                entry.hashCode = hashCode;
                index = -index - 1;
                entries.add(index, entry);
            }

            entry.pos = pos;
            dirty.add(this);

            maybeSplit();
        }

        private void maybeSplit() throws IOException {
            if (entries.size() > maxIndexChildNodes) {
                int splitPos = entries.size() / 2;
                IndexEntry splitEntry = entries.remove(splitPos);
                if (parent == null) {
                    parent = newRootBlock();
                }
                IndexBlock sibling = new IndexBlock();
                List<IndexEntry> siblingEntries = entries.subList(splitPos, entries.size());
                sibling.entries.addAll(siblingEntries);
                siblingEntries.clear();
                sibling.tailPos = tailPos;
                tailPos = splitEntry.childPos;
                splitEntry.childPos = 0;
                parent.add(this, splitEntry, sibling);
            }
        }

        private void add(IndexBlock left, IndexEntry entry, IndexBlock right) throws IOException {
            int index = left.parentEntryIndex;
            if (index < entries.size()) {
                IndexEntry parentEntry = entries.get(index);
                assert parentEntry.childPos == left.getPos();
                parentEntry.childPos = right.getPos();
            } else {
                assert index == entries.size() && (tailPos == 0 || tailPos == left.getPos());
                tailPos = right.getPos();
            }
            entries.add(index, entry);
            entry.childPos = left.getPos();
            dirty.add(this);

            maybeSplit();
        }

        public DataBlock get(K key) throws Exception {
            Lookup lookup = find(key.toString());
            if (lookup.entry == null) {
                return null;
            }

            DataBlock block = new DataBlock(lookup.entry.pos);
            block.read();
            return block;
        }

        public Lookup find(String keyString) throws Exception {
            return find((long) keyString.hashCode());
        }

        private Lookup find(long hashCode) throws Exception {
            int index = Collections.binarySearch(entries, new IndexEntry(hashCode));
            if (index >= 0) {
                return new Lookup(this, entries.get(index));
            }

            index = -index - 1;
            long childBlockPos;
            if (index == entries.size()) {
                childBlockPos = tailPos;
            } else {
                childBlockPos = entries.get(index).childPos;
            }
            if (childBlockPos == 0) {
                return new Lookup(this, null);
            }

            IndexBlock childBlock = load(childBlockPos, this, index);
            return childBlock.find(hashCode);
        }

        public void remove(IndexEntry entry) throws Exception {
            int index = entries.indexOf(entry);
            assert index >= 0;
            entries.remove(index);
            dirty.add(this);

            if (entry.childPos == 0) {
                maybeMerge();
            } else {
                // Not a leaf node. Move up an entry from a leaf node, then possibly merge the leaf node
                IndexBlock leafBlock = load(entry.childPos, this, index);
                leafBlock = leafBlock.findHighestLeaf();
                IndexEntry highestEntry = leafBlock.entries.remove(leafBlock.entries.size() - 1);
                highestEntry.childPos = entry.childPos;
                entries.add(index, highestEntry);
                dirty.add(leafBlock);
                leafBlock.maybeMerge();
            }
        }

        private void maybeMerge() throws Exception {
            if (parent == null) {
                if (entries.size() == 0 && tailPos != 0) {
                    // Empty root block, discard it
                    header.setRoot(load(tailPos, null, 0));
                }
                return;
            }
            if (entries.size() < minIndexChildNodes) {
                IndexBlock left = parent.getPrevious(this);
                if (left != null) {
                    if (left.entries.size() > minIndexChildNodes) {
                        // Redistribute entries with lhs block
                        left.mergeFrom(this);
                        left.maybeSplit();
                    } else if (left.entries.size() + entries.size() <= maxIndexChildNodes) {
                        // Merge with the lhs block
                        left.mergeFrom(this);
                        parent.maybeMerge();
                        return;
                    }
                }
                IndexBlock right = parent.getNext(this);
                if (right != null) {
                    if (right.entries.size() > minIndexChildNodes) {
                        // Redistribute entries with rhs block
                        mergeFrom(right);
                        maybeSplit();
                        return;
                    } else if (right.entries.size() + entries.size() <= maxIndexChildNodes) {
                        // Merge with the rhs block
                        mergeFrom(right);
                        parent.maybeMerge();
                        return;
                    }
                }

                throw new UnsupportedOperationException("implement me");
            }
        }

        private void mergeFrom(IndexBlock right) {
            IndexEntry newChildEntry = parent.entries.remove(parentEntryIndex);
            if (right.pos == parent.tailPos) {
                parent.tailPos = pos;
            } else {
                IndexEntry newParentEntry = parent.entries.get(parentEntryIndex);
                assert newParentEntry.childPos == right.pos;
                newParentEntry.childPos = pos;
            }
            entries.add(newChildEntry);
            entries.addAll(right.entries);
            newChildEntry.childPos = tailPos;
            tailPos = right.tailPos;
            dirty.add(parent);
            dirty.add(this);
        }

        private IndexBlock getNext(IndexBlock indexBlock) throws Exception {
            int index = indexBlock.parentEntryIndex + 1;
            if (index > entries.size()) {
                return null;
            }
            if (index == entries.size()) {
                return load(tailPos, this, index);
            }
            return load(entries.get(index).childPos, this, index);
        }

        private IndexBlock getPrevious(IndexBlock indexBlock) throws Exception {
            int index = indexBlock.parentEntryIndex - 1;
            if (index < 0) {
                return null;
            }
            return load(entries.get(index).childPos, this, index);
        }

        private IndexBlock findHighestLeaf() throws Exception {
            if (tailPos == 0) {
                return this;
            }
            return load(tailPos, this, entries.size()).findHighestLeaf();
        }
    }

    private static class IndexEntry implements Comparable<IndexEntry> {
        long hashCode;
        long pos;
        long childPos;

        private IndexEntry() {
        }

        private IndexEntry(long hashCode) {
            this.hashCode = hashCode;
        }

        public int compareTo(IndexEntry indexEntry) {
            if (hashCode > indexEntry.hashCode) {
                return 1;
            }
            if (hashCode < indexEntry.hashCode) {
                return -1;
            }
            return 0;
        }
    }

    private class Lookup {
        final IndexBlock indexBlock;
        final IndexEntry entry;

        private Lookup(IndexBlock indexBlock, IndexEntry entry) {
            this.indexBlock = indexBlock;
            this.entry = entry;
        }
    }

    private class DataBlock extends Block {
        private byte[] serialisedValue;
        private V value;

        public DataBlock(long pos) {
            this.pos = pos;
        }

        public DataBlock(String key, V value) throws Exception {
            this.value = value;
            ByteArrayOutputStream outStr = new ByteArrayOutputStream();
            serializer.write(outStr, value);
            this.serialisedValue = outStr.toByteArray();
            dirty.add(this);
        }

        @Override
        protected int getType() {
            return 0x33;
        }

        @Override
        protected int getSize() {
            return INT_SIZE + serialisedValue.length;
        }

        public void doRead(DataInputStream instr) throws Exception {
            int length = instr.readInt();
            serialisedValue = new byte[length];
            instr.readFully(serialisedValue);
            value = serializer.read(new ByteArrayInputStream(serialisedValue));
        }

        public void doWrite(DataOutputStream outstr) throws Exception {
            outstr.writeInt(serialisedValue.length);
            outstr.write(serialisedValue);
        }
    }

    private static class RandomAccessFileInputStream extends InputStream {
        private final RandomAccessFile file;

        private RandomAccessFileInputStream(RandomAccessFile file) {
            this.file = file;
        }

        @Override
        public int read(byte[] bytes) throws IOException {
            return file.read(bytes);
        }

        @Override
        public int read() throws IOException {
            return file.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            return file.read(bytes, offset, length);
        }
    }

    private static class RandomAccessFileOutputStream extends OutputStream {
        private final RandomAccessFile file;

        private RandomAccessFileOutputStream(RandomAccessFile file) {
            this.file = file;
        }

        @Override
        public void write(int i) throws IOException {
            file.write(i);
        }

        @Override
        public void write(byte[] bytes) throws IOException {
            file.write(bytes);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            file.write(bytes, offset, length);
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

    private static class CorruptedCacheException extends RuntimeException {
        private CorruptedCacheException(String message) {
            super(message);
        }
    }
}
