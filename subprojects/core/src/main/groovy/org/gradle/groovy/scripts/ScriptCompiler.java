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
package org.gradle.groovy.scripts;

import org.codehaus.groovy.ast.ClassNode;
import org.gradle.api.Action;
import org.gradle.groovy.scripts.internal.CompileOperation;

/**
 * Compiles a script into a {@code Script} object.
 */
public interface ScriptCompiler {

    /**
     * Compiles the script into a {@code Script} object of the given type.
     *
     * @return a {@code ScriptRunner} for the script.
     * @throws ScriptCompilationException On compilation failure.
     */
    <T extends Script, M> ScriptRunner<T, M> compile(Class<T> scriptType, CompileOperation<M> extractingTransformer, ClassLoader classloader, Action<? super ClassNode> verifier);
}
