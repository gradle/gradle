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

package org.gradle.groovy.scripts.internal;

import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.hash.ContentHasherFactory;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.resource.TextResource;

import java.io.File;

public class DefaultScriptSourceHasher implements ScriptSourceHasher {
    private final FileHasher fileHasher;
    private final ContentHasherFactory contentHasherFactory;

    public DefaultScriptSourceHasher(FileHasher fileHasher, ContentHasherFactory contentHasherFactory) {
        this.fileHasher = fileHasher;
        this.contentHasherFactory = contentHasherFactory;
    }

    @Override
    public HashCode hash(ScriptSource scriptSource) {
        TextResource resource = scriptSource.getResource();
        File file = resource.getFile();
        if (file != null) {
            return fileHasher.hash(file);
        }
        Hasher hasher = contentHasherFactory.create();
        hasher.putString(resource.getText());
        return hasher.hash();
    }
}
