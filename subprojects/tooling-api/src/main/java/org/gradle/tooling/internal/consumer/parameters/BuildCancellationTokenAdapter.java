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

package org.gradle.tooling.internal.consumer.parameters;

import org.gradle.initialization.BuildCancellationToken;
import org.gradle.tooling.internal.protocol.InternalCancellationToken;

public class BuildCancellationTokenAdapter implements InternalCancellationToken {
    private final BuildCancellationToken cancellationToken;

    public BuildCancellationTokenAdapter(BuildCancellationToken cancellationToken) {
        this.cancellationToken = cancellationToken;
    }

    public boolean isCancellationRequested() {
        return cancellationToken.isCancellationRequested();
    }

    public boolean addCallback(Runnable cancellationHandler) {
        return cancellationToken.addCallback(cancellationHandler);
    }

    public void removeCallback(Runnable cancellationHandler) {
        cancellationToken.removeCallback(cancellationHandler);
    }
}
