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

package org.gradle.swiftpm.internal;

import org.gradle.language.swift.SwiftVersion;
import org.gradle.swiftpm.Package;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.List;
import java.util.Set;

public class DefaultPackage implements Package, Serializable {
    private final Set<AbstractProduct> products;
    private final List<Dependency> dependencies;
    private final List<DefaultTarget> targets;
    private final SwiftVersion swiftLanguageVersion;

    public DefaultPackage(Set<AbstractProduct> products, List<DefaultTarget> targets, List<Dependency> dependencies, @Nullable SwiftVersion swiftLanguageVersion) {
        this.products = products;
        this.targets = targets;
        this.dependencies = dependencies;
        this.swiftLanguageVersion = swiftLanguageVersion;
    }

    public List<Dependency> getDependencies() {
        return dependencies;
    }

    @Nullable
    public SwiftVersion getSwiftLanguageVersion() {
        return swiftLanguageVersion;
    }

    @Override
    public Set<AbstractProduct> getProducts() {
        return products;
    }

    public List<DefaultTarget> getTargets() {
        return targets;
    }
}
