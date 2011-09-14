/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.launcher.daemon.server.exec;

import org.gradle.logging.internal.OutputEvent;
import org.gradle.logging.internal.OutputEventListener;
import org.gradle.logging.internal.LoggingOutputInternal;

/**
 * Registers a logging event listener to forward any output back to the client.
 */
public class ForwardOutput implements DaemonCommandAction {
    
    private final LoggingOutputInternal loggingOutput;
    
    public ForwardOutput(LoggingOutputInternal loggingOutput) {
        this.loggingOutput = loggingOutput;
    }
    
    public void execute(final DaemonCommandExecution execution) {
        OutputEventListener listener = new OutputEventListener() {
            public void onOutput(OutputEvent event) {
                try {
                    execution.getConnection().dispatch(event);
                } catch (Exception e) {
                    //Ignore. It means the client has disconnected so no point sending him any log output.
                    //we should be checking if client still listens elsewhere anyway.
                }
            }
        };

        loggingOutput.addOutputEventListener(listener);
        execution.proceed();
        loggingOutput.removeOutputEventListener(listener);
    }
}
