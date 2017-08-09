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

package org.gradle.deployment.internal;

import org.gradle.internal.UncheckedException;

import java.util.concurrent.atomic.AtomicBoolean;

public class SimpleBlockingDeployment implements DeploymentInternal {
    private final AtomicBoolean block = new AtomicBoolean();

    private boolean changed;
    private Throwable failure;

    @Override
    public Status status() {
        synchronized (block) {
            while (block.get()) {
                try {
                    block.wait();
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
            Status result = new DefaultDeploymentStatus(changed, failure);
            changed = false;
            return result;
        }
    }

    @Override
    public void outOfDate() {
        synchronized (block) {
            changed = true;
            failure = null;
            block.set(true);
            block.notifyAll();
        }
    }

    @Override
    public void upToDate(Throwable failure) {
        synchronized (block) {
            this.failure = failure;
            block.set(false);
            block.notifyAll();
        }
    }
}
