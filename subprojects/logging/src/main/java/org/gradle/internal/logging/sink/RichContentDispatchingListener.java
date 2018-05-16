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

import org.gradle.api.logging.LogLevel;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;

public class RichContentDispatchingListener implements OutputEventListener {
    private final OutputEventListener richContentChain;
    private final OutputEventListener plainContentChain;
    private final boolean stdoutAttachedToConsole;
    private final boolean stderrAttachedToConsole;

    public RichContentDispatchingListener(OutputEventListener richContentChain, OutputEventListener plainContentChain, boolean stdoutAttachedToConsole, boolean stderrAttachedToConsole) {
        this.richContentChain = richContentChain;
        this.plainContentChain = plainContentChain;
        this.stdoutAttachedToConsole = stdoutAttachedToConsole;
        this.stderrAttachedToConsole = stderrAttachedToConsole;
    }

    @Override
    public void onOutput(OutputEvent event) {
        if (stdoutAttachedToConsole && stderrAttachedToConsole) {
            richContentChain.onOutput(event);
        } else if (stdoutAttachedToConsole) {
            if (event.getLogLevel() != LogLevel.ERROR) {
                richContentChain.onOutput(event);
            }
        }
    }
}
