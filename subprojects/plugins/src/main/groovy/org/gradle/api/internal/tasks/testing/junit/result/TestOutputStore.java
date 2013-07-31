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
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.tasks.testing.TestOutputEvent;

import java.io.*;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.gradle.api.internal.tasks.testing.junit.result.KryoSerializationUtil.*;

public class TestOutputStore {

    private final File resultsDir;
    private final Charset messageStorageCharset;

    public TestOutputStore(File resultsDir) {
        this.resultsDir = resultsDir;
        this.messageStorageCharset = Charset.forName("UTF-8");
    }

    private File getOutputsFile() {
        return new File(resultsDir, "output.bin");
    }

    private File getIndexFile() {
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

    public class Writer {
        private final Output output;

        private final Map<String, Map<Long, TestCaseRegion>> index = new LinkedHashMap<String, Map<Long, TestCaseRegion>>();

        public Writer() {
            try {
                output = new Output(new FileOutputStream(getOutputsFile()));
            } catch (FileNotFoundException e) {
                throw new UncheckedIOException(e);
            }
        }

        public void finishOutputs() {
            output.close();
            writeIndex();
        }

        public void onOutput(long internalId, TestDescriptorInternal testDescriptor, TestOutputEvent.Destination destination, final String message) {
            String className = testDescriptor.getClassName();
            String name = testDescriptor.getName();

            // This is a rather weak contract, but given the current inputs is the best we can do
            boolean isClassLevelOutput = name.equals(className);

            boolean stdout = destination == TestOutputEvent.Destination.StdOut;

            mark(className, internalId, stdout);

            output.writeBoolean(stdout);
            output.writeBoolean(isClassLevelOutput);
            writeString(className, messageStorageCharset, output);
            output.writeLong(internalId, true);
            writeString(message, messageStorageCharset, output);
        }

        private void mark(String className, long testId, boolean isStdout) {
            if (!index.containsKey(className)) {
                index.put(className, new LinkedHashMap<Long, TestCaseRegion>());
            }

            Map<Long, TestCaseRegion> testCaseRegions = index.get(className);
            if (!testCaseRegions.containsKey(testId)) {
                TestCaseRegion region = new TestCaseRegion();
                testCaseRegions.put(testId, region);
            }

            TestCaseRegion region = testCaseRegions.get(testId);

            Region streamRegion = isStdout ? region.stdOutRegion : region.stdErrRegion;

            int total = output.total();
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

            indexOutput.writeInt(index.size(), true);

            for (Map.Entry<String, Map<Long, TestCaseRegion>> classEntry : index.entrySet()) {
                String className = classEntry.getKey();
                Map<Long, TestCaseRegion> regions = classEntry.getValue();

                indexOutput.writeString(className);
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
            indexOutput.close();
        }
    }

    public Writer writer() {
        return new Writer();
    }

    private static class Index {
        final ImmutableMap<?, Index> children;
        final Region stdOut;
        final Region stdErr;

        private Index() {
            this(new Region(), new Region());
        }

        private Index(Region stdOut, Region stdErr) {
            this.children = ImmutableMap.of();
            this.stdOut = stdOut;
            this.stdErr = stdErr;
        }

        private Index(ImmutableMap<?, Index> children, Region stdOut, Region stdErr) {
            this.children = children;
            this.stdOut = stdOut;
            this.stdErr = stdErr;
        }
    }

    private static class IndexBuilder<K> {
        final Region stdOut = new Region();
        final Region stdErr = new Region();
        final Class<K> keyType;

        private final ImmutableMap.Builder<K, Index> children = ImmutableMap.builder();

        private IndexBuilder(Class<K> keyType) {
            this.keyType = keyType;
        }

        void add(K key, Index index) {
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

    public class Reader {

        private final Index index;

        public Reader() {
            File indexFile = getIndexFile();
            if (!indexFile.exists()) {
                index = new Index();
                return;
            }

            Input input;
            try {
                input = new Input(new FileInputStream(indexFile));
            } catch (FileNotFoundException e) {
                throw new UncheckedIOException(e);
            }

            IndexBuilder<String> rootBuilder = null;
            try {
                int numClasses = input.readInt(true);
                rootBuilder = new IndexBuilder<String>(String.class);

                for (int classCounter = 0; classCounter < numClasses; ++classCounter) {
                    String className = input.readString();
                    IndexBuilder<Long> classBuilder = new IndexBuilder<Long>(Long.class);

                    int numEntries = input.readInt(true);
                    for (int entryCounter = 0; entryCounter < numEntries; ++entryCounter) {
                        long testId = input.readLong(true);
                        Region stdOut = new Region(input.readLong(), input.readLong());
                        Region stdErr = new Region(input.readLong(), input.readLong());
                        classBuilder.add(testId, new Index(stdOut, stdErr));
                    }

                    rootBuilder.add(className, classBuilder.build());
                }
            } finally {
                input.close();
            }

            index = rootBuilder.build();
        }

        public boolean hasOutput(String className, TestOutputEvent.Destination destination) {
            Index classIndex = index.children.get(className);
            if (classIndex == null) {
                return false;
            } else {
                Region region = destination == TestOutputEvent.Destination.StdOut ? classIndex.stdOut : classIndex.stdErr;
                return region.start >= 0;
            }
        }

        public void writeAllOutput(String className, TestOutputEvent.Destination destination, java.io.Writer writer) {
            doRead(className, null, true, destination, writer);
        }

        public void writeNonTestOutput(String className, TestOutputEvent.Destination destination, java.io.Writer writer) {
            doRead(className, null, false, destination, writer);
        }

        public void writeTestOutput(String className, Long testId, TestOutputEvent.Destination destination, java.io.Writer writer) {
            doRead(className, testId, false, destination, writer);
        }

        private void doRead(String className, Long testId, boolean allClassOutput, TestOutputEvent.Destination destination, java.io.Writer writer) {

            Index targetIndex = index.children.get(className);
            if (targetIndex != null && testId != null) {
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

            boolean ignoreClassLevel = !allClassOutput && testId != null;
            boolean ignoreTestLevel = !allClassOutput && testId == null;

            final File file = getOutputsFile();
            try {
                // NOTE: could potentially hold this stream open instead of open/close
                //       if the reader had a dispose lifecycle.
                Input input = new Input(new FileInputStream(file));
                skip(input, region.start);
                try {
                    while (input.total() <= region.stop) {
                        boolean readStdout = input.readBoolean();
                        boolean isClassLevel = input.readBoolean();

                        if (stdout != readStdout || (ignoreClassLevel && isClassLevel) || (ignoreTestLevel && !isClassLevel)) {
                            skipNext(input);
                            input.readLong(true);
                            skipNext(input);
                            continue;
                        }

                        String readClassName = readString(messageStorageCharset, input);
                        if (!className.equals(readClassName)) {
                            input.readLong(true);
                            skipNext(input);
                            continue;
                        }

                        boolean shouldWrite;
                        if (testId == null) {
                            input.readLong(true);
                            shouldWrite = true;
                        } else {
                            long readTestId = input.readLong(true);
                            shouldWrite = testId.longValue() == readTestId;
                        }

                        if (shouldWrite) {
                            String message = readString(messageStorageCharset, input);
                            writer.write(message);
                        } else {
                            skipNext(input);
                        }
                    }
                } finally {
                    input.close();
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    // Workaround for https://code.google.com/p/kryo/issues/detail?id=119
    // Present in Kryo 2.21
    private long skip(Input input, long count) {
        long remaining = count;
        while (remaining > 0) {
            int skip = Math.min(Integer.MAX_VALUE, (int) remaining);
            input.skip(skip);
            remaining -= skip;
        }
        return count;
    }

    public Reader reader() {
        return new Reader();
    }
}
