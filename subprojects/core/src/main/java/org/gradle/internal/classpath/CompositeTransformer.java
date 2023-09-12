/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.classpath;

import org.gradle.api.file.RelativePath;
import org.gradle.internal.Pair;
import org.gradle.internal.classpath.transforms.ClassTransform;
import org.gradle.internal.hash.Hasher;
import org.objectweb.asm.ClassVisitor;

import java.io.IOException;

public class CompositeTransformer implements ClassTransform {
    private final ClassTransform first;
    private final ClassTransform second;

    public CompositeTransformer(ClassTransform first, ClassTransform second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public void applyConfigurationTo(Hasher hasher) {
        first.applyConfigurationTo(hasher);
        second.applyConfigurationTo(hasher);
    }

    @Override
    public Pair<RelativePath, ClassVisitor> apply(ClasspathEntryVisitor.Entry entry, ClassVisitor visitor, ClassData classData) throws IOException {
        return first.apply(entry, second.apply(entry, visitor, classData).right, classData);
    }
}
