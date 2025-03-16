/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.launcher.daemon.client;

import org.gradle.internal.UncheckedException;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.remote.internal.Connection;
import org.gradle.launcher.daemon.protocol.Command;
import org.gradle.launcher.daemon.protocol.Failure;
import org.gradle.launcher.daemon.protocol.Finished;
import org.gradle.launcher.daemon.protocol.InvalidateVirtualFileSystemAfterChange;
import org.gradle.launcher.daemon.protocol.Message;
import org.gradle.launcher.daemon.protocol.Result;
import org.gradle.launcher.daemon.registry.DaemonInfo;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.server.api.DaemonState;

import java.util.List;
import java.util.UUID;

import static org.gradle.launcher.daemon.server.api.DaemonState.Busy;
import static org.gradle.launcher.daemon.server.api.DaemonState.Canceled;
import static org.gradle.launcher.daemon.server.api.DaemonState.Idle;

public class NotifyDaemonAboutChangedPathsClient {
    private final DaemonConnector connector;
    private final IdGenerator<UUID> idGenerator;
    private final DaemonRegistry daemonRegistry;

    public NotifyDaemonAboutChangedPathsClient(DaemonConnector connector, IdGenerator<UUID> idGenerator, DaemonRegistry daemonRegistry) {
        this.connector = connector;
        this.idGenerator = idGenerator;
        this.daemonRegistry = daemonRegistry;
    }

    public void notifyDaemonsAboutChangedPaths(List<String> changedPaths) {
        for (DaemonInfo daemonInfo : daemonRegistry.getAll()) {
            DaemonState state = daemonInfo.getState();
            if (state == Idle || state == Busy || state == Canceled) {
                DaemonClientConnection connection = connector.maybeConnect(daemonInfo);
                if (connection == null) {
                    continue;
                }
                dispatch(connection, new InvalidateVirtualFileSystemAfterChange(changedPaths, idGenerator.generateId(), connection.getDaemon().getToken()));
            }
        }
    }

    private static void dispatch(Connection<Message> connection, Command command) {
        Throwable failure = null;
        try {
            connection.dispatch(command);
            Result<?> result = (Result) connection.receive();
            if (result instanceof Failure) {
                failure = ((Failure) result).getValue();
            }
            connection.dispatch(new Finished());
        } catch (Throwable e) {
            failure = e;
        }
        if (failure != null) {
            throw UncheckedException.throwAsUncheckedException(failure);
        }
    }
}
