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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import org.gradle.util.UncheckedException;
import org.gradle.launcher.exec.ReportedException;

import org.gradle.launcher.daemon.protocol.Result;
import org.gradle.launcher.daemon.protocol.CommandComplete;

/**
 * Handles sending the result of the execution back to the client.
 * 
 * Likely to be the first thing in the pipeline.
 */
public class ReturnResult implements DaemonCommandAction {

    private static final Logger LOGGER = Logging.getLogger(ReturnResult.class);
    
    public void execute(DaemonCommandExecution execution) {
        execution.proceed();

        Object toReturn = null;
        Throwable exception = execution.getException();
        if (exception != null) {
            if (!(exception instanceof ReportedException)) {
                LOGGER.error("Could not execute build.", exception);
            }
            toReturn = new CommandComplete(UncheckedException.asUncheckedException(exception));
        } else {
            toReturn = new Result(execution.getResult());
        }

        execution.getConnection().dispatch(toReturn);
    }

}
