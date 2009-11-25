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

import org.junit.Before;
import static org.junit.Assert.assertEquals;
import org.gradle.api.testing.execution.control.refork.ReforkDecisionContext;
import org.gradle.api.testing.execution.control.refork.ReforkDecisionContextImpl;
import org.gradle.api.testing.fabric.TestClassProcessResult;

/**
 * @author Tom Eyckmans
 */
public class NextActionRequestMessageTest extends AbstractTestClientControlMessageTest<NextActionRequestMessage> {
    private ReforkDecisionContext reforkDecisionContext;
    private TestClassProcessResult previousProcessedTestResult;

    @Before
    public void setUp() throws Exception
    {
        reforkDecisionContext = new ReforkDecisionContextImpl();
        previousProcessedTestResult = new TestClassProcessResult(null);
    }

    protected NextActionRequestMessage createMessageObject(int forkId) {
        final NextActionRequestMessage message = new NextActionRequestMessage(forkId);

        message.setReforkDecisionContext(reforkDecisionContext);
        message.setPreviousProcessedTestResult(previousProcessedTestResult);

        return message;
    }

    @Override
    protected void assertTestClientControlMessage(NextActionRequestMessage originalMessage, NextActionRequestMessage deserializedMessage) {
        // TODO expand
        assertEquals(originalMessage.getReforkDecisionContext().isEmpty(), deserializedMessage.getReforkDecisionContext().isEmpty());
        assertEquals(originalMessage.getPreviousProcessedTestResult().getTestClassRunInfo(), deserializedMessage.getPreviousProcessedTestResult().getTestClassRunInfo());
    }
}
