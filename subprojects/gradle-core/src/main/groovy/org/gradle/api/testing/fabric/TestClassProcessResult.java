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
package org.gradle.api.testing.fabric;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Tom Eyckmans
 */
public class TestClassProcessResult implements Serializable {

    private TestClassRunInfo testClassRunInfo;

    private Throwable executionErrorReason;
    private Throwable processorErrorReason;

    private List<TestMethodProcessResult> methodResults;

    public TestClassProcessResult(TestClassRunInfo testClassRunInfo) {
        this.testClassRunInfo = testClassRunInfo;
        methodResults = new ArrayList<TestMethodProcessResult>();
    }

    public void addMethodResult(TestMethodProcessResult methodResult) {
        methodResults.add(methodResult);
    }

    public List<TestMethodProcessResult> getMethodResults() {
        return methodResults;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeObject(testClassRunInfo);
        out.writeObject(executionErrorReason);
        out.writeObject(processorErrorReason);
        out.writeObject(methodResults);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        testClassRunInfo = (TestClassRunInfo) in.readObject();
        executionErrorReason = (Throwable) in.readObject();
        processorErrorReason = (Throwable) in.readObject();
        methodResults = (List<TestMethodProcessResult>) in.readObject();
    }

    public TestClassRunInfo getTestClassRunInfo() {
        return testClassRunInfo;
    }

    public Throwable getExecutionErrorReason() {
        return executionErrorReason;
    }

    public Throwable getProcessorErrorReason() {
        return processorErrorReason;
    }

    void setExecutionErrorReason(Throwable executionErrorReason) {
        this.executionErrorReason = executionErrorReason;
    }

    void setProcessorErrorReason(Throwable processorErrorReason) {
        this.processorErrorReason = processorErrorReason;
    }
}
