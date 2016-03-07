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
import org.gradle.api.internal.changedetection.state.CachingFileSnapshotter;
import org.gradle.api.internal.initialization.loadercache.ClassLoaderId;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.Cast;
import org.gradle.internal.hash.HashUtil;

import java.io.File;
import java.util.Arrays;

public class CrossBuildInMemoryCachingScriptClassCache {
    private final Cache<Key, CachedCompiledScript> cachedCompiledScripts = CacheBuilder.newBuilder().maximumSize(100).recordStats().build();
    private final CachingFileSnapshotter snapshotter;

    public CrossBuildInMemoryCachingScriptClassCache(CachingFileSnapshotter snapshotter) {
        this.snapshotter = snapshotter;
    }

    public <T extends Script, M> CompiledScript<T, M> getOrCompile(ScriptSource source, ClassLoader classLoader, ClassLoaderId classLoaderId, CompileOperation<M> operation, Class<T> scriptBaseClass, Action<? super ClassNode> verifier, ScriptClassCompiler delegate) {
        Key key = new Key(source.getClassName(), classLoader, operation.getId());
        CachedCompiledScript cached = cachedCompiledScripts.getIfPresent(key);
        byte[] hash = hashFor(source);
        if (cached != null) {
            if (Arrays.equals(hash, cached.hash)) {
                return Cast.uncheckedCast(cached.compiledScript);
            }
        }
        CompiledScript<T, M> compiledScript = delegate.compile(source, classLoader, classLoaderId, operation, scriptBaseClass, verifier);
        cachedCompiledScripts.put(key, new CachedCompiledScript(hash, compiledScript));
        return compiledScript;
    }

    private byte[] hashFor(ScriptSource source) {
        File file = source.getResource().getFile();
        String hash;
        if (file != null && file.exists()) {
            CachingFileSnapshotter.FileInfo snapshot = snapshotter.snapshot(file);
            return snapshot.getHash();
        }
        return HashUtil.createHash(source.getResource().getText(), "md5").asByteArray();
    }

    private static class Key {
        private final String className;
        private final ClassLoader classLoader;
        private final String dslId;

        public Key(String className, ClassLoader classLoader, String dslId) {
            this.className = className;
            this.classLoader = classLoader;
            this.dslId = dslId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Key key = (Key) o;

            return classLoader.equals(key.classLoader)
                && className.equals(key.className)
                && dslId.equals(key.dslId);
        }

        @Override
        public int hashCode() {
            int result = className.hashCode();
            result = 31 * result + classLoader.hashCode();
            result = 31 * result + dslId.hashCode();
            return result;
        }
    }

    private static class CachedCompiledScript {
        private final byte[] hash;
        private final CompiledScript<?, ?> compiledScript;

        private CachedCompiledScript(byte[] hash, CompiledScript<?, ?> compiledScript) {
            this.hash = hash;
            this.compiledScript = compiledScript;
        }
    }

}
