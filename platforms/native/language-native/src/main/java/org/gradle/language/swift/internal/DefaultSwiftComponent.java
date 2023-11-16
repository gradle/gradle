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
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.internal.Cast;
import org.gradle.language.internal.DefaultBinaryCollection;
import org.gradle.language.nativeplatform.internal.ComponentWithNames;
import org.gradle.language.nativeplatform.internal.DefaultNativeComponent;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.language.swift.SwiftBinary;
import org.gradle.language.swift.SwiftComponent;
import org.gradle.language.swift.SwiftVersion;
import org.gradle.nativeplatform.TargetMachine;

import java.util.Collections;

public abstract class DefaultSwiftComponent<T extends SwiftBinary> extends DefaultNativeComponent implements SwiftComponent, ComponentWithNames {
    private final DefaultBinaryCollection<T> binaries;
    private final FileCollection swiftSource;
    private final Property<String> module;
    private final String name;
    private final Names names;
    private final Property<SwiftVersion> sourceCompatibility;
    private final SetProperty<TargetMachine> targetMachines;

    public DefaultSwiftComponent(String name, ObjectFactory objectFactory) {
        this(name, SwiftBinary.class, objectFactory);
    }

    public DefaultSwiftComponent(String name, Class<? extends SwiftBinary> binaryType, ObjectFactory objectFactory) {
        super(objectFactory);
        this.name = name;
        this.swiftSource = createSourceView("src/"+ name + "/swift", Collections.singletonList("swift"));
        this.module = objectFactory.property(String.class);
        this.sourceCompatibility = objectFactory.property(SwiftVersion.class);

        this.names = Names.of(name);
        this.binaries = Cast.uncheckedCast(objectFactory.newInstance(DefaultBinaryCollection.class, binaryType));
        this.targetMachines = objectFactory.setProperty(TargetMachine.class);
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
    public Property<String> getModule() {
        return module;
    }

    @Override
    public FileCollection getSwiftSource() {
        return swiftSource;
    }

    @Override
    public DefaultBinaryCollection<T> getBinaries() {
        return binaries;
    }

    @Override
    public Property<SwiftVersion> getSourceCompatibility() {
        return sourceCompatibility;
    }

    @Override
    public SetProperty<TargetMachine> getTargetMachines() {
        return targetMachines;
    }
}
