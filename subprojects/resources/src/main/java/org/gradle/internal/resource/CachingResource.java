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

package org.gradle.internal.resource;

import java.io.File;
import java.net.URI;

public class CachingResource implements Resource {
    private final Resource resource;
    private String content;

    public CachingResource(Resource resource) {
        this.resource = resource;
    }

    public String getDisplayName() {
        return resource.getDisplayName();
    }

    public File getFile() {
        return resource.getFile();
    }

    public URI getURI() {
        return resource.getURI();
    }

    @Override
    public boolean isContentCheapToQuery() {
        return true;
    }

    @Override
    public boolean getExists() {
        maybeFetch();
        return content != null;
    }

    @Override
    public boolean getHasEmptyContent() {
        return content != null ? content.length() == 0 : resource.getHasEmptyContent();
    }

    @Override
    public String getText() {
        maybeFetch();
        return content;
    }

    private void maybeFetch() {
        if (content == null) {
            content = resource.getText();
        }
    }
}
