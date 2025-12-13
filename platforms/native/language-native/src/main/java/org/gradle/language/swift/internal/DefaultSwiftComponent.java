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

package org.gradle.language.swift.internal;

import org.gradle.api.file.FileCollection;
import org.gradle.internal.Cast;
import org.gradle.language.internal.DefaultBinaryCollection;
import org.gradle.language.nativeplatform.internal.ComponentWithNames;
import org.gradle.language.nativeplatform.internal.DefaultNativeComponent;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.language.swift.SwiftBinary;
import org.gradle.language.swift.SwiftComponent;

import java.util.Collections;

public abstract class DefaultSwiftComponent<T extends SwiftBinary> extends DefaultNativeComponent implements SwiftComponent, ComponentWithNames {
    private final DefaultBinaryCollection<T> binaries;
    private final FileCollection swiftSource;
    private final String name;
    private final Names names;

    public DefaultSwiftComponent(String name) {
        this(name, SwiftBinary.class);
    }

    public DefaultSwiftComponent(String name, Class<? extends SwiftBinary> binaryType) {
        this.name = name;
        this.swiftSource = createSourceView("src/"+ name + "/swift", Collections.singletonList("swift"));
        this.names = Names.of(name);
        this.binaries = Cast.uncheckedCast(getObjectFactory().newInstance(DefaultBinaryCollection.class, binaryType));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Names getNames() {
        return names;
    }

    @Override
    public FileCollection getSwiftSource() {
        return swiftSource;
    }

    @Override
    public DefaultBinaryCollection<T> getBinaries() {
        return binaries;
    }
}
