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

package org.gradle.nativecode.base.internal;

import org.gradle.api.DomainObjectSet;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Nullable;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.language.base.internal.TaskNamerForBinaries;
import org.gradle.nativecode.base.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class DefaultNativeBinary implements NativeBinaryInternal {
    private final DomainObjectSet<NativeDependencySet> libs = new DefaultDomainObjectSet<NativeDependencySet>(NativeDependencySet.class);
    private final DefaultTaskDependency buildDependencies = new DefaultTaskDependency();
    private final ArrayList<Object> compilerArgs = new ArrayList<Object>();
    private final ArrayList<Object> linkerArgs = new ArrayList<Object>();
    private final TaskNamerForBinaries namer;
    private final String name;
    private File outputFile;

    protected DefaultNativeBinary(NativeComponent owner, String name) {
        this.name = name;
        namer = new TaskNamerForBinaries(name);
        owner.getBinaries().add(this);
    }

    public String getName() {
        return name;
    }

    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    // TODO:DAZ This should allow source sets to be extended on a per-binary basis
    public DomainObjectSet<SourceSet> getSourceSets() {
        return getComponent().getSourceSets();
    }

    public List<Object> getCompilerArgs() {
        return compilerArgs;
    }

    public void compilerArgs(Object... args) {
        Collections.addAll(compilerArgs, args);
    }

    public List<Object> getLinkerArgs() {
        return linkerArgs;
    }

    public void linkerArgs(Object... args) {
        Collections.addAll(linkerArgs, args);
    }

    public String getTaskName(@Nullable String verb) {
        return namer.getTaskName(verb, null);
    }

    public DomainObjectSet<NativeDependencySet> getLibs() {
        return libs;
    }

    public void lib(Object notation) {
        if (notation instanceof Library) {
            LibraryInternal library = (LibraryInternal) notation;
            libs.add(library.getDefaultBinary().getAsNativeDependencySet());
        } else if (notation instanceof LibraryBinary) {
            LibraryBinary libraryBinary = (LibraryBinary) notation;
            libs.add(libraryBinary.getAsNativeDependencySet());
        } else if (notation instanceof NativeDependencySet) {
            libs.add((NativeDependencySet) notation);
        } else {
            throw new InvalidUserDataException(String.format("Cannot convert %s to a library dependency.", notation));
        }
    }

    public void builtBy(Object... tasks) {
        buildDependencies.add(tasks);
    }

    public TaskDependency getBuildDependencies() {
        return buildDependencies;
    }

    public abstract String getOutputFileName();

    protected abstract NativeComponent getComponent();

}
