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

import org.gradle.internal.resource.TextResource;
import org.gradle.internal.resource.StringTextResource;
import org.gradle.internal.hash.HashUtil;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * In-memory ScriptSource.
 */
public class StringScriptSource implements ScriptSource {
    private final TextResource resource;
    private final String scriptFileExtension;

    /**
     * Creates an in-memory ScriptSource for a <code>.gradle</code> script.
     *
     * @param description the description
     * @param content the content, may be null
     */
    public StringScriptSource(String description, String content) {
        this(description, content, ".gradle");
    }

    /**
     * Creates an in-memory ScriptSource for a script.
     *
     * @param description the description
     * @param content the content
     * @param scriptFileExtension the extension to be appended to the synthetic {@link #getFileName()}, must not be null
     */
    public StringScriptSource(String description, String content, String scriptFileExtension) {
        this.resource = new StringTextResource(description, content == null ? "" : content);
        this.scriptFileExtension = checkNotNull(scriptFileExtension);
    }

    public String getClassName() {
        return "script_" + HashUtil.createCompactMD5(resource.getText());
    }

    public TextResource getResource() {
        return resource;
    }

    public String getFileName() {
        return getClassName() + scriptFileExtension;
    }

    public String getDisplayName() {
        return resource.getDisplayName();
    }
}
