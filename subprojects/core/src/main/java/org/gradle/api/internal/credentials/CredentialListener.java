/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.credentials;

import org.gradle.api.internal.TaskInternal;
import org.gradle.internal.service.scopes.EventScope;
import org.gradle.internal.service.scopes.Scopes;

/**
 * Collects issues to be reported to users.
 */
@EventScope(Scopes.Build.class)
public interface CredentialListener {
    /**
     * Signals a safety issue with credentials.
     *
     * @param locationSpecificReason a location-specific user-facing summary of the issue
     * @param task a reference to the task
     */
    void onUnsafeCredentials(String locationSpecificReason, TaskInternal task);
}
