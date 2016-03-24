/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import groovy.lang.Script;
import org.codehaus.groovy.ast.ClassNode;
import org.gradle.api.Action;
import org.gradle.api.internal.changedetection.state.FileSnapshotter;
import org.gradle.api.internal.initialization.loadercache.ClassLoaderId;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.Cast;
import org.gradle.internal.hash.HashValue;

public class CrossBuildInMemoryCachingScriptClassCache {
    private final Cache<ScriptCacheKey, CachedCompiledScript> cachedCompiledScripts = CacheBuilder.newBuilder().maximumSize(100).recordStats().build();
    private final FileSnapshotter snapshotter;

    public CrossBuildInMemoryCachingScriptClassCache(FileSnapshotter snapshotter) {
        this.snapshotter = snapshotter;
    }

    public <T extends Script, M> CompiledScript<T, M> getOrCompile(ScriptSource source, ClassLoader classLoader, ClassLoaderId classLoaderId, CompileOperation<M> operation, Class<T> scriptBaseClass, Action<? super ClassNode> verifier, ScriptClassCompiler delegate) {
        ScriptCacheKey key = new ScriptCacheKey(source.getClassName(), classLoader, operation.getId());
        CachedCompiledScript cached = cachedCompiledScripts.getIfPresent(key);
        HashValue hash = snapshotter.snapshot(source.getResource()).getHash();
        if (cached != null) {
            if (hash.equals(cached.hash)) {
                return Cast.uncheckedCast(cached.compiledScript);
            }
        }
        CompiledScript<T, M> compiledScript = delegate.compile(source, classLoader, classLoaderId, operation, scriptBaseClass, verifier);
        cachedCompiledScripts.put(key, new CachedCompiledScript(hash, compiledScript));
        return compiledScript;
    }

    private static class CachedCompiledScript {
        private final HashValue hash;
        private final CompiledScript<?, ?> compiledScript;

        private CachedCompiledScript(HashValue hash, CompiledScript<?, ?> compiledScript) {
            this.hash = hash;
            this.compiledScript = compiledScript;
        }
    }

}
