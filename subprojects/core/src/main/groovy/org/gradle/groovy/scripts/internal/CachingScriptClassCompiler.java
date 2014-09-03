/*
 * Copyright 2010 the original author or authors.
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

import java.util.*;

public class CachingScriptClassCompiler implements ScriptClassCompiler {
    private final Map<Collection<Object>, Class<?>> cachedClasses = new HashMap<Collection<Object>, Class<?>>();
    private final ScriptClassCompiler scriptClassCompiler;

    public CachingScriptClassCompiler(ScriptClassCompiler scriptClassCompiler) {
        this.scriptClassCompiler = scriptClassCompiler;
    }

    public <T extends Script> Class<? extends T> compile(ScriptSource source, ClassLoader classLoader, Transformer transformer, Class<T> scriptBaseClass, Verifier verifier) {
        List<Object> key = Arrays.asList(source.getClassName(), classLoader, transformer.getId(), scriptBaseClass.getName());
        Class<?> c = cachedClasses.get(key);
        if (c == null) {
            c = scriptClassCompiler.compile(source, classLoader, transformer, scriptBaseClass, verifier);
            cachedClasses.put(key, c);
        }
        return c.asSubclass(scriptBaseClass);
    }
}
