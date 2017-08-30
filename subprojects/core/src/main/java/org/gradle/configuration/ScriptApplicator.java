/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.configuration;

import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.groovy.scripts.ScriptSource;

import javax.annotation.Nullable;

public interface ScriptApplicator {

    class Extensions {
        public static void applyScriptTo(Object target, ScriptApplicator applicator, ScriptSource scriptSource, ClassLoaderScope targetScope, ClassLoaderScope baseScope, boolean topLevelScript) {
            applicator.applyTo(target, scriptSource, null, targetScope, baseScope, topLevelScript);
        }
    }

    void applyTo(Object target, ScriptSource scriptSource, @Nullable ScriptHandler scriptHandler, ClassLoaderScope targetScope, ClassLoaderScope baseScope, boolean topLevelScript);
    void applyTo(Iterable<Object> target, ScriptSource scriptSource, ClassLoaderScope targetScope, ClassLoaderScope baseScope);
}


