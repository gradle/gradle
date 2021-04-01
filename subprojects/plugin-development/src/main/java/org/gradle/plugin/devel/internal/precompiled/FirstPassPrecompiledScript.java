/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.plugin.devel.internal.precompiled;

import groovy.lang.Closure;
import org.gradle.plugin.use.internal.PluginsAwareScript;

public abstract class FirstPassPrecompiledScript extends PluginsAwareScript {

    @SuppressWarnings("rawtypes")
    @Override
    public void buildscript(Closure configureClosure) {
        throw new IllegalStateException("The `buildscript` block is not supported in Groovy script plugins. Use the `plugins` block or project level dependencies instead.");
    }
}
