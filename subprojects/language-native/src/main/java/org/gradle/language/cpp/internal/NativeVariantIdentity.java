/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.language.cpp.internal;

import com.google.common.collect.Sets;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.component.ComponentWithCoordinates;
import org.gradle.api.component.SoftwareComponentVariant;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.api.provider.Provider;
import org.gradle.nativeplatform.Linkage;
import org.gradle.nativeplatform.TargetMachine;
import org.gradle.util.internal.GUtil;

import java.util.Set;

public class NativeVariantIdentity implements SoftwareComponentInternal, ComponentWithCoordinates {
    private final String name;
    private final Provider<String> baseName;
    private final Provider<String> group;
    private final Provider<String> version;
    private final boolean debuggable;
    private final boolean optimized;
    private final TargetMachine targetMachine;
    private final SoftwareComponentVariant linkVariant;
    private final SoftwareComponentVariant runtimeVariant;
    private final Linkage linkage;
    private final Set<UsageContext> variants;

    public NativeVariantIdentity(String name, Provider<String> baseName, Provider<String> group, Provider<String> version, boolean debuggable, boolean optimized, TargetMachine targetMachine, UsageContext linkVariant, UsageContext runtimeVariant) {
        this(name, baseName, group, version, debuggable, optimized, targetMachine, linkVariant, runtimeVariant, null);
    }

    public NativeVariantIdentity(String name, Provider<String> baseName, Provider<String> group, Provider<String> version, boolean debuggable, boolean optimized, TargetMachine targetMachine, UsageContext linkVariant, UsageContext runtimeVariant, Linkage linkage) {
        this.name = name;
        this.baseName = baseName;
        this.group = group;
        this.version = version;
        this.debuggable = debuggable;
        this.optimized = optimized;
        this.targetMachine = targetMachine;
        this.linkVariant = linkVariant;
        this.runtimeVariant = runtimeVariant;
        this.linkage = linkage;
        this.variants = Sets.newLinkedHashSet();
        if (linkVariant != null) {
            variants.add(linkVariant);
        }
        if (runtimeVariant !=null) {
            variants.add(runtimeVariant);
        }
    }

    public boolean isDebuggable() {
        return debuggable;
    }

    public boolean isOptimized() {
        return optimized;
    }

    public TargetMachine getTargetMachine() {
        return targetMachine;
    }

    public Linkage getLinkage() {
        return linkage;
    }

    @Override
    public ModuleVersionIdentifier getCoordinates() {
        return DefaultModuleVersionIdentifier.newId(group.get(), baseName.get() + "_" + GUtil.toWords(name, '_'), version.get());
    }

    @Override
    public Set<? extends UsageContext> getUsages() {
        return variants;
    }

    @Override
    public String getName() {
        return name;
    }

    public SoftwareComponentVariant getRuntimeVariant() {
        return runtimeVariant;
    }

    public SoftwareComponentVariant getLinkVariant() {
        return linkVariant;
    }
}
