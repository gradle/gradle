/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.launcher;

import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.initialization.ParsedCommandLine;
import org.gradle.launcher.protocol.Build;
import org.gradle.logging.internal.OutputEventListener;
import org.gradle.messaging.remote.internal.Connection;

public class DaemonBuildAction extends DaemonClientAction implements Action<ExecutionListener> {
    private final DaemonConnector connector;
    private final StartParameter startParameter;
    private final ParsedCommandLine args;

    public DaemonBuildAction(OutputEventListener outputEventListener, DaemonConnector connector, StartParameter startParameter, ParsedCommandLine args) {
        super(outputEventListener);
        this.connector = connector;
        this.startParameter = startParameter;
        this.args = args;
    }

    public void execute(ExecutionListener executionListener) {
        Connection<Object> connection = connector.connect(startParameter);
        run(new Build(startParameter.getCurrentDir(), args), connection, executionListener);
    }
}
