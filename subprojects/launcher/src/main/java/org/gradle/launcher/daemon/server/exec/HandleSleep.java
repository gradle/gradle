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

import org.gradle.launcher.daemon.protocol.Command;
import org.gradle.launcher.daemon.protocol.Sleep;

/**
 * Handles the special sleep command.
 * 
 * If a Sleep is received, processing does not proceed past this point.
 */
public class HandleSleep implements DaemonCommandAction {

    public void execute(DaemonCommandExecution execution) {
        Command command = execution.getCommand();
        if (command instanceof Sleep) {
            ((Sleep) command).run();
            execution.setResult("Command executed successfully: " + command);
            // don't proceed, don't need to go further
        } else {
            execution.proceed();
        }
    }

}
