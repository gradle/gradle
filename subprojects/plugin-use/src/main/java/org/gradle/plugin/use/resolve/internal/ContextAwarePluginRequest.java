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
package org.gradle.plugin.use.resolve.internal;

import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.use.PluginId;

import javax.annotation.Nullable;
import java.io.File;
import java.net.URI;

import static org.gradle.plugin.management.internal.DefaultPluginRequest.*;

public class ContextAwarePluginRequest implements PluginRequestInternal {

    private final PluginRequestInternal delegate;
    private final PluginRequestResolutionContext context;

    public ContextAwarePluginRequest(PluginRequestInternal delegate, PluginRequestResolutionContext context) {
        this.delegate = delegate;
        this.context = context;
    }

    @Nullable
    public URI getScriptUri() {
        if (isScriptRequest()) {
            return null;
        }
        return getRequestingScriptUri().resolve(getScript());
    }

    private boolean isScriptRequest() {
        return getScript() == null;
    }

    private URI getRequestingScriptUri() {
        URI uriLocation = context.getRequestingScriptLocation().getURI();
        File fileLocation = context.getRequestingScriptLocation().getFile();
        if (uriLocation != null) {
            return uriLocation;
        } else if (fileLocation != null) {
            return fileLocation.toURI();
        } else {
            // Shouldn't harm as the only known cases are for empty scripts that can't have any plugins block!
            throw new UnsupportedOperationException("Requesting script URI cannot be computed from a synthetic ScriptSource");
        }
    }

    @Override
    public String getDisplayName() {
        if (isScriptRequest()) {
            return scriptRequestDisplayName();
        }
        return delegate.getDisplayName();
    }

    private String scriptRequestDisplayName() {
        return buildDisplayName(getId(), getRelativeScriptUri().toString(), getVersion(), getModule(), isApply());
    }

    @Nullable
    URI getRelativeScriptUri() {
        URI scriptUri = getScriptUri();
        if (scriptUri == null) {
            return null;
        }
        return context.getBuildRootDir().toURI().relativize(scriptUri);
    }

    @Nullable
    @Override
    public PluginId getId() {
        return delegate.getId();
    }

    @Nullable
    @Override
    public String getVersion() {
        return delegate.getVersion();
    }

    @Nullable
    @Override
    public String getScript() {
        return delegate.getScript();
    }

    @Nullable
    @Override
    public ModuleVersionSelector getModule() {
        return delegate.getModule();
    }

    @Override
    public boolean isApply() {
        return delegate.isApply();
    }

    @Override
    public int getRequestingScriptLineNumber() {
        return delegate.getRequestingScriptLineNumber();
    }

    @Override
    public String getRequestingScriptDisplayName() {
        return delegate.getRequestingScriptDisplayName();
    }
}
