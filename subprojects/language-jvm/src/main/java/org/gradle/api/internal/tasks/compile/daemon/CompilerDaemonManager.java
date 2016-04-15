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
package org.gradle.api.internal.tasks.compile.daemon;

import net.jcip.annotations.ThreadSafe;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.language.base.internal.compile.CompileSpec;

import java.io.File;

/**
 * Controls the lifecycle of the compiler daemon and provides access to it.
 */
@ThreadSafe
public class CompilerDaemonManager implements CompilerDaemonFactory, Stoppable {

    private CompilerClientsManager clientsManager;

    public CompilerDaemonManager(CompilerClientsManager clientsManager) {
        this.clientsManager = clientsManager;
    }

    @Override
    public CompilerDaemon getDaemon(final File workingDir, final DaemonForkOptions forkOptions) {
        return new CompilerDaemon() {
            public <T extends CompileSpec> CompileResult execute(org.gradle.language.base.internal.compile.Compiler<T> compiler, T spec) {
                CompilerDaemonClient client = clientsManager.reserveIdleClient(forkOptions);
                if (client == null) {
                    client = clientsManager.reserveNewClient(workingDir, forkOptions);
                }
                try {
                    return client.execute(compiler, spec);
                } finally {
                    clientsManager.release(client);
                }
            }
        };
    }

    @Override
    public void stop() {
        clientsManager.stop();
    }
}
