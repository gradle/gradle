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
        long start = -1;
        long stop = -1;

        private Region() {
        }

        private Region(long start, long stop) {
            this.start = start;
            this.stop = stop;
        }
    }

    private static class TestCaseRegion {
        String name;
        Region stdOutRegion = new Region();
        Region stdErrRegion = new Region();
    }

    public class Writer {
        private final Output output;

        private final Map<String, Map<String, TestCaseRegion>> index = new LinkedHashMap<String, Map<String, TestCaseRegion>>();

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

        public void onOutput(final TestDescriptorInternal testDescriptor, TestOutputEvent.Destination destination, final String message) {
            String className = testDescriptor.getClassName();
            String methodName = testDescriptor.getName();
            boolean stdout = destination == TestOutputEvent.Destination.StdOut;

            mark(className, methodName, stdout);

            output.writeString(className);
            output.writeString(methodName);
            output.writeBoolean(stdout);
            byte[] bytes = message.getBytes(messageStorageCharset);
            output.writeInt(bytes.length, true);
            output.writeBytes(bytes);
        }

        private void mark(String className, String testCaseName, boolean isStdout) {
            if (!index.containsKey(className)) {
                index.put(className, new LinkedHashMap<String, TestCaseRegion>());
            }

            Map<String, TestCaseRegion> testCaseRegions = index.get(className);
            if (!testCaseRegions.containsKey(testCaseName)) {
                TestCaseRegion region = new TestCaseRegion();
                region.name = testCaseName;
                testCaseRegions.put(testCaseName, region);
            }

            TestCaseRegion region = testCaseRegions.get(testCaseName);

            Region streamRegion = isStdout ? region.stdOutRegion : region.stdErrRegion;

            if (streamRegion.start < 0) {
                streamRegion.start = output.position();
            }
            streamRegion.stop = output.position();
        }

        private void writeIndex() {
            Output indexOutput;
            try {
                indexOutput = new Output(new FileOutputStream(getIndexFile()));
            } catch (FileNotFoundException e) {
                throw new UncheckedIOException(e);
            }

            indexOutput.writeInt(index.size(), true);

            for (Map.Entry<String, Map<String, TestCaseRegion>> classEntry : index.entrySet()) {
                String className = classEntry.getKey();
                Map<String, TestCaseRegion> regions = classEntry.getValue();

                indexOutput.writeString(className);
                indexOutput.writeInt(regions.size(), true);

                for (Map.Entry<String, TestCaseRegion> testCaseEntry : regions.entrySet()) {
                    TestCaseRegion region = testCaseEntry.getValue();

                    indexOutput.writeString(region.name);
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
        final ImmutableMap<String, Index> children;
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

        private Index(ImmutableMap<String, Index> children, Region stdOut, Region stdErr) {
            this.children = children;
            this.stdOut = stdOut;
            this.stdErr = stdErr;
        }
    }

    private static class IndexBuilder {
        final Region stdOut = new Region();
        final Region stdErr = new Region();

        private final ImmutableMap.Builder<String, Index> children = ImmutableMap.builder();

        void add(String name, Index index) {
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

            children.put(name, index);
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

            IndexBuilder rootBuilder = null;
            try {
                int numClasses = input.readInt(true);
                rootBuilder = new IndexBuilder();

                for (int classCounter = 0; classCounter < numClasses; ++classCounter) {
                    String className = input.readString();
                    IndexBuilder classBuilder = new IndexBuilder();

                    int numEntries = input.readInt(true);
                    for (int entryCounter = 0; entryCounter < numEntries; ++entryCounter) {
                        String name = input.readString();
                        Region stdOut = new Region(input.readLong(), input.readLong());
                        Region stdErr = new Region(input.readLong(), input.readLong());
                        classBuilder.add(name, new Index(stdOut, stdErr));
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

        public void readTo(String className, TestOutputEvent.Destination destination, java.io.Writer writer) {
            doRead(className, null, destination, writer);
        }

        public void readTo(String className, String testCaseName, TestOutputEvent.Destination destination, java.io.Writer writer) {
            doRead(className, testCaseName, destination, writer);
        }

        protected void doRead(String className, String targetTestCaseName, TestOutputEvent.Destination destination, java.io.Writer writer) {

            Index targetIndex = index.children.get(className);
            if (targetIndex != null && targetTestCaseName != null) {
                targetIndex = targetIndex.children.get(targetTestCaseName);
            }

            if (targetIndex == null) {
                return;
            }

            boolean stdout = destination == TestOutputEvent.Destination.StdOut;
            Region region = stdout ? targetIndex.stdOut : targetIndex.stdErr;

            if (region.start < 0) {
                return;
            }

            final File file = getOutputsFile();
            try {
                // NOTE: could potentially hold this stream open instead of open/close
                //       if the reader had a dispose lifecycle.
                Input input = new Input(new FileInputStream(file));
                skip(input, region.start);
                try {
                    while (input.position() <= region.stop) {
                        String readClassName = input.readString();
                        String readTestCaseName = input.readString();
                        boolean readStdout = input.readBoolean();

                        int readMessageLength = input.readInt(true);

                        boolean shouldWrite = readStdout == stdout
                                && readClassName.equals(className)
                                && (targetTestCaseName == null || targetTestCaseName.equals(readTestCaseName));

                        if (shouldWrite) {
                            byte[] bytes = input.readBytes(readMessageLength);
                            writer.write(new String(bytes, messageStorageCharset));
                        } else {
                            input.skip(readMessageLength);
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
