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

import org.codehaus.groovy.classgen.Verifier;

/**
 * Compiles a script into a {@code Script} object.
 */
public interface ScriptCompiler {

    /**
     * Sets the parent classloader for the script. Can be null, defaults to the context classloader.
     */
    ScriptCompiler setClassloader(ClassLoader classloader);

    /**
     * Sets the transformer to use to compile the script. Can be null, in which case no transformations are applied to
     * the script.
     */
    ScriptCompiler setTransformer(Transformer transformer);

    ScriptCompiler setVerifier(Verifier verifier);

    /**
     * Compiles the script into a {@code Script} object of the given type. 
     *
     * @returns a {@code ScriptRunner} for the script.
     * @throws ScriptCompilationException On compilation failure.
     */
    <T extends Script> ScriptRunner<T> compile(Class<T> scriptType) throws ScriptCompilationException;
}
