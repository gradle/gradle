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
        // TODO(daniel): The events received here are from the worker process.
        // TODO(daniel): This code is executed in a different thread than the worker client (the thread with the current BuildOperation) therefore we can't get the OperationIdentifier or BuildOperation#getId()

        // FIXME(adam): However, I would step back and consider whether it is the best approach to send the parent build operation id across to the worker process and then sending it back in every event.
        // FIXME(ADAM): ... It might make more sense to simply attach the parent operation id to events whose build operation id is null received from the worker process.

        // TODO(ew): presumably this is sent from worker to client.
        // What is receiving these events?
        // Can it know what the Parent Build Operation ID is?
        // Because it knows which worker the event is coming from and maintains a mapping from worker to operation ID?
        outputEventListener.onOutput(event);
    }

    @Override
    public void sendOutputEvent(StyledTextOutputEvent event) {
        // TODO(daniel): See above.
        outputEventListener.onOutput(event);
    }

}
