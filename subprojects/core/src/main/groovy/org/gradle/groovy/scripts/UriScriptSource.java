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
import org.gradle.internal.resource.ResourceLocation;
import org.gradle.internal.resource.UriTextResource;

import java.io.File;
import java.net.URI;

/**
 * A {@link ScriptSource} which loads the script from a URI.
 */
public class UriScriptSource extends AbstractUriScriptSource {
    private final TextResource resource;

    public static ScriptSource file(String description, File sourceFile) {
        if (sourceFile.exists()) {
            return new UriScriptSource(description, sourceFile);
        } else {
            return new NonExistentFileScriptSource(description, sourceFile);
        }
    }

    public UriScriptSource(String description, File sourceFile) {
        resource = new UriTextResource(description, sourceFile);
    }

    public UriScriptSource(String description, URI sourceUri) {
        resource = new UriTextResource(description, sourceUri);
    }

    public TextResource getResource() {
        return resource;
    }

    public String getFileName() {
        ResourceLocation location = resource.getLocation();
        File sourceFile = location.getFile();
        URI sourceUri = location.getURI();
        return sourceFile != null ? sourceFile.getPath() : sourceUri.toString();
    }

    public String getDisplayName() {
        return resource.getDisplayName();
    }
}
