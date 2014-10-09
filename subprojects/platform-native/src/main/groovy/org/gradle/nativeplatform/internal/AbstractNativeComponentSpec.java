/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.nativeplatform.internal;

import org.gradle.api.DomainObjectSet;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetContainer;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.NativeComponentSpec;
import org.gradle.nativeplatform.ObjectFile;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.ComponentSpecIdentifier;
import org.gradle.platform.base.TransformationFileType;
import org.gradle.platform.base.internal.ComponentSpecInternal;
import org.gradle.util.GUtil;

import java.util.HashSet;
import java.util.Set;

public abstract class AbstractNativeComponentSpec implements NativeComponentSpec, ComponentSpecInternal {
    private final FunctionalSourceSet mainSourceSet;
    private final LanguageSourceSetContainer sourceSets = new LanguageSourceSetContainer();
    private final ComponentSpecIdentifier id;
    private final DomainObjectSet<BinarySpec> binaries;
    private final DomainObjectSet<NativeBinarySpec> nativeBinaries;
    private final Set<Class<? extends TransformationFileType>> inputTypes = new HashSet<Class<? extends TransformationFileType>>();

    private String baseName;

    public AbstractNativeComponentSpec(ComponentSpecIdentifier id, FunctionalSourceSet mainSourceSet) {
        this.mainSourceSet = mainSourceSet;
        sourceSets.addMainSources(mainSourceSet);
        this.id = id;
        this.binaries = new DefaultDomainObjectSet<BinarySpec>(NativeBinarySpec.class);
        this.nativeBinaries = this.binaries.withType(NativeBinarySpec.class);
        this.inputTypes.add(ObjectFile.class);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public String getName() {
        return id.getName();
    }

    public String getProjectPath() {
        return id.getProjectPath();
    }

    public FunctionalSourceSet getMainSource() {
        return mainSourceSet;
    }

    public DomainObjectSet<LanguageSourceSet> getSource() {
        return sourceSets;
    }

    public void source(Object sources) {
        sourceSets.source(sources);
    }

    public DomainObjectSet<BinarySpec> getBinaries() {
        return binaries;
    }

    public DomainObjectSet<NativeBinarySpec> getNativeBinaries() {
        return nativeBinaries;
    }

    public String getBaseName() {
        return GUtil.elvis(baseName, getName());
    }

    public void setBaseName(String baseName) {
        this.baseName = baseName;
    }


    public Set<Class<? extends TransformationFileType>> getInputTypes() {
        return inputTypes;
    }
}