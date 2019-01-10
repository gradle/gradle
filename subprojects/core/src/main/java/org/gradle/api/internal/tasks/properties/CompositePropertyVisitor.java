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

import org.gradle.api.internal.tasks.TaskDestroyablePropertySpec;
import org.gradle.api.internal.tasks.TaskLocalStatePropertySpec;
import org.gradle.api.internal.tasks.ValidatingValue;
import org.gradle.api.tasks.FileNormalizer;

public class CompositePropertyVisitor implements PropertyVisitor {
    private final PropertyVisitor[] visitors;

    public CompositePropertyVisitor(PropertyVisitor... visitors) {
        this.visitors = visitors;
    }

    @Override
    public boolean visitOutputFilePropertiesOnly() {
        for (PropertyVisitor visitor : visitors) {
            if (!visitor.visitOutputFilePropertiesOnly()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void visitInputFileProperty(String propertyName, boolean optional, boolean skipWhenEmpty, Class<? extends FileNormalizer> fileNormalizer, ValidatingValue value, FilePropertyType filePropertyType) {
        for (PropertyVisitor visitor : visitors) {
            visitor.visitInputFileProperty(propertyName, optional, skipWhenEmpty, fileNormalizer, value, filePropertyType);
        }
    }

    @Override
    public void visitInputProperty(String propertyName, ValidatingValue value, boolean optional) {
        for (PropertyVisitor visitor : visitors) {
            visitor.visitInputProperty(propertyName, value, optional);
        }
    }

    @Override
    public void visitOutputFileProperty(String propertyName, boolean optional, ValidatingValue value, OutputFilePropertyType filePropertyType) {
        for (PropertyVisitor visitor : visitors) {
            visitor.visitOutputFileProperty(propertyName, optional, value, filePropertyType);
        }
    }

    @Override
    public void visitDestroyableProperty(TaskDestroyablePropertySpec destroyableProperty) {
        for (PropertyVisitor visitor : visitors) {
            visitor.visitDestroyableProperty(destroyableProperty);
        }
    }

    @Override
    public void visitLocalStateProperty(TaskLocalStatePropertySpec localStateProperty) {
        for (PropertyVisitor visitor : visitors) {
            visitor.visitLocalStateProperty(localStateProperty);
        }
    }
}
