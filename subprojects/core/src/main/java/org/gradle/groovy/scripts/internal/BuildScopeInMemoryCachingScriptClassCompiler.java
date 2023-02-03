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
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.Cast;

import java.util.Map;

/**
 * This in-memory cache is responsible for caching compiled build scripts during a build.
 * If the compiled script is not found in this cache, it will try to find it in the global cache,
 * which will use the delegate script class compiler in case of a miss. The lookup in this cache is
 * more efficient than looking in the global cache, as we do not check the script's hash code here,
 * assuming that it did not change during the build.
 */
public class BuildScopeInMemoryCachingScriptClassCompiler implements ScriptClassCompiler {
    private final CrossBuildInMemoryCachingScriptClassCache cache;
    private final ScriptClassCompiler scriptClassCompiler;
    private final Map<ScriptCacheKey, CompiledScript<?, ?>> cachedCompiledScripts = Maps.newHashMap();

    public BuildScopeInMemoryCachingScriptClassCompiler(CrossBuildInMemoryCachingScriptClassCache cache, ScriptClassCompiler scriptClassCompiler) {
        this.cache = cache;
        this.scriptClassCompiler = scriptClassCompiler;
    }

    @Override
    public <T extends Script, M> CompiledScript<T, M> compile(ScriptSource source, ClassLoaderScope targetScope, CompileOperation<M> operation, Class<T> scriptBaseClass, Action<? super ClassNode> verifier) {
        ScriptCacheKey key = new ScriptCacheKey(source.getClassName(), targetScope.getExportClassLoader(), operation.getId());
        CompiledScript<T, M> compiledScript = Cast.uncheckedCast(cachedCompiledScripts.get(key));
        if (compiledScript == null) {
            compiledScript = cache.getOrCompile(source, targetScope, operation, scriptBaseClass, verifier, scriptClassCompiler);
            cachedCompiledScripts.put(key, compiledScript);
        }
        return compiledScript;
    }

}
