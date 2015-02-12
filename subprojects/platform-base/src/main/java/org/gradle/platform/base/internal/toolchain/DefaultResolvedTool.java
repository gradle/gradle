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

package org.gradle.platform.base.internal.toolchain;

import org.gradle.util.TreeVisitor;

public class DefaultResolvedTool<T> implements ResolvedTool<T> {
    private final ToolProvider provider;
    private final Class<T> toolType;

    public DefaultResolvedTool(ToolProvider provider, Class<T> toolType) {
        this.provider = provider;
        this.toolType = toolType;
    }

    @Override
    public T get() {
        return provider.get(toolType);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void explain(TreeVisitor<? super String> visitor) {
    }
}
