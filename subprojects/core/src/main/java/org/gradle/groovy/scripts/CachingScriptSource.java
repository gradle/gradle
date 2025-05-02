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
package org.gradle.groovy.scripts;

import org.gradle.internal.resource.CachingTextResource;
import org.gradle.internal.resource.TextResource;

public class CachingScriptSource extends DelegatingScriptSource {
    private final TextResource resource;

    public static ScriptSource of(ScriptSource source) {
        if (source.getResource().isContentCached()) {
            return source;
        }
        return new CachingScriptSource(source);
    }

    private CachingScriptSource(ScriptSource source) {
        super(source);
        resource = new CachingTextResource(source.getResource());
    }

    @Override
    public TextResource getResource() {
        return resource;
    }
}
