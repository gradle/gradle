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

import com.google.common.collect.Maps;
import groovy.lang.Script;
import org.codehaus.groovy.ast.ClassNode;
import org.gradle.api.Action;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.Cast;

import java.util.*;

public class CachingScriptClassCompiler implements ScriptClassCompiler {
    private final Map<Collection<Object>, CompiledScript<?>> cachedCompiledScripts = Maps.newHashMap();
    private final ScriptClassCompiler scriptClassCompiler;

    public CachingScriptClassCompiler(ScriptClassCompiler scriptClassCompiler) {
        this.scriptClassCompiler = scriptClassCompiler;
    }

    @Override
    public <T extends Script> CompiledScript<T> compile(ScriptSource source, ClassLoader classLoader, MetadataExtractingTransformer<?> extractingTransformer, String classpathClosureName, Class<T> scriptBaseClass, Action<? super ClassNode> verifier) {
        List<Object> key = Arrays.asList(source.getClassName(), classLoader, extractingTransformer.getTransformer().getId(), scriptBaseClass.getName());
        CompiledScript<T> compiledScript = Cast.uncheckedCast(cachedCompiledScripts.get(key));
        if (compiledScript == null) {
            compiledScript = scriptClassCompiler.compile(source, classLoader, extractingTransformer, classpathClosureName, scriptBaseClass, verifier);
            cachedCompiledScripts.put(key, compiledScript);
        }
        return compiledScript;
    }

}
