/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.platform.base.internal;

import org.gradle.api.Named;
import org.gradle.api.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;

public interface BinaryNamingScheme {
    String getBinaryName();

    String getTaskName(@Nullable String verb);

    String getTaskName(@Nullable String verb, @Nullable String target);

    /**
     * Returns a directory that can be used for outputs for this binary.
     */
    File getOutputDirectory(File baseDir);

    /**
     * Returns a directory that can be used for outputs of the given type for this binary.
     */
    File getOutputDirectory(File baseDir, String outputType);

    String getDescription();

    List<String> getVariantDimensions();

    /**
     * Creates a copy of this scheme, replacing the component name.
     */
    BinaryNamingScheme withComponentName(String componentName);

    /**
     * Creates a copy of this scheme, replacing the role. The 'role' refers to the role that the binary plays within its component.
     */
    BinaryNamingScheme withRole(String role, boolean isMain);

    /**
     * Creates a copy of this scheme, replacing the binary type.
     */
    BinaryNamingScheme withBinaryType(String type);

    /**
     * Creates a copy of this scheme, specifying a binary name. This overrides the default binary name that would be generated from the other attributes.
     */
    BinaryNamingScheme withBinaryName(String name);

    /**
     * Creates a copy of this scheme, <em>adding</em> a variant dimension.
     */
    BinaryNamingScheme withVariantDimension(String dimension);

    /**
     * Creates a copy of this scheme, <em>adding</em> a variant dimension if required.
     */
    <T extends Named> BinaryNamingScheme withVariantDimension(T value, Collection<? extends T> allValuesForAxis);
}
