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

import org.gradle.internal.classpath.types.InstrumentingTypeRegistry;
import org.gradle.internal.hash.Hasher;

import java.io.File;

/**
 * Classpath element transform factory. There are some differences when instrumenting classes to be loaded by the instrumenting agent, this interface encapsulates them.
 */
public interface ClasspathElementTransformFactory {
    /**
     * Updates jar/directory content hash to account for the transform produced by this factory.
     *
     * @param hasher the hasher to modify
     */
    void applyConfigurationTo(Hasher hasher);

    /**
     * Returns the transformation to be applied to the given jar/directory.
     *
     * @param file the jar/directory to transform
     * @param classTransform the transform that will be applied to every class
     * @param typeRegistry the registry of type hierarchies
     * @return the transformation that will transform the file upon request.
     */
    ClasspathElementTransform createTransformer(File file, ClassTransform classTransform, InstrumentingTypeRegistry typeRegistry);
}
