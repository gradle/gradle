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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Transformer;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.internal.resource.local.GroupedAndNamedUniqueFileStore;
import org.gradle.internal.resource.local.UniquePathKeyFileStore;

import java.io.File;

public class ExternalResourceFileStore extends GroupedAndNamedUniqueFileStore<String> {

    private static final Transformer<String, String> GROUPER = new Transformer<String, String>() {
        @Override
        public String transform(String s) {
            return String.valueOf(Math.abs(s.hashCode()) % 100);
        }
    };

    private static final Transformer<String, String> NAMER = new Transformer<String, String>() {
        @Override
        public String transform(String s) {
            return StringUtils.substringAfterLast(s, "/");
        }
    };

    public ExternalResourceFileStore(File baseDir, TemporaryFileProvider tmpProvider) {
        super(new UniquePathKeyFileStore(baseDir), tmpProvider, GROUPER, NAMER);
    }
}
