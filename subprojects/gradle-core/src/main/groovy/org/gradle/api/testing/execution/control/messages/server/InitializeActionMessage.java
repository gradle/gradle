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
package org.gradle.api.testing.execution.control.messages.server;

import org.gradle.api.testing.execution.control.refork.ReforkItemConfigs;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * @author Tom Eyckmans
 */
public class InitializeActionMessage extends AbstractTestServerControlMessage {

    private String testFrameworkId;
    private ReforkItemConfigs reforkItemConfigs;

    public InitializeActionMessage(int pipelineId) {
        super(pipelineId);
    }

    public String getTestFrameworkId() {
        return testFrameworkId;
    }

    public void setTestFrameworkId(String testFrameworkId) {
        this.testFrameworkId = testFrameworkId;
    }

    public ReforkItemConfigs getReforkItemConfigs() {
        return reforkItemConfigs;
    }

    public void setReforkItemConfigs(ReforkItemConfigs reforkItemConfigs) {
        this.reforkItemConfigs = reforkItemConfigs;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeUTF(testFrameworkId);
        out.writeObject(reforkItemConfigs);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        testFrameworkId = in.readUTF();
        reforkItemConfigs = (ReforkItemConfigs) in.readObject();
    }
}
