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
package org.gradle.execution;

import static org.hamcrest.Matchers.equalTo;
import org.hamcrest.Matchers;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.gradle.api.Task;
import static org.gradle.util.WrapUtil.toList;

/**
 * @author Hans Dockter
 */
public class DefaultOutputTest {
    private JUnit4Mockery context = new JUnit4Mockery();
    private Task taskStub = context.mock(Task.class);
    private DefaultOutputHandler outputHandler = new DefaultOutputHandler(taskStub);

    @Test
    public void init() {
        assertThat(outputHandler.getHasOutput(), equalTo(false));
    }

    @Test
    public void setOutput() {
        outputHandler.setHasOutput(true);
        assertThat(outputHandler.getHasOutput(), equalTo(true));
    }

    @Test
    public void testGetHistory() {
        final OutputHistory outputHistoryDummy = context.mock(OutputHistory.class);
        final OutputHistoryReader outputHistoryReaderStub = context.mock(OutputHistoryReader.class);
        outputHandler.setOutputHistoryReader(outputHistoryReaderStub);
        context.checking(new Expectations() {{
            allowing(outputHistoryReaderStub).readHistory(taskStub);
            will(returnValue(outputHistoryDummy));
        }});
        assertThat(outputHandler.getHistory(), Matchers.sameInstance(outputHistoryDummy));
    }

    @Test
    public void writeHistoryWithHasOutputTrueAndExecutionSuccessful() {
        final OutputHistoryWriter outputHistoryWriterMock = context.mock(OutputHistoryWriter.class);

        outputHandler.setHasOutput(true);
        outputHandler.setOutputHistoryWriter(outputHistoryWriterMock);

        context.checking(new Expectations() {{
            one(outputHistoryWriterMock).taskSuccessfullyExecuted(taskStub);
        }});

        outputHandler.writeHistory(true);
    }

    @Test
    public void writeHistoryWithHasOutputTrueAndExecutionFailed() {
        final OutputHistoryWriter outputHistoryWriterMock = context.mock(OutputHistoryWriter.class);

        outputHandler.setHasOutput(true);
        outputHandler.setOutputHistoryWriter(outputHistoryWriterMock);

        context.checking(new Expectations() {{
            one(outputHistoryWriterMock).taskFailed(taskStub);
        }});

        outputHandler.writeHistory(false);
    }

    @Test
    public void writeHistoryWithHasOutputFalseAndExecutionSuccessful() {
        final OutputHistoryWriter outputHistoryWriterMock = context.mock(OutputHistoryWriter.class);

        outputHandler.setHasOutput(false);
        outputHandler.setOutputHistoryWriter(outputHistoryWriterMock);

        context.checking(new Expectations() {{
            one(outputHistoryWriterMock).taskFailed(taskStub);
        }});

        outputHandler.writeHistory(true);
    }

    @Test
    public void writeHistoryWithHasOutputFalseAndExecutionFailed() {
        final OutputHistoryWriter outputHistoryWriterMock = context.mock(OutputHistoryWriter.class);

        outputHandler.setHasOutput(false);
        outputHandler.setOutputHistoryWriter(outputHistoryWriterMock);

        context.checking(new Expectations() {{
            one(outputHistoryWriterMock).taskFailed(taskStub);
        }});

        outputHandler.writeHistory(false);
    }

    
}
