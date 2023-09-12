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

import org.gradle.internal.classpath.ClasspathBuilder;
import org.gradle.internal.classpath.ClasspathEntryVisitor;
import org.gradle.internal.classpath.ClasspathWalker;
import org.gradle.internal.classpath.types.InstrumentingTypeRegistry;

import java.io.File;
import java.io.IOException;

import static org.gradle.internal.classpath.transforms.MrJarUtils.isInUnsupportedMrJarVersionedDirectory;

/**
 * Transformation for legacy instrumentation when transformed JARs are part of the classpath.
 * This is still used when TestKit and TAPI run Gradle in embedded or debug mode.
 * <p>
 * This transformation filters out not yet supported versioned directories of the multi-release JARs.
 */
public class MultiReleaseJarTransformForLegacy extends BaseJarTransform {
    public MultiReleaseJarTransformForLegacy(File source, ClasspathBuilder classpathBuilder, ClasspathWalker classpathWalker, InstrumentingTypeRegistry typeRegistry, ClassTransform transform) {
        super(source, classpathBuilder, classpathWalker, typeRegistry, transform);
    }

    @Override
    protected void processClassFile(ClasspathBuilder.EntryBuilder builder, ClasspathEntryVisitor.Entry classEntry) throws IOException {
        if (!isInUnsupportedMrJarVersionedDirectory(classEntry)) {
            super.processClassFile(builder, classEntry);
        }
    }

    @Override
    protected void processResource(ClasspathBuilder.EntryBuilder builder, ClasspathEntryVisitor.Entry resourceEntry) throws IOException {
        // The entries should only be filtered out if we're transforming the proper multi-release JAR.
        // Otherwise, even if the entry path looks like it is inside the versioned directory, it may still be accessed as a
        // resource.
        // Of course, user code can try to access resources inside versioned directories with full paths anyway, but that's
        // a tradeoff we're making.
        if (!isInUnsupportedMrJarVersionedDirectory(resourceEntry)) {
            super.processResource(builder, resourceEntry);
        }
    }
}
