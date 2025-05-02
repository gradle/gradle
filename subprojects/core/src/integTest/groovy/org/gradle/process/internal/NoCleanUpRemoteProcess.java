/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.process.internal;

import org.gradle.api.Action;
import org.gradle.process.internal.worker.WorkerProcessContext;

import java.io.Serializable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class NoCleanUpRemoteProcess implements Action<WorkerProcessContext>, Serializable {
    public void execute(WorkerProcessContext workerProcessContext) {
        final Lock lock = new ReentrantLock();
        lock.lock();
        new Thread(new Runnable() {
            public void run() {
                lock.lock();
            }
        }).start();

        TestListenerInterface sender = workerProcessContext.getServerConnection().addOutgoing(TestListenerInterface.class);
        workerProcessContext.getServerConnection().connect();
        sender.send("message 1", 1);
        sender.send("message 2", 2);
    }
}
