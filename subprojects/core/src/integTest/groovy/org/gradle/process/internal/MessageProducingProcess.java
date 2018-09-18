/*
 * Copyright 2017 the original author or authors.
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
import java.util.concurrent.CountDownLatch;

public class MessageProducingProcess implements Action<WorkerProcessContext>, Serializable {
    public void execute(WorkerProcessContext workerProcessContext) {
        final CountDownLatch latch = new CountDownLatch(1);
        new Thread(new Runnable() {
            public void run() {
                latch.countDown();
                for (int i=0; i<1000; i++) {
                    System.out.println("message " + i + "...");
                }
            }
        }).start();
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
