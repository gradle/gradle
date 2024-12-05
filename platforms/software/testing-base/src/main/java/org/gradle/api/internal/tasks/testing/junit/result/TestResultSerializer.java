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

import org.gradle.api.UncheckedIOException;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.FlushableEncoder;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TestResultSerializer {
    private static final int RESULT_VERSION = 4;

    public enum VersionMismatchAction {
        THROW_EXCEPTION,
        RETURN_NULL,
    }

    private final Path resultsFile;

    public TestResultSerializer(File resultsDir) {
        this.resultsFile = resultsDir.toPath().resolve("results.bin");
    }

    public void write(PersistentTestResult rootResult) {
        try (OutputStream outputStream = Files.newOutputStream(resultsFile)) {
            if (!rootResult.getChildren().isEmpty()) { // only write if we have results, otherwise truncate
                FlushableEncoder encoder = new KryoBackedEncoder(outputStream);
                encoder.writeSmallInt(RESULT_VERSION);
                write(rootResult, encoder);
                encoder.flush();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(PersistentTestResult result, Encoder encoder) throws IOException {
        encoder.writeSmallLong(result.getId());
        encoder.writeString(result.getName());
        encoder.writeString(result.getDisplayName());
        encoder.writeSmallInt(result.getResultType().ordinal());
        encoder.writeLong(result.getStartTime());
        encoder.writeLong(result.getEndTime());

        encoder.writeSmallInt(result.getFailures().size());
        for (PersistentTestFailure testFailure : result.getFailures()) {
            encoder.writeString(testFailure.getExceptionType());
            encoder.writeString(testFailure.getMessage());
            encoder.writeString(testFailure.getStackTrace());
        }

        encoder.writeSmallInt(result.getChildren().size());
        for (PersistentTestResult methodResult : result.getChildren()) {
            write(methodResult, encoder);
        }
    }

    @Nullable
    public PersistentTestResult read(VersionMismatchAction versionMismatchAction) {
        if (!isHasResults()) {
            return null;
        }
        try (InputStream inputStream = Files.newInputStream(resultsFile)) {
            Decoder decoder = new KryoBackedDecoder(inputStream);
            int version = decoder.readSmallInt();
            if (version != RESULT_VERSION) {
                if (versionMismatchAction == VersionMismatchAction.RETURN_NULL) {
                    return null;
                } else if (versionMismatchAction == VersionMismatchAction.THROW_EXCEPTION) {
                    throw new IllegalArgumentException(String.format("Unexpected result file version %d found in %s.", version, resultsFile));
                }
                throw new AssertionError("Unexpected version mismatch action: " + versionMismatchAction);
            }
            return read(decoder);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public boolean isHasResults() {
        try {
            return Files.size(resultsFile) > 0;
        } catch (IOException e) {
            return false;
        }
    }

    private static PersistentTestResult read(Decoder decoder) throws IOException {
        long id = decoder.readSmallLong();
        String name = decoder.readString();
        String displayName = decoder.readString();
        TestResult.ResultType resultType = TestResult.ResultType.values()[decoder.readSmallInt()];
        long startTime = decoder.readLong();
        long endTime = decoder.readLong();

        int failureCount = decoder.readSmallInt();
        List<PersistentTestFailure> failures = new ArrayList<>(failureCount);
        for (int i = 0; i < failureCount; i++) {
            String exceptionType = decoder.readString();
            String message = decoder.readString();
            String stackTrace = decoder.readString();
            failures.add(new PersistentTestFailure(message, stackTrace, exceptionType));
        }

        int childCount = decoder.readSmallInt();
        List<PersistentTestResult> children = new ArrayList<>(childCount);
        for (int i = 0; i < childCount; i++) {
            children.add(read(decoder));
        }

        return new PersistentTestResult(id, name, displayName, resultType, startTime, endTime, failures, children);
    }
}
