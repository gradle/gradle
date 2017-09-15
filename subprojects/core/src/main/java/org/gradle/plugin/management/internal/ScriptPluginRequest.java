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
package org.gradle.plugin.management.internal;

import org.gradle.groovy.scripts.ScriptSource;

import java.net.URI;

import static com.google.common.base.Preconditions.checkNotNull;

public class ScriptPluginRequest extends AbstractPluginRequest {

    private final URI script;

    public ScriptPluginRequest(ScriptSource requestingScriptSource, int requestingScriptLineNumber, URI script) {
        this(requestingScriptSource.getDisplayName(), requestingScriptLineNumber, script);
    }

    public ScriptPluginRequest(String requestingScriptDisplayName, int requestingScriptLineNumber, URI script) {
        super(requestingScriptDisplayName, requestingScriptLineNumber);
        this.script = checkNotNull(script);
    }

    @Override
    public URI getScript() {
        return script;
    }

    @Override
    public String getDisplayName() {
        return buildDisplayName(script);
    }

    public static String buildDisplayName(URI script) {
        return "script '" + script + "'";
    }
}
