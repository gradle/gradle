/*
 * Copyright 2026 Gradle and contributors.
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

package org.gradle.api.internal.provenance;

import org.jspecify.annotations.Nullable;

/** Report-local failed operation; never an accepted occurrence or an authority token. */
public final class FailedOperation {
    private final String operation;
    @Nullable
    private final Attribution attribution;

    public FailedOperation(String operation, @Nullable Attribution attribution) {
        this.operation = operation;
        this.attribution = attribution;
    }

    public String getOperation() {
        return operation;
    }

    @Nullable
    public Attribution getAttribution() {
        return attribution;
    }
}
