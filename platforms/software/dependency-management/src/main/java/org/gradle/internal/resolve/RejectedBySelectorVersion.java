/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.internal.resolve;

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;

public class RejectedBySelectorVersion extends RejectedVersion {
    private final VersionSelector rejectionSelector;

    public RejectedBySelectorVersion(ModuleComponentIdentifier id, VersionSelector rejectionSelector) {
        super(id);
        this.rejectionSelector = rejectionSelector;
    }

    public VersionSelector getRejectionSelector() {
        return rejectionSelector;
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        RejectedBySelectorVersion that = (RejectedBySelectorVersion) o;
        return getId().equals(that.getId());
    }
}
