/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.launcher.daemon.protocol.Cancel;
import org.gradle.launcher.daemon.protocol.Success;

/**
 * If the command is a Stop, asks the daemon to stop asynchronously and does not proceed with execution.
 */
public class HandleCancel implements DaemonCommandAction {
    public void execute(DaemonCommandExecution execution) {
        if (execution.getCommand() instanceof Cancel) {
            System.out.println("HandelCancel processing");
            execution.getDaemonStateControl().cancelBuild();
            execution.getConnection().completed(new Success(null));
        } else {
            System.out.println("HandelCancel calls proceed");
            execution.proceed();
        }
    }
}