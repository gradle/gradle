/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.groovy.scripts.internal;

import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.Transformer;

/**
 * The initial compilation pass of a script, which only looks at its imports and its top-level script blocks.
 * It compiles a reduced source, so that the pass is not recompiled when only the rest of the script changes.
 */
public class InitialPassCompileOperation extends NoDataCompileOperation {

    /**
     * Compiles the whole script in the initial pass, as before. For comparing performance while this is a prototype.
     */
    private static final String COMPILE_WHOLE_SCRIPT_PROPERTY = "org.gradle.internal.groovy.compile-whole-script-in-initial-pass";

    public InitialPassCompileOperation(String id, String stage, Transformer transformer) {
        super(id, stage, transformer);
    }

    @Override
    public ScriptSource getSourceToCompile(ScriptSource source) {
        if (Boolean.getBoolean(COMPILE_WHOLE_SCRIPT_PROPERTY)) {
            return source;
        }
        return InitialPassScriptSource.of(source);
    }
}
