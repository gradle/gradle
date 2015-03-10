/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.internal.resource.Resource;
import org.gradle.internal.resource.UriResource;

import java.io.File;

public class NonExistentFileScriptSource extends AbstractUriScriptSource {

    private final Resource resource;
    private final File file;

    public NonExistentFileScriptSource(String description, File file) {
        this.resource = new EmptyFileResource(description, file);
        this.file = file;
    }

    @Override
    public Resource getResource() {
        return resource;
    }

    @Override
    public String getFileName() {
        return file.getAbsolutePath();
    }

    @Override
    public String getDisplayName() {
        return resource.getDisplayName();
    }

    private static class EmptyFileResource extends UriResource {
        public EmptyFileResource(String description, File sourceFile) {
            super(description, sourceFile);
        }

        @Override
        public String getText() {
            return "";
        }

        @Override
        public boolean getExists() {
            return false;
        }
    }
}
