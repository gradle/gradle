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

import org.gradle.api.Task;

/**
 * @author Hans Dockter
 */
public class DefaultOutputHandler implements OutputHandler {
    private OutputHistoryReader outputHistoryReader = new DefaultOutputHistoryReader();
    private OutputHistoryWriter outputHistoryWriter = new DefaultOutputHistoryWriter();
    private Task task;
    private boolean hasOutput = false;

    public DefaultOutputHandler(Task task) {
        this.task = task;
    }

    public OutputHistory getHistory() {
        return outputHistoryReader.readHistory(task);
    }

    public boolean getHasOutput() {
        return hasOutput;
    }

    public void setHasOutput(boolean hasOutput) {
        this.hasOutput = hasOutput;
    }

    public void writeHistory(boolean successful) {
        if (hasOutput && successful) {
            outputHistoryWriter.taskSuccessfullyExecuted(task);
        } else {
            outputHistoryWriter.taskFailed(task);
        }
    }

    public OutputHistoryReader getOutputHistoryReader() {
        return outputHistoryReader;
    }

    public void setOutputHistoryReader(OutputHistoryReader outputHistoryReader) {
        this.outputHistoryReader = outputHistoryReader;
    }

    public OutputHistoryWriter getOutputHistoryWriter() {
        return outputHistoryWriter;
    }

    public void setOutputHistoryWriter(OutputHistoryWriter outputHistoryWriter) {
        this.outputHistoryWriter = outputHistoryWriter;
    }
}
