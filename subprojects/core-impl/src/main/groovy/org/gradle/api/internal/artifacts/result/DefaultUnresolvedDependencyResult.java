/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.result;

import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;

/**
 * by Szczepan Faber, created at: 7/26/12
 */
public class DefaultUnresolvedDependencyResult implements UnresolvedDependencyResult {

    private final ModuleVersionSelector requested;
    private final Exception failure;
    private final DefaultResolvedModuleVersionResult from;

    public DefaultUnresolvedDependencyResult(ModuleVersionSelector requested, Exception failure, DefaultResolvedModuleVersionResult from) {
        assert requested != null;
        assert failure != null;
        assert from != null;

        this.from = from;
        this.failure = failure;
        this.requested = requested;
    }

    public ModuleVersionSelector getRequested() {
        return requested;
    }

    public DefaultResolvedModuleVersionResult getFrom() {
        return from;
    }

    @Override
    public String toString() {
        return requested.getGroup() + ":" + requested.getName() + ":" + requested.getVersion() + " - " + failure.getMessage();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultUnresolvedDependencyResult)) {
            return false;
        }

        DefaultUnresolvedDependencyResult that = (DefaultUnresolvedDependencyResult) o;

        if (!from.equals(that.from)) {
            return false;
        }
        if (!requested.equals(that.requested)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = requested.hashCode();
        result = 31 * result + from.hashCode();
        return result;
    }
}
