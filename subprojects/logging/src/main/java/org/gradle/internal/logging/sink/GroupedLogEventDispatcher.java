/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.logging.sink;

import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.RenderableOutputEvent;

import javax.annotation.Nullable;

public class GroupedLogEventDispatcher extends LogEventDispatcher {
    private final OutputEventListener stdoutChain;

    public GroupedLogEventDispatcher(@Nullable OutputEventListener stdoutChain, @Nullable OutputEventListener stderrChain) {
        super(stdoutChain, stderrChain);
        this.stdoutChain = stdoutChain;
    }

    @Override
    public void onOutput(OutputEvent event) {
        if (stdoutChain != null && isGrouped(event)) {
            stdoutChain.onOutput(event);
        } else {
            super.onOutput(event);
        }
    }

    private boolean isGrouped(OutputEvent event) {
        return event instanceof RenderableOutputEvent && ((RenderableOutputEvent)event).isGrouped();
    }
}
