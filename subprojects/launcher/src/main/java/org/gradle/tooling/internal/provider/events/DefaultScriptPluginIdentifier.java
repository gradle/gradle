/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.tooling.internal.provider.events;

import org.gradle.tooling.internal.protocol.events.InternalScriptPluginIdentifier;

import java.io.Serializable;
import java.net.URI;

public class DefaultScriptPluginIdentifier implements InternalScriptPluginIdentifier, Serializable {

    private final String displayName;
    private final URI uri;

    public DefaultScriptPluginIdentifier(String displayName, URI uri) {
        this.displayName = displayName;
        this.uri = uri;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultScriptPluginIdentifier that = (DefaultScriptPluginIdentifier) o;
        return uri.equals(that.uri);
    }

    @Override
    public int hashCode() {
        return uri.hashCode();
    }

}
