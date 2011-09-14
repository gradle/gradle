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

import org.gradle.launcher.daemon.protocol.Build;
import org.gradle.launcher.daemon.protocol.Command;

/**
 * Superclass template for actions that only work for Build.
 * 
 * If an action of this type receives a command that is not Build it will throw an exception.
 */
abstract public class BuildCommandOnly implements DaemonCommandAction {

    public void execute(DaemonCommandExecution execution) {
        Command command = execution.getCommand();
        if (!(command instanceof Build)) {
            throw new IllegalStateException(String.format("{} command action received a command that isn't Build (command is {}), this shouldn't happen", this.getClass(), command.getClass()));
        }

        doBuild(execution, (Build)command);
    }

    /**
     * Note that the build param is the same object as execution.getCommand(), just “pre casted”.
     */
    protected void doBuild(DaemonCommandExecution execution, Build build) {}
}