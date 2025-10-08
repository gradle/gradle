/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.report.generic;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Iterables;
import com.google.common.io.CharStreams;
import org.gradle.api.Action;
import org.gradle.api.internal.tasks.testing.junit.result.TestClassResult;
import org.gradle.api.internal.tasks.testing.junit.result.TestMethodResult;
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableFailure;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResult;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResultStore;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.internal.UncheckedException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

public class TestTreeModelResultsProvider implements TestResultsProvider {
    private final TestTreeModel root;
    private final SerializableTestResultStore.OutputReader outputReader;
    private final BiMap<TestTreeModel, Long> modelIdMap = HashBiMap.create();
    private long nextId = 1;

    public TestTreeModelResultsProvider(TestTreeModel root, SerializableTestResultStore.OutputReader outputReader) {
        this.root = root;
        this.outputReader = outputReader;
    }

    private long getClassId(TestTreeModel model) {
        return modelIdMap.computeIfAbsent(model, m -> nextId++);
    }

    private static long getMethodId(TestTreeModel.PerRootInfo model) {
        return model.getOutputId();
    }

    @Override
    public void writeAllOutput(long classId, TestOutputEvent.Destination destination, Writer writer) {
        writeNonTestOutput(classId, destination, writer);
        TestTreeModel model = modelIdMap.inverse().get(classId);
        for (TestTreeModel testModel : model.getChildren().values()) {
            for (TestTreeModel.PerRootInfo perRootInfo : testModel.getPerRootInfo().get(0)) {
                copyOutput(perRootInfo.getOutputId(), destination, writer);
            }
        }
    }

    @Override
    public void writeNonTestOutput(long classId, TestOutputEvent.Destination destination, Writer writer) {
        TestTreeModel model = modelIdMap.inverse().get(classId);
        // There should only be one node for a class, all other nodes get merged into one
        TestTreeModel.PerRootInfo perRootInfo = Iterables.getOnlyElement(model.getPerRootInfo().get(0));
        copyOutput(perRootInfo.getOutputId(), destination, writer);
    }

    @Override
    public void writeTestOutput(long classId, long testId, TestOutputEvent.Destination destination, Writer writer) {
        // We know the testId is the outputId
        copyOutput(testId, destination, writer);
    }

    private void copyOutput(long outputId, TestOutputEvent.Destination destination, Writer writer) {
        try (Reader output = outputReader.getOutput(outputId, destination)) {
            CharStreams.copy(output, writer);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public void visitClasses(Action<? super TestClassResult> visitor) {
        for (TestTreeModel model : root.getChildren().values()) {
            TestClassResult classResult = buildClassResult(model);
            visitor.execute(classResult);
        }
    }

    private TestClassResult buildClassResult(TestTreeModel model) {
        long id = getClassId(model);
        // There should only be one node for a class, all other nodes get merged into one
        SerializableTestResult result = Iterables.getOnlyElement(model.getPerRootInfo().get(0)).getResult();
        TestClassResult classResult = new TestClassResult(id, result.getName(), result.getDisplayName(), result.getStartTime());
        for (TestTreeModel testModel : model.getChildren().values()) {
            for (TestTreeModel.PerRootInfo perRootInfo : testModel.getPerRootInfo().get(0)) {
                TestMethodResult methodResult = buildMethodResult(perRootInfo);
                classResult.add(methodResult);
            }
        }
        return classResult;
    }

    private static TestMethodResult buildMethodResult(TestTreeModel.PerRootInfo perRootInfo) {
        long id = getMethodId(perRootInfo);
        SerializableTestResult result = perRootInfo.getResult();
        TestMethodResult methodResult = new TestMethodResult(id, result.getName(), result.getDisplayName(), result.getResultType(), result.getDuration(), result.getEndTime());
        methodResult.getFailures().addAll(result.getFailures());
        if (result.getAssumptionFailure() != null) {
            SerializableFailure assumptionFailure = result.getAssumptionFailure();
            methodResult.setAssumptionFailure(assumptionFailure.getMessage(), assumptionFailure.getStackTrace(), assumptionFailure.getExceptionType());
        }
        return methodResult;
    }

    @Override
    public boolean hasOutput(long classId, TestOutputEvent.Destination destination) {
        TestTreeModel model = modelIdMap.inverse().get(classId);
        if (outputReader.hasOutput(Iterables.getOnlyElement(model.getPerRootInfo().get(0)).getOutputId(), destination)) {
            return true;
        }
        for (TestTreeModel testModel : model.getChildren().values()) {
            for (TestTreeModel.PerRootInfo perRootInfo : testModel.getPerRootInfo().get(0)) {
                if (outputReader.hasOutput(perRootInfo.getOutputId(), destination)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean hasOutput(long classId, long testId, TestOutputEvent.Destination destination) {
        // We know the testId is the outputId
        return outputReader.hasOutput(testId, destination);
    }

    @Override
    public boolean isHasResults() {
        return !root.getChildren().isEmpty();
    }

    @Override
    public void close() throws IOException {
        outputReader.close();
    }
}
