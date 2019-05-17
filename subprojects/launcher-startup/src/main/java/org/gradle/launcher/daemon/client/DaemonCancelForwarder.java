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

package org.gradle.launcher.daemon.client;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.dispatch.Dispatch;
import org.gradle.launcher.daemon.protocol.Cancel;

public class DaemonCancelForwarder implements Stoppable {
    private static final Logger LOGGER = Logging.getLogger(DaemonCancelForwarder.class);

    private final Runnable cancellationCallback;
    private final BuildCancellationToken cancellationToken;

    public DaemonCancelForwarder(final Dispatch<? super Cancel> dispatch, BuildCancellationToken cancellationToken) {
        this.cancellationToken = cancellationToken;
        cancellationCallback = new Runnable() {
            @Override
            public void run() {
                LOGGER.info("Request daemon to cancel build...");
                dispatch.dispatch(new Cancel());
            }
        };
    }

    public void start() {
        cancellationToken.addCallback(cancellationCallback);
    }

    @Override
    public void stop() {
        cancellationToken.removeCallback(cancellationCallback);
    }
}
