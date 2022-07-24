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

import org.gradle.launcher.daemon.configuration.DaemonServerConfiguration;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.protocol.Command;

import java.util.LinkedList;
import java.util.List;

/**
 * A continuation style object used to model the execution of a command.
 * <p>
 * Facilitates processing “chains”, making it easier to break up processing logic into discrete {@link org.gradle.launcher.daemon.server.api.DaemonCommandAction actions}.
 * <p>
 * The given actions will be executed in the order given to the constructor, and should use the {@link #proceed()} method to allow
 * the next action to run. If an action does not call {@code proceed()}, it will be the last action that executes.
 */
public class DaemonCommandExecution {

    private final DaemonServerConfiguration configuration;
    final private DaemonConnection connection;
    final private Command command;
    final private DaemonContext daemonContext;
    final private DaemonStateControl daemonStateControl;
    final private LinkedList<DaemonCommandAction> actions;

    private Object result;

    public DaemonCommandExecution(DaemonServerConfiguration configuration, DaemonConnection connection, Command command, DaemonContext daemonContext, DaemonStateControl daemonStateControl, List<DaemonCommandAction> actions) {
        this.configuration = configuration;
        this.connection = connection;
        this.command = command;
        this.daemonContext = daemonContext;
        this.daemonStateControl = daemonStateControl;

        this.actions = new LinkedList<DaemonCommandAction>(actions);
    }

    public DaemonConnection getConnection() {
        return connection;
    }

    /**
     * The command to execute.
     * <p>
     * If the client disconnects before sending a command, this <b>will</b> be {@code null}.
     */
    public Command getCommand() {
        return command;
    }

    public DaemonContext getDaemonContext() {
        return daemonContext;
    }

    public DaemonStateControl getDaemonStateControl() {
        return daemonStateControl;
    }

    /**
     * Sets what is to be considered the result of executing the command.
     * <p>
     * This may be called multiple times to do things like wrap the result in another type.
     */
    public void setResult(Object result) {
        this.result = result;
    }

    /**
     * The currently nominated result for the execution.
     *
     * <p>May be null if no action has set the result yet.
     */
    public Object getResult() {
        return this.result;
    }

    /**
     * Continues (or starts) execution.
     * <p>
     * Each action should call this method if it determines that execution should continue.
     *
     * @return true if execution did occur, false if this execution has already occurred.
     */
    public boolean proceed() {
        if (actions.isEmpty()) {
            return false;
        } else {
            actions.removeFirst().execute(this);
            return true;
        }
    }

    /**
     * Informs if this execution is of single-use-daemon type
     */
    public boolean isSingleUseDaemon() {
        return configuration.isSingleUse();
    }

    @Override
    public String toString() {
        return String.format("DaemonCommandExecution[command = %s, connection = %s]", command, connection);
    }
}
