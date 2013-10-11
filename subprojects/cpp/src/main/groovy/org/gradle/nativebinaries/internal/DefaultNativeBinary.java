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

package org.gradle.nativebinaries.internal;

import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.notations.api.NotationParser;
import org.gradle.language.DependentSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.AbstractBuildableModelElement;
import org.gradle.language.base.internal.BinaryNamingScheme;
import org.gradle.language.base.internal.DefaultBinaryNamingScheme;
import org.gradle.nativebinaries.*;

import java.io.File;
import java.util.*;

public abstract class DefaultNativeBinary extends AbstractBuildableModelElement implements NativeBinaryInternal {
    private final NotationParser<Set<LanguageSourceSet>> sourcesNotationParser = SourceSetNotationParser.parser();
    private final Set<? super Object> libs = new LinkedHashSet<Object>();
    private final DomainObjectSet<LanguageSourceSet> source = new DefaultDomainObjectSet<LanguageSourceSet>(LanguageSourceSet.class);
    private final DefaultTool linker = new DefaultTool();
    private final NativeBinaryTasks tasks = new DefaultNativeBinaryTasks();
    private final BinaryNamingScheme namingScheme;
    private final Flavor flavor;
    private final ToolChainInternal toolChain;
    private final Platform targetPlatform;
    private final BuildType buildType;
    private File outputFile;
    private boolean buildable;

    protected DefaultNativeBinary(NativeComponent owner, Flavor flavor, ToolChainInternal toolChain, Platform targetPlatform, BuildType buildType, DefaultBinaryNamingScheme namingScheme) {
        this.namingScheme = namingScheme;
        this.flavor = flavor;
        this.toolChain = toolChain;
        this.targetPlatform = targetPlatform;
        this.buildType = buildType;
        this.buildable = true;
        owner.getSource().all(new Action<LanguageSourceSet>() {
            public void execute(LanguageSourceSet sourceSet) {
                source.add(sourceSet);
            }
        });
    }

    @Override
    public String toString() {
        return namingScheme.getDescription();
    }

    public String getName() {
        return namingScheme.getLifecycleTaskName();
    }

    public Flavor getFlavor() {
        return flavor;
    }

    public ToolChainInternal getToolChain() {
        return toolChain;
    }

    public Platform getTargetPlatform() {
        return targetPlatform;
    }

    public BuildType getBuildType() {
        return buildType;
    }

    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    public DomainObjectSet<LanguageSourceSet> getSource() {
        return source;
    }

    public void source(Object sources) {
        source.addAll(sourcesNotationParser.parseNotation(sources));
    }

    public Tool getLinker() {
        return linker;
    }

    public NativeBinaryTasks getTasks() {
        return tasks;
    }

    public BinaryNamingScheme getNamingScheme() {
        return namingScheme;
    }

    // TODO:DAZ Dependency resolution shouldn't be done in the model
    public Collection<NativeDependencySet> getLibs() {
        return getLibs(source.withType(DependentSourceSet.class));
    }

    public Collection<NativeDependencySet> getLibs(DependentSourceSet sourceSet) {
        return getLibs(Collections.singleton(sourceSet));
    }

    private Collection<NativeDependencySet> getLibs(Collection<? extends DependentSourceSet> sourceSets) {
        Set<? super Object> allLibs = new HashSet<Object>(libs);
        for (DependentSourceSet dependentSourceSet : sourceSets) {
            allLibs.addAll(dependentSourceSet.getLibs());
        }
        return new NativeDependencyResolver().resolve(this, allLibs);
    }

    public void lib(Object notation) {
        libs.add(notation);
    }

    public boolean isBuildable() {
        return buildable;
    }

    public void setBuildable(boolean buildable) {
        this.buildable = buildable;
    }

    public abstract String getOutputFileName();

}
