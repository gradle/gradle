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
        checkRootDepth();
    }

    private void checkRootDepth() {
        // 1 is root level
        // 2 is class level
        // 3 is test level
        if (root.getDepth() > 3) {
            StringBuilder out = new StringBuilder();
            root.dumpStructure(out);
            throw new IllegalStateException("Test tree root depth must be less than or equal to 3, but was " + root.getDepth() + ". Structure:\n" + out);
        }
    }

    private long getModelId(TestTreeModel model) {
        return modelIdMap.computeIfAbsent(model, m -> nextId++);
    }

    @Override
    public void writeAllOutput(long classId, TestOutputEvent.Destination destination, Writer writer) {
        writeNonTestOutput(classId, destination, writer);
        TestTreeModel model = modelIdMap.inverse().get(classId);
        for (TestTreeModel testModel : model.getChildren().values()) {
            long outputId = testModel.getPerRootInfo().get(0).getOutputId();
            copyOutput(outputId, destination, writer);
        }
    }

    @Override
    public void writeNonTestOutput(long classId, TestOutputEvent.Destination destination, Writer writer) {
        TestTreeModel model = modelIdMap.inverse().get(classId);
        long outputId = model.getPerRootInfo().get(0).getOutputId();
        copyOutput(outputId, destination, writer);
    }

    @Override
    public void writeTestOutput(long classId, long testId, TestOutputEvent.Destination destination, Writer writer) {
        TestTreeModel model = modelIdMap.inverse().get(testId);
        long outputId = model.getPerRootInfo().get(0).getOutputId();
        copyOutput(outputId, destination, writer);
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
        long id = getModelId(model);
        SerializableTestResult result = model.getPerRootInfo().get(0).getResult();
        TestClassResult classResult = new TestClassResult(id, result.getName(), result.getDisplayName(), result.getStartTime());
        for (TestTreeModel testModel : model.getChildren().values()) {
            TestMethodResult methodResult = buildMethodResult(testModel);
            classResult.add(methodResult);
        }
        return classResult;
    }

    private TestMethodResult buildMethodResult(TestTreeModel testModel) {
        long id = getModelId(testModel);
        SerializableTestResult result = testModel.getPerRootInfo().get(0).getResult();
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
        if (outputReader.hasOutput(model.getPerRootInfo().get(0).getOutputId(), destination)) {
            return true;
        }
        for (TestTreeModel testModel : model.getChildren().values()) {
            if (outputReader.hasOutput(testModel.getPerRootInfo().get(0).getOutputId(), destination)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasOutput(long classId, long testId, TestOutputEvent.Destination destination) {
        TestTreeModel model = modelIdMap.inverse().get(testId);
        return outputReader.hasOutput(model.getPerRootInfo().get(0).getOutputId(), destination);
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
