/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.provider;

import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.FileSystemLocationProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import javax.annotation.Nullable;
import java.io.File;

/**
 * This file is used to add <a href="https://groovy-lang.org/metaprogramming.html#_extension_modules">Groovy Extension Module</a> to {@link org.gradle.api.provider.Property}.
 */
@SuppressWarnings("unused")
public class PropertyExtensionModule {

    public static <T> void call(Property<T> property, @Nullable T value) {
        property.set(value);
    }

    public static <T> void call(Property<T> property, Provider<? extends T> value) {
        property.set(value);
    }

    public static void call(FileSystemLocationProperty<? extends FileSystemLocation> property, File value) {
        property.set(value);
    }
}
