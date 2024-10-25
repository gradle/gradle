/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.logging.events;

import org.gradle.api.logging.LogLevel;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.time.Timestamp;

public class UserInputResumeEvent extends RenderableOutputEvent implements InteractiveEvent {
    public UserInputResumeEvent(long timestamp) {
        super(Timestamp.ofMillis(timestamp), "prompt", LogLevel.QUIET, null);
    }

    @Override
    public LogLevel getLogLevel() {
        return LogLevel.QUIET;
    }

    @Override
    public void render(StyledTextOutput output) {
        // Add a newline after a batch of questions
        output.println();
    }

    @Override
    public RenderableOutputEvent withBuildOperationId(OperationIdentifier buildOperationId) {
        throw new UnsupportedOperationException();
    }
}
