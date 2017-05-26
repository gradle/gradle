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

package org.gradle.internal.operations;

public class BuildOperationIdentifierPreservingRunnable implements Runnable {
    private final Runnable delegate;
    private final Object buildOperationId;

    public BuildOperationIdentifierPreservingRunnable(Runnable delegate) {
        this(delegate, BuildOperationIdentifierRegistry.getCurrentOperationIdentifier());
    }

    BuildOperationIdentifierPreservingRunnable(Runnable delegate, Object buildOperationId) {
        this.delegate = delegate;
        this.buildOperationId = buildOperationId;
    }

    @Override
    public void run() {
        if (buildOperationId == null) {
            delegate.run();
        } else {
            BuildOperationIdentifierRegistry.setCurrentOperationIdentifier(buildOperationId);
            try {
                delegate.run();
            } finally {
                BuildOperationIdentifierRegistry.clearCurrentOperationIdentifier();
            }
        }
    }
}
