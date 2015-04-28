/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks.mirah;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import org.gradle.language.mirah.tasks.BaseMirahCompileOptions;

/**
 * Options for Mirah compilation.
 */
public class MirahCompileOptions extends BaseMirahCompileOptions {
    private boolean fork;

    /**
     * Whether to run the Mirah compiler in a separate process. Defaults to {@code false}
     * for the Ant based compiler ({@code useAnt = true}), and to {@code true} for the Zinc
     * based compiler ({@code useAnt = false}).
     */
    public boolean isFork() {
        return fork;
    }

    public void setFork(boolean fork) {
        this.fork = fork;
    }
}
