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

class ErrorOutputDispatchingListener implements OutputEventListener {
    private final OutputEventListener stderrChain;
    private final OutputEventListener stdoutChain;
    private final boolean redirectStderr;

    public ErrorOutputDispatchingListener(OutputEventListener stderrChain, OutputEventListener stdoutChain, boolean redirectStderr) {
        this.stderrChain = stderrChain;
        this.stdoutChain = stdoutChain;
        this.redirectStderr = redirectStderr;
    }

    @Override
    public void onOutput(OutputEvent event) {
        if (event.getLogLevel() == null) {
            stderrChain.onOutput(event);
            stdoutChain.onOutput(event);
        } else if (event.getLogLevel() != LogLevel.ERROR) {
            stdoutChain.onOutput(event);
        } else {
            // Write anything that isn't the build failure report to the output stream if there is a console attached.
            // This is to avoid interleaving of error and non-error output generated at approximately the same time, as the process stdout and stderr may be forwarded on different pipes and so ordering is lost
            // TODO - should attempt to flush the output stream prior to writing to the error stream
            if (redirectStderr) {
                stdoutChain.onOutput(event);
            } else {
                stderrChain.onOutput(event);
            }
        }
    }
}
