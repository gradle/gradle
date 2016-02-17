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

package org.gradle.api.internal.tasks.testing.junit.result;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.internal.io.RandomAccessFileInputStream;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;

import java.io.*;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;

public class TestOutputStore {

    private final File resultsDir;
    private final Charset messageStorageCharset;

    public TestOutputStore(File resultsDir) {
        this.resultsDir = resultsDir;
        this.messageStorageCharset = Charset.forName("UTF-8");
    }

    File getOutputsFile() {
        return new File(resultsDir, "output.bin");
    }

    File getIndexFile() {
        return new File(resultsDir, getOutputsFile().getName() + ".idx");
    }

    private static class Region {
        long start;
        long stop;

        private Region() {
            start = -1;
            stop = -1;
        }

        private Region(long start, long stop) {
            this.start = start;
            this.stop = stop;
        }
    }

    private static class TestCaseRegion {
        Region stdOutRegion = new Region();
        Region stdErrRegion = new Region();
    }

    public class Writer implements Closeable {
        private final KryoBackedEncoder output;

        private final Map<Long, Map<Long, TestCaseRegion>> index = new LinkedHashMap<Long, Map<Long, TestCaseRegion>>();

        public Writer() {
            try {
                output = new KryoBackedEncoder(new FileOutputStream(getOutputsFile()));
            } catch (FileNotFoundException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void close() {
            output.close();
            writeIndex();
        }

        public void onOutput(long classId, TestOutputEvent outputEvent) {
            onOutput(classId, 0, outputEvent);
        }

        public void onOutput(long classId, long testId, TestOutputEvent outputEvent) {
            boolean stdout = outputEvent.getDestination() == TestOutputEvent.Destination.StdOut;
            mark(classId, testId, stdout);

            output.writeBoolean(stdout);
            output.writeSmallLong(classId);
            output.writeSmallLong(testId);

            byte[] bytes;
            try {
                bytes = outputEvent.getMessage().getBytes(messageStorageCharset.name());
            } catch (UnsupportedEncodingException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
            output.writeSmallInt(bytes.length);
            output.writeBytes(bytes, 0, bytes.length);
        }

        private void mark(long classId, long testId, boolean isStdout) {
            if (!index.containsKey(classId)) {
                index.put(classId, new LinkedHashMap<Long, TestCaseRegion>());
            }

            Map<Long, TestCaseRegion> testCaseRegions = index.get(classId);
            if (!testCaseRegions.containsKey(testId)) {
                TestCaseRegion region = new TestCaseRegion();
                testCaseRegions.put(testId, region);
            }

            TestCaseRegion region = testCaseRegions.get(testId);

            Region streamRegion = isStdout ? region.stdOutRegion : region.stdErrRegion;

            int total = output.getWritePosition();
            if (streamRegion.start < 0) {
                streamRegion.start = total;
            }
            streamRegion.stop = total;
        }

        private void writeIndex() {
            Output indexOutput;
            try {
                indexOutput = new Output(new FileOutputStream(getIndexFile()));
            } catch (FileNotFoundException e) {
                throw new UncheckedIOException(e);
            }


            try {
                indexOutput.writeInt(index.size(), true);

                for (Map.Entry<Long, Map<Long, TestCaseRegion>> classEntry : index.entrySet()) {
                    Long classId = classEntry.getKey();
                    Map<Long, TestCaseRegion> regions = classEntry.getValue();

                    indexOutput.writeLong(classId, true);
                    indexOutput.writeInt(regions.size(), true);

                    for (Map.Entry<Long, TestCaseRegion> testCaseEntry : regions.entrySet()) {
                        long id = testCaseEntry.getKey();
                        TestCaseRegion region = testCaseEntry.getValue();
                        indexOutput.writeLong(id, true);
                        indexOutput.writeLong(region.stdOutRegion.start);
                        indexOutput.writeLong(region.stdOutRegion.stop);
                        indexOutput.writeLong(region.stdErrRegion.start);
                        indexOutput.writeLong(region.stdErrRegion.stop);
                    }
                }
            } finally {
                indexOutput.close();
            }
        }
    }

    public Writer writer() {
        return new Writer();
    }

    private static class Index {
        final ImmutableMap<Long, Index> children;
        final Region stdOut;
        final Region stdErr;

        private Index(Region stdOut, Region stdErr) {
            this.children = ImmutableMap.of();
            this.stdOut = stdOut;
            this.stdErr = stdErr;
        }

        private Index(ImmutableMap<Long, Index> children, Region stdOut, Region stdErr) {
            this.children = children;
            this.stdOut = stdOut;
            this.stdErr = stdErr;
        }
    }

    private static class IndexBuilder {
        final Region stdOut = new Region();
        final Region stdErr = new Region();

        private final ImmutableMap.Builder<Long, Index> children = ImmutableMap.builder();

        void add(long key, Index index) {
            if (stdOut.start < 0) {
                stdOut.start = index.stdOut.start;
            }
            if (stdErr.start < 0) {
                stdErr.start = index.stdErr.start;
            }
            if (index.stdOut.stop > stdOut.stop) {
                stdOut.stop = index.stdOut.stop;
            }
            if (index.stdErr.stop > stdErr.stop) {
                stdErr.stop = index.stdErr.stop;
            }

            children.put(key, index);
        }

        Index build() {
            return new Index(children.build(), stdOut, stdErr);
        }
    }

    public class Reader implements Closeable {
        private final Index index;
        private final RandomAccessFile dataFile;

        public Reader() {
            File indexFile = getIndexFile();
            File outputsFile = getOutputsFile();

            if (outputsFile.exists()) {
                if (!indexFile.exists()) {
                    throw new IllegalStateException(String.format("Test outputs data file '%s' exists but the index file '%s' does not", outputsFile, indexFile));
                }

                Input input;
                try {
                    input = new Input(new FileInputStream(indexFile));
                } catch (FileNotFoundException e) {
                    throw new UncheckedIOException(e);
                }

                IndexBuilder rootBuilder = null;
                try {
                    int numClasses = input.readInt(true);
                    rootBuilder = new IndexBuilder();

                    for (int classCounter = 0; classCounter < numClasses; ++classCounter) {
                        long classId = input.readLong(true);
                        IndexBuilder classBuilder = new IndexBuilder();

                        int numEntries = input.readInt(true);
                        for (int entryCounter = 0; entryCounter < numEntries; ++entryCounter) {
                            long testId = input.readLong(true);
                            Region stdOut = new Region(input.readLong(), input.readLong());
                            Region stdErr = new Region(input.readLong(), input.readLong());
                            classBuilder.add(testId, new Index(stdOut, stdErr));
                        }

                        rootBuilder.add(classId, classBuilder.build());
                    }
                } finally {
                    input.close();
                }

                index = rootBuilder.build();

                try {
                    dataFile = new RandomAccessFile(getOutputsFile(), "r");
                } catch (FileNotFoundException e) {
                    throw new UncheckedIOException(e);
                }
            } else { // no outputs file
                if (indexFile.exists()) {
                    throw new IllegalStateException(String.format("Test outputs data file '%s' does not exist but the index file '%s' does", outputsFile, indexFile));
                }

                index = null;
                dataFile = null;
            }
        }

        @Override
        public void close() throws IOException {
            if (dataFile != null) {
                dataFile.close();
            }
        }

        public boolean hasOutput(long classId, TestOutputEvent.Destination destination) {
            if (dataFile == null) {
                return false;
            }

            Index classIndex = index.children.get(classId);
            if (classIndex == null) {
                return false;
            } else {
                Region region = destination == TestOutputEvent.Destination.StdOut ? classIndex.stdOut : classIndex.stdErr;
                return region.start >= 0;
            }
        }

        public void writeAllOutput(long classId, TestOutputEvent.Destination destination, java.io.Writer writer) {
            doRead(classId, 0, true, destination, writer);
        }

        public void writeNonTestOutput(long classId, TestOutputEvent.Destination destination, java.io.Writer writer) {
            doRead(classId, 0, false, destination, writer);
        }

        public void writeTestOutput(long classId, long testId, TestOutputEvent.Destination destination, java.io.Writer writer) {
            doRead(classId, testId, false, destination, writer);
        }

        private void doRead(long classId, long testId, boolean allClassOutput, TestOutputEvent.Destination destination, java.io.Writer writer) {
            if (dataFile == null) {
                return;
            }

            Index targetIndex = index.children.get(classId);
            if (targetIndex != null && testId != 0) {
                targetIndex = targetIndex.children.get(testId);
            }

            if (targetIndex == null) {
                return;
            }

            boolean stdout = destination == TestOutputEvent.Destination.StdOut;
            Region region = stdout ? targetIndex.stdOut : targetIndex.stdErr;

            if (region.start < 0) {
                return;
            }

            boolean ignoreClassLevel = !allClassOutput && testId != 0;
            boolean ignoreTestLevel = !allClassOutput && testId == 0;

            try {
                dataFile.seek(region.start);
                long maxPos = region.stop - region.start;
                KryoBackedDecoder decoder = new KryoBackedDecoder(new RandomAccessFileInputStream(dataFile));
                while (decoder.getReadPosition() <= maxPos) {
                    boolean readStdout = decoder.readBoolean();
                    long readClassId = decoder.readSmallLong();
                    long readTestId = decoder.readSmallLong();
                    int readLength = decoder.readSmallInt();

                    boolean isClassLevel = readTestId == 0;

                    if (stdout != readStdout || classId != readClassId) {
                        decoder.skipBytes(readLength);
                        continue;
                    }

                    if (ignoreClassLevel && isClassLevel) {
                        decoder.skipBytes(readLength);
                        continue;
                    }

                    if (ignoreTestLevel && !isClassLevel) {
                        decoder.skipBytes(readLength);
                        continue;
                    }

                    if (testId == 0 || testId == readTestId) {
                        byte[] stringBytes = new byte[readLength];
                        decoder.readBytes(stringBytes);
                        String message;
                        try {
                            message = new String(stringBytes, messageStorageCharset.name());
                        } catch (UnsupportedEncodingException e) {
                            // shouldn't happen
                            throw UncheckedException.throwAsUncheckedException(e);
                        }

                        writer.write(message);
                    } else {
                        decoder.skipBytes(readLength);
                    }
                }
            } catch (IOException e1) {
                throw new UncheckedIOException(e1);
            }
        }
    }

    // IMPORTANT: return must be closed when done with.
    public Reader reader() {
        return new Reader();
    }
}
