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
import org.gradle.api.internal.initialization.loadercache.ClassLoaderId;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.Cast;

import java.util.Map;

public class CachingScriptClassCompiler implements ScriptClassCompiler {
    private final Map<Key, CompiledScript<?, ?>> cachedCompiledScripts = Maps.newHashMap();
    private final ScriptClassCompiler scriptClassCompiler;

    public CachingScriptClassCompiler(ScriptClassCompiler scriptClassCompiler) {
        this.scriptClassCompiler = scriptClassCompiler;
    }

    @Override
    public <T extends Script, M> CompiledScript<T, M> compile(ScriptSource source, ClassLoader classLoader, final ClassLoaderId classLoaderId, CompileOperation<M> operation, String classpathClosureName, Class<T> scriptBaseClass, Action<? super ClassNode> verifier) {
        Key key = new Key(source.getClassName(), classLoader, operation.getId(), scriptBaseClass.getName());
        CompiledScript<T, M> compiledScript = Cast.uncheckedCast(cachedCompiledScripts.get(key));
        if (compiledScript == null) {
            compiledScript = scriptClassCompiler.compile(source, classLoader, classLoaderId, operation, classpathClosureName, scriptBaseClass, verifier);
            cachedCompiledScripts.put(key, compiledScript);
        }
        return compiledScript;
    }

    private static class Key {
        private final String className;
        private final ClassLoader classLoader;
        private final String transformerId;
        private final String baseClassName;

        public Key(String className, ClassLoader classLoader, String transformerId, String baseClassName) {
            this.className = className;
            this.classLoader = classLoader;
            this.transformerId = transformerId;
            this.baseClassName = baseClassName;
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

            return baseClassName.equals(key.baseClassName)
                    && classLoader.equals(key.classLoader)
                    && className.equals(key.className)
                    && transformerId.equals(key.transformerId);
        }

        @Override
        public int hashCode() {
            int result = className.hashCode();
            result = 31 * result + classLoader.hashCode();
            result = 31 * result + transformerId.hashCode();
            result = 31 * result + baseClassName.hashCode();
            return result;
        }
    }

}
