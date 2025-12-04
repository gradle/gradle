/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.testing.testengine.descriptor;

import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.ClasspathResourceSource;
import org.junit.platform.engine.support.descriptor.CompositeTestSource;
import org.junit.platform.engine.support.descriptor.DirectorySource;
import org.junit.platform.engine.support.descriptor.FilePosition;
import org.junit.platform.engine.support.descriptor.FileSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.engine.support.descriptor.PackageSource;

import java.io.File;
import java.util.Arrays;
import java.util.Optional;

public final class CustomSourceTestDescriptor extends AbstractTestDescriptor {
    private final File file;
    private final String name;

    public CustomSourceTestDescriptor(UniqueId parentId, File file, String name) {
        super(parentId.append("testDefinitionFile", file.getName()).append("testDefinition", name), file.getName() + " - " + name);
        this.file = file;
        this.name = name;
    }

    @Override
    public Type getType() {
        return Type.TEST;
    }

    @Override
    public boolean mayRegisterTests() {
        return false;
    }

    @Override
    public Optional<TestSource> getSource() {
        if ("noLocation".equals(name)) {
            return Optional.empty();
        } else if ("unknownLocation".equals(name)) {
            return Optional.of(new CustomTestSource());
        } else if ("fileLocationNoPos".equals(name)) {
            return Optional.of(FileSource.from(getFile()));
        } else if ("fileLocationOnlyLine".equals(name)) {
            return Optional.of(FileSource.from(getFile(), FilePosition.from(1)));
        } else if ("fileLocationLineAndCol".equals(name)) {
            return Optional.of(FileSource.from(getFile(), FilePosition.from(1, 2)));
        } else if ("directorySource".equals(name)) {
            return Optional.of(DirectorySource.from(new File(System.getProperty("user.home"))));
        } else if ("classpathResourceSourceNoPos".equals(name)) {
            return Optional.of(ClasspathResourceSource.from("SomeClass"));
        } else if ("classpathResourceSourceOnlyLine".equals(name)) {
            return Optional.of(ClasspathResourceSource.from("SomeClass", FilePosition.from(1)));
        } else if ("classpathResourceSourceLineAndCol".equals(name)) {
            return Optional.of(ClasspathResourceSource.from("SomeClass", FilePosition.from(1, 2)));
        } else if ("noArgMethodLocation".equals(name)) {
            return Optional.of(MethodSource.from("SomeClass", "someMethod"));
        } else if ("someArgMethodLocation".equals(name)) {
            return Optional.of(MethodSource.from("SomeClass", "someMethod", "SomeArg"));
        } else if ("packageLocation".equals(name)) {
            return Optional.of(PackageSource.from("some.package"));
        } else if ("unknownAndFileLocation".equals(name)) {
            return Optional.of(CompositeTestSource.from(
                Arrays.asList(
                    new CustomTestSource(),
                    FileSource.from(getFile())
                )
            ));
        }
        throw new RuntimeException("Cannot find source for " + toString());
    }

    public File getFile() {
        return file;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "Test[file=" + file.getName() + ", name=" + name + "]";
    }

    public static class CustomTestSource implements TestSource {

    }
}
