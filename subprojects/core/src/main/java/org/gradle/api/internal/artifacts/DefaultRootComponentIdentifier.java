/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.component.RootComponentIdentifier;

/**
 * Default implementation of {@link RootComponentIdentifier}.
 */
public class DefaultRootComponentIdentifier implements RootComponentIdentifier {

    /**
     * A unique value representing the component instance identified by this identifier.
     */
    private final long instanceId;

    public DefaultRootComponentIdentifier(long instanceId) {
        this.instanceId = instanceId;
    }

    public long getInstanceId() {
        return instanceId;
    }

    @Override
    public String getDisplayName() {
        return "root";
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultRootComponentIdentifier that = (DefaultRootComponentIdentifier) o;
        return instanceId == that.instanceId;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(instanceId);
    }

}
