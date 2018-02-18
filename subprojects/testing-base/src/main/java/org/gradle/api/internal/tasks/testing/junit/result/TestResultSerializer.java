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

import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.FlushableEncoder;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;

import java.io.*;
import java.util.Collection;

public class TestResultSerializer {
    private static final int RESULT_VERSION = 3;

    private final File resultsFile;

    public TestResultSerializer(File resultsDir) {
        this.resultsFile = new File(resultsDir, "results.bin");
    }

    public void write(Collection<TestClassResult> results) {
        try {
            OutputStream outputStream = new FileOutputStream(resultsFile);
            try {
                if (!results.isEmpty()) { // only write if we have results, otherwise truncate
                    FlushableEncoder encoder = new KryoBackedEncoder(outputStream);
                    encoder.writeSmallInt(RESULT_VERSION);
                    write(results, encoder);
                    encoder.flush();
                }
            } finally {
                outputStream.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void write(Collection<TestClassResult> results, Encoder encoder) throws IOException {
        encoder.writeSmallInt(results.size());
        for (TestClassResult result : results) {
            write(result, encoder);
        }
    }

    private void write(TestClassResult classResult, Encoder encoder) throws IOException {
        encoder.writeSmallLong(classResult.getId());
        encoder.writeString(classResult.getClassName());
        encoder.writeString(classResult.getClassDisplayName());
        encoder.writeLong(classResult.getStartTime());
        encoder.writeSmallInt(classResult.getResults().size());
        for (TestMethodResult methodResult : classResult.getResults()) {
            write(methodResult, encoder);
        }
    }

    private void write(TestMethodResult methodResult, Encoder encoder) throws IOException {
        encoder.writeSmallLong(methodResult.getId());
        encoder.writeString(methodResult.getName());
        encoder.writeString(methodResult.getDisplayName());
        encoder.writeSmallInt(methodResult.getResultType().ordinal());
        encoder.writeSmallLong(methodResult.getDuration());
        encoder.writeLong(methodResult.getEndTime());
        encoder.writeSmallInt(methodResult.getFailures().size());
        for (TestFailure testFailure : methodResult.getFailures()) {
            encoder.writeString(testFailure.getExceptionType());
            encoder.writeString(testFailure.getMessage());
            encoder.writeString(testFailure.getStackTrace());
        }
    }

    public void read(Action<? super TestClassResult> visitor) {
        if (!isHasResults()) {
            return;
        }
        try {
            InputStream inputStream = new FileInputStream(resultsFile);
            try {
                Decoder decoder = new KryoBackedDecoder(inputStream);
                int version = decoder.readSmallInt();
                if (version != RESULT_VERSION) {
                    throw new IllegalArgumentException(String.format("Unexpected result file version %d found in %s.", version, resultsFile));
                }
                readResults(decoder, visitor);
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

    private void readResults(Decoder decoder, Action<? super TestClassResult> visitor) throws ClassNotFoundException, IOException {
        int classCount = decoder.readSmallInt();
        for (int i = 0; i < classCount; i++) {
            TestClassResult classResult = readClassResult(decoder);
            visitor.execute(classResult);
        }
    }

    private TestClassResult readClassResult(Decoder decoder) throws IOException, ClassNotFoundException {
        long id = decoder.readSmallLong();
        String className = decoder.readString();
        String classDisplayName = decoder.readString();
        long startTime = decoder.readLong();
        TestClassResult result = new TestClassResult(id, className, classDisplayName, startTime);
        int testMethodCount = decoder.readSmallInt();
        for (int i = 0; i < testMethodCount; i++) {
            TestMethodResult methodResult = readMethodResult(decoder);
            result.add(methodResult);
        }
        return result;
    }

    private TestMethodResult readMethodResult(Decoder decoder) throws ClassNotFoundException, IOException {
        long id = decoder.readSmallLong();
        String name = decoder.readString();
        String displayName = decoder.readString();
        TestResult.ResultType resultType = TestResult.ResultType.values()[decoder.readSmallInt()];
        long duration = decoder.readSmallLong();
        long endTime = decoder.readLong();
        TestMethodResult methodResult = new TestMethodResult(id, name, displayName, resultType, duration, endTime);
        int failures = decoder.readSmallInt();
        for (int i = 0; i < failures; i++) {
            String exceptionType = decoder.readString();
            String message = decoder.readString();
            String stackTrace = decoder.readString();
            methodResult.addFailure(message, stackTrace, exceptionType);
        }
        return methodResult;
    }
}
