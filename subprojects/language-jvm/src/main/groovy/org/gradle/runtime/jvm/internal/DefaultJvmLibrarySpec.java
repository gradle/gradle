/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.runtime.jvm.internal;

import org.gradle.api.DomainObjectSet;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.runtime.base.TransformationFileType;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetContainer;
import org.gradle.runtime.jvm.JvmByteCode;
import org.gradle.runtime.jvm.JvmResources;
import org.gradle.runtime.base.ComponentSpecIdentifier;
import org.gradle.runtime.base.internal.ComponentSpecInternal;
import org.gradle.runtime.jvm.JvmLibraryBinarySpec;
import org.gradle.runtime.jvm.JvmLibrarySpec;

import java.util.HashSet;
import java.util.Set;

public class DefaultJvmLibrarySpec implements JvmLibrarySpec, ComponentSpecInternal {
    private final LanguageSourceSetContainer sourceSets = new LanguageSourceSetContainer();
    private final FunctionalSourceSet mainSourceSet;
    private final ComponentSpecIdentifier identifier;
    private final DomainObjectSet<JvmLibraryBinarySpec> binaries = new DefaultDomainObjectSet<JvmLibraryBinarySpec>(JvmLibraryBinarySpec.class);
    private final Set<Class<? extends TransformationFileType>> languageOutputs = new HashSet<Class<? extends TransformationFileType>>();
    public DefaultJvmLibrarySpec(ComponentSpecIdentifier identifier, FunctionalSourceSet mainSourceSet) {
        this.identifier = identifier;
        this.mainSourceSet = mainSourceSet;
        sourceSets.addMainSources(mainSourceSet);
        this.languageOutputs.add(JvmResources.class);
        this.languageOutputs.add(JvmByteCode.class);
    }

    public String getName() {
        return identifier.getName();
    }

    public String getProjectPath() {
        return identifier.getProjectPath();
    }

    public String getDisplayName() {
        return String.format("JVM library '%s'", getName());
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public DomainObjectSet<LanguageSourceSet> getSource() {
        return sourceSets;
    }

    public void source(Object source) {
        sourceSets.source(source);
    }

    public DomainObjectSet<JvmLibraryBinarySpec> getBinaries() {
        return binaries;
    }

    public FunctionalSourceSet getMainSource() {
        return mainSourceSet;
    }

    public Set<Class<? extends TransformationFileType>> getInputTypes() {
        return languageOutputs;
    }
}
