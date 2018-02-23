/*
 * Copyright 2007-2008 the original author or authors.
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

/**
 * A factory for script compilers.
 */
public interface ScriptCompilerFactory {
    /**
     * Creates a compiler for the given source. The returned compiler can be used to compile the script into various
     * different forms.
     *
     * @param source The script source.
     * @return a compiler which can be used to compiler the script.
     */
    ScriptCompiler createCompiler(ScriptSource source);
}
