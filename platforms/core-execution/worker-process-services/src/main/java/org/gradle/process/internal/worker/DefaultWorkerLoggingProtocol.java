/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.process.internal.worker;

import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.StyledTextOutputEvent;
import org.gradle.process.internal.worker.child.WorkerLoggingProtocol;

public class DefaultWorkerLoggingProtocol implements WorkerLoggingProtocol {
    private OutputEventListener outputEventListener;

    public DefaultWorkerLoggingProtocol(OutputEventListener outputEventListener) {
        this.outputEventListener = outputEventListener;
    }

    @Override
    public void sendOutputEvent(LogEvent event) {
        outputEventListener.onOutput(event);
    }

    @Override
    public void sendOutputEvent(StyledTextOutputEvent event) {
        outputEventListener.onOutput(event);
    }

}
