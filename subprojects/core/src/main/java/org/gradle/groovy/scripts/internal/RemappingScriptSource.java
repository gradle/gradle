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

import org.gradle.groovy.scripts.DelegatingScriptSource;
import org.gradle.groovy.scripts.ScriptSource;

/**
 * When stored into the persistent store, we want the script to be created with a predictable class name: we don't want the path of the script
 * to be used in the generated class name because the same contents can be used for scripts found in different paths. Since we are storing
 * each build file in a separate directory based on the hash of the script contents, we can use the same file name
 * for each class. When the script is going to be loaded from the cache, we will get this class and set the path to the script using {@link
 * org.gradle.groovy.scripts.Script#setScriptSource(org.gradle.groovy.scripts.ScriptSource)}
 */
public class RemappingScriptSource extends DelegatingScriptSource {
    public final static String MAPPED_SCRIPT = "_BuildScript_";

    public RemappingScriptSource(ScriptSource source) {
        super(source);
    }

    @Override
    public String getClassName() {
        return MAPPED_SCRIPT;
    }

}
