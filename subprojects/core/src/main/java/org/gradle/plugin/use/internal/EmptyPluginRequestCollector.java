/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugin.use.internal;

import com.google.common.collect.Lists;
import org.gradle.plugin.use.PluginDependencySpec;

import java.util.List;

/**
 * Used in contexts where the `plugins` block is currently not allowed, e.g. settings and init scripts.
 */
public class EmptyPluginRequestCollector implements PluginRequestCollector {
    @Override
    public List<TargetedPluginRequest> getRequests() {
        return Lists.newArrayList();
    }

    @Override
    public PluginDependencySpec id(String id) {
        throw new UnsupportedOperationException();
    }
}
