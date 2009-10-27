/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file;

import org.gradle.api.internal.ChainingTransformer;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RelativePath;
import org.gradle.api.Transformer;
import groovy.lang.Closure;

public class DefaultCopyDestinationMapper implements CopyDestinationMapper {
    private final ChainingTransformer<String> nameTransformer = new ChainingTransformer<String>(String.class);

    public RelativePath getPath(FileTreeElement element) {
        RelativePath original = element.getRelativePath();
        String transformedName = nameTransformer.transform(original.getLastName());
        return new RelativePath(original.isFile(), original.getParent(), transformedName);
    }

    public void add(Transformer<String> transformer) {
        nameTransformer.add(transformer);
    }

    public void add(Closure transformer) {
        nameTransformer.add(transformer);
    }
}
