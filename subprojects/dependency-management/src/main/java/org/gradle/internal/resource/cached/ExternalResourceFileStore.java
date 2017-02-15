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
package org.gradle.internal.resource.cached;

import org.gradle.api.Transformer;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.internal.resource.local.GroupedAndNamedUniqueFileStore;
import org.gradle.internal.resource.local.PathKeyFileStore;

import java.io.File;

public class ExternalResourceFileStore extends GroupedAndNamedUniqueFileStore<String> {

    private static final Transformer<String, String> GROUPER = new Transformer<String, String>() {
        @Override
        public String transform(String s) {
            return "" + (Math.abs(s.hashCode()) % 10);
        }
    };

    private static final Transformer<String, String> NAMER = new Transformer<String, String>() {
        @Override
        public String transform(String s) {
            return "" + (Math.abs(s.substring(0, 8).hashCode()) % 10);
        }
    };

    public ExternalResourceFileStore(File baseDir, TemporaryFileProvider tmpProvider) {
        super(new PathKeyFileStore(baseDir), tmpProvider, GROUPER, NAMER);
    }

    protected String toPath(String key, String checksumPart) {
        String group = GROUPER.transform(key);
        String name = NAMER.transform(key);

        return group + "/" + name + "/" + key;
    }

}
