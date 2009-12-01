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
package org.gradle.api.testing.execution.control.messages.client;

import org.gradle.api.testing.execution.control.refork.ReforkContextData;
import org.gradle.api.testing.fabric.TestClassProcessResult;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * @author Tom Eyckmans
 */
public class NextActionRequestMessage extends AbstractTestClientControlMessage {

    private TestClassProcessResult previousProcessedTestResult = null;
    private ReforkContextData reforkContextData = null;

    public NextActionRequestMessage(final int forkId) {
        super(forkId);
    }

    public TestClassProcessResult getPreviousProcessedTestResult() {
        return previousProcessedTestResult;
    }

    public ReforkContextData getReforkDecisionContext() {
        return reforkContextData;
    }

    public void setPreviousProcessedTestResult(TestClassProcessResult previousProcessedTestResult) {
        this.previousProcessedTestResult = previousProcessedTestResult;
    }

    public void setReforkDecisionContext(ReforkContextData reforkContextData) {
        this.reforkContextData = reforkContextData;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeObject(previousProcessedTestResult);
        out.writeObject(reforkContextData);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        previousProcessedTestResult = (TestClassProcessResult) in.readObject();
        reforkContextData = (ReforkContextData) in.readObject();
    }
}
