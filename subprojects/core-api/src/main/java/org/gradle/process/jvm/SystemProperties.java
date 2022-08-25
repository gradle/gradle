/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.process.jvm;

import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import javax.inject.Inject;

public interface SystemProperties {
    interface SystemPropertyValue<T extends Provider<?>> {
        T getValue();
    }
    interface SystemPropertyStringValue extends SystemPropertyValue<Property<String>> {
        @Input
        Property<String> getValue();
    }
    interface SystemPropertyInputFile extends SystemPropertyValue<RegularFileProperty> {
        @InputFile
        @PathSensitive(PathSensitivity.RELATIVE)
        RegularFileProperty getValue();
    }
    interface SystemPropertyInputDirectory extends SystemPropertyValue<DirectoryProperty> {
        @InputFiles
        @PathSensitive(PathSensitivity.RELATIVE)
        DirectoryProperty getValue();
    }
    // TODO: Also output file?
    interface SystemPropertyIgnoredValue extends SystemPropertyValue<Property<String>> {
        @Internal
        Property<String> getValue();
    }

    @Inject
    ObjectFactory getObjectFactory();

    @Nested
    MapProperty<String, SystemPropertyValue> getSystemProperties();

    default void systemProperty(String name, String value) {
        systemProperty(name, value(value));
    }

    default <T extends SystemPropertyValue> void systemProperty(String name, T value) {
        getSystemProperties().put(name, value);
    }

    default SystemPropertyInputFile inputFile(Provider<? extends RegularFile> file) {
        SystemPropertyInputFile inputFile = getObjectFactory().newInstance(SystemPropertyInputFile.class);
        inputFile.getValue().value(file);
        return inputFile;
    }
    default SystemPropertyInputDirectory inputDirectory(Provider<? extends Directory> file) {
        SystemPropertyInputDirectory inputDirectory = getObjectFactory().newInstance(SystemPropertyInputDirectory.class);
        inputDirectory.getValue().value(file);
        return inputDirectory;
    }

    default SystemPropertyIgnoredValue ignoredValue(Provider<String> value) {
        SystemPropertyIgnoredValue ignoredValue = getObjectFactory().newInstance(SystemPropertyIgnoredValue.class);
        ignoredValue.getValue().set(value);
        return ignoredValue;
    }

    default SystemPropertyStringValue value(String value) {
        SystemPropertyStringValue valueProperty = getObjectFactory().newInstance(SystemPropertyStringValue.class);
        valueProperty.getValue().set(value);
        return valueProperty;
    }

    default SystemPropertyStringValue value(Provider<String> value) {
        SystemPropertyStringValue valueProperty = getObjectFactory().newInstance(SystemPropertyStringValue.class);
        valueProperty.getValue().set(value);
        return valueProperty;
    }
}
