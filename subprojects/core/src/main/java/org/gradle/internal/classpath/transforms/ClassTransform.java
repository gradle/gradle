/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.classpath.transforms;

import org.gradle.api.file.RelativePath;
import org.gradle.internal.Pair;
import org.gradle.internal.classpath.ClassData;
import org.gradle.internal.classpath.ClasspathEntryVisitor;
import org.gradle.internal.hash.Hasher;
import org.objectweb.asm.ClassVisitor;

import java.io.IOException;

/**
 * Transform that modifies a class
 */
public interface ClassTransform {
    void applyConfigurationTo(Hasher hasher);

    Pair<RelativePath, ClassVisitor> apply(ClasspathEntryVisitor.Entry entry, ClassVisitor visitor, ClassData classData) throws IOException;
}
