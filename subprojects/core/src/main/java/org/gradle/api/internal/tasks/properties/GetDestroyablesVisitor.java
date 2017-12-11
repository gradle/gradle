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

package org.gradle.api.internal.tasks.properties;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.internal.file.PathToFileResolver;

import java.util.ArrayList;
import java.util.List;

public class GetDestroyablesVisitor extends PropertyVisitor.Adapter {
    private final String beanName;
    private final PathToFileResolver resolver;
    private List<Object> destroyables = new ArrayList<Object>();

    public GetDestroyablesVisitor(String beanName, PathToFileResolver resolver) {
        this.beanName = beanName;
        this.resolver = resolver;
    }

    @Override
    public void visitDestroyableProperty(Object value) {
        destroyables.add(value);
    }

    public List<Object> getDestroyables() {
        return destroyables;
    }

    public FileCollection getFiles() {
        return new DefaultConfigurableFileCollection(beanName + " destroy files", resolver, null, destroyables);
    }
}
