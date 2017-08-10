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

class DefaultDeploymentStatus implements Deployment.Status {
    private final boolean changed;
    private final Throwable failure;

    DefaultDeploymentStatus(boolean changed, Throwable failure) {
        this.changed = changed;
        this.failure = failure;
    }

    @Override
    public Throwable getFailure() {
        return failure;
    }

    @Override
    public boolean hasChanged() {
        return changed;
    }

    @Override
    public String toString() {
        return "DefaultDeploymentStatus{"
            + "changed=" + changed
            + ", failure=" + failure
            + '}';
    }
}
