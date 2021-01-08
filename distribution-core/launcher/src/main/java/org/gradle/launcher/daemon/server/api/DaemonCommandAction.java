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
package org.gradle.launcher.daemon.server.api;

/**
 * An action that operations as part of a command execution.
 * <p>
 * Implementations must be multiple use and threadsafe.
 */
public interface DaemonCommandAction {

    /**
     * Executes this action.
     * <p>
     * The execution object is a kind of continuation and also carries the “result” of the action.
     * For example, if an exception arises as part of actioning the command, the exception should be
     * set on the execution object and not thrown. The implication of this is that any exceptions
     * thrown by DaemonCommandAction implementations are programming errors in the implementation
     * and not something like a build failure if the daemon command is to run a build.
     */
    void execute(DaemonCommandExecution execution);
}
