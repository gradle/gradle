/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.UncheckedException;
import org.gradle.messaging.remote.internal.Message;

import java.io.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class TestResultSerializer {
    private static final int RESULT_VERSION = 1;

    private final File resultsFile;

    public TestResultSerializer(File resultsDir) {
        this.resultsFile = new File(resultsDir, "results.bin");
    }

    public void write(Collection<TestClassResult> results) {
        try {
            OutputStream outputStream = new FileOutputStream(resultsFile);
            try {
                if (!results.isEmpty()) { // only write if we have results, otherwise truncate
                    Output output = new Output(outputStream);
                    output.writeInt(RESULT_VERSION, true);
                    write(results, output);
                    output.flush();
                }
            } finally {
                outputStream.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void write(Collection<TestClassResult> results, Output output) throws IOException {
        output.writeInt(results.size(), true);
        for (TestClassResult result : results) {
            write(result, output);
        }
    }

    private void write(TestClassResult classResult, Output output) throws IOException {
        output.writeLong(classResult.getId(), true);
        output.writeString(classResult.getClassName());
        output.writeLong(classResult.getStartTime());
        output.writeInt(classResult.getResults().size(), true);
        for (TestMethodResult methodResult : classResult.getResults()) {
            write(methodResult, output);
        }
    }

    private void write(TestMethodResult methodResult, Output output) throws IOException {
        output.writeLong(methodResult.getId(), true);
        output.writeString(methodResult.getName());
        output.writeInt(methodResult.getResultType().ordinal(), true);
        output.writeLong(methodResult.getDuration(), true);
        output.writeLong(methodResult.getEndTime());
        if (methodResult.getExceptions().isEmpty()) {
            output.writeBoolean(false);
        } else {
            output.writeBoolean(true);
            Message.send(methodResult.getExceptions(), output);
        }
    }

    public void read(Action<? super TestClassResult> visitor) {
        if (!isHasResults()) {
            return;
        }
        try {
            InputStream inputStream = new FileInputStream(resultsFile);
            try {
                Input input = new Input(inputStream);
                int version = input.readInt(true);
                if (version != RESULT_VERSION) {
                    throw new IllegalArgumentException(String.format("Unexpected result file version %d found in %s.", version, resultsFile));
                }
                readResults(input, visitor);
            } finally {
                inputStream.close();
            }
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public boolean isHasResults() {
        return resultsFile.exists() && resultsFile.length() > 0;
    }

    private void readResults(Input input, Action<? super TestClassResult> visitor) throws ClassNotFoundException, IOException {
        int classCount = input.readInt(true);
        for (int i = 0; i < classCount; i++) {
            TestClassResult classResult = readClassResult(input);
            visitor.execute(classResult);
        }
    }

    private TestClassResult readClassResult(Input input) throws IOException, ClassNotFoundException {
        long id = input.readLong(true);
        String className = input.readString();
        long startTime = input.readLong();
        TestClassResult result = new TestClassResult(id, className, startTime);
        int testMethodCount = input.readInt(true);
        for (int i = 0; i < testMethodCount; i++) {
            TestMethodResult methodResult = readMethodResult(input);
            result.add(methodResult);
        }
        return result;
    }

    private TestMethodResult readMethodResult(Input input) throws ClassNotFoundException, IOException {
        long id = input.readLong(true);
        String name = input.readString();
        TestResult.ResultType resultType = TestResult.ResultType.values()[input.readInt(true)];
        long duration = input.readLong(true);
        long endTime = input.readLong();
        boolean hasFailures = input.readBoolean();
        List<Throwable> failures;
        if (hasFailures) {
            failures = (List<Throwable>) Message.receive(input, getClass().getClassLoader());
        } else {
            failures = Collections.emptyList();
        }
        return new TestMethodResult(id, name, resultType, duration, endTime, failures);
    }

}
