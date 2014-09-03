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
package org.gradle.groovy.scripts.internal;

import groovy.lang.Script;
import org.codehaus.groovy.classgen.Verifier;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.Transformer;

import java.io.File;

public interface ScriptCompilationHandler {
    void compileToDir(ScriptSource source, ClassLoader classLoader, File scriptCacheDir, Transformer transformer,
                      Class<? extends Script> scriptBaseClass, Verifier verifier);

    <T extends Script> Class<? extends T> loadFromDir(ScriptSource source, ClassLoader classLoader, File scriptCacheDir,
                                       Class<T> scriptBaseClass);
}
