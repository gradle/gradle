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

package org.gradle.plugin.repository.internal;

import org.gradle.plugin.use.resolve.internal.PluginResolver;

abstract class AbstractPluginRepository implements PluginRepositoryInternal {

    private String lockReason;

    @Override
    public final PluginResolver asResolver() {
        lock("The repository has been used to resolve plugins");
        return internalAsResolver();
    }

    protected abstract PluginResolver internalAsResolver();

    @Override
    public void lock(String reason) {
        lockReason = reason;
    }

    protected final void checkMutable() {
        if (lockReason != null) {
            throw new IllegalStateException("Cannot mutate this plugin repository: " + lockReason);
        }
    }
}
