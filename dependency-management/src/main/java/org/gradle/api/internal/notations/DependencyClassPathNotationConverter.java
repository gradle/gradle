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
package org.gradle.api.internal.notations;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.artifacts.SelfResolvingDependency;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.artifacts.dependencies.DefaultSelfResolvingDependency;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.runtimeshaded.RuntimeShadedJarFactory;
import org.gradle.api.internal.runtimeshaded.RuntimeShadedJarType;
import org.gradle.internal.component.local.model.OpaqueComponentIdentifier;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationConvertResult;
import org.gradle.internal.typeconversion.NotationConverter;
import org.gradle.internal.typeconversion.TypeConversionException;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import static org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory.ClassPathNotation.GRADLE_API;
import static org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory.ClassPathNotation.GRADLE_TEST_KIT;
import static org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory.ClassPathNotation.LOCAL_GROOVY;

public class DependencyClassPathNotationConverter implements NotationConverter<DependencyFactory.ClassPathNotation, SelfResolvingDependency> {

    private final ClassPathRegistry classPathRegistry;
    private final Instantiator instantiator;
    private final FileCollectionFactory fileCollectionFactory;
    private final RuntimeShadedJarFactory runtimeShadedJarFactory;
    private final CurrentGradleInstallation currentGradleInstallation;
    private final ConcurrentMap<DependencyFactory.ClassPathNotation, SelfResolvingDependency> internCache = Maps.newConcurrentMap();

    public DependencyClassPathNotationConverter(
        Instantiator instantiator,
        ClassPathRegistry classPathRegistry,
        FileCollectionFactory fileCollectionFactory,
        RuntimeShadedJarFactory runtimeShadedJarFactory,
        CurrentGradleInstallation currentGradleInstallation) {
        this.instantiator = instantiator;
        this.classPathRegistry = classPathRegistry;
        this.fileCollectionFactory = fileCollectionFactory;
        this.runtimeShadedJarFactory = runtimeShadedJarFactory;
        this.currentGradleInstallation = currentGradleInstallation;
    }

    @Override
    public void describe(DiagnosticsVisitor visitor) {
        visitor.candidate("ClassPathNotation").example("gradleApi()");
    }

    @Override
    public void convert(DependencyFactory.ClassPathNotation notation, NotationConvertResult<? super SelfResolvingDependency> result) throws TypeConversionException {
        SelfResolvingDependency dependency = internCache.get(notation);
        if (dependency == null) {
            dependency = create(notation);
        }
        result.converted(dependency);
    }

    private SelfResolvingDependency create(final DependencyFactory.ClassPathNotation notation) {
        boolean runningFromInstallation = currentGradleInstallation.getInstallation() != null;
        FileCollectionInternal fileCollectionInternal;
        if (runningFromInstallation && notation.equals(GRADLE_API)) {
            fileCollectionInternal = fileCollectionFactory.create(new GeneratedFileCollection(notation.displayName) {
                @Override
                Set<File> generateFileCollection() {
                    return gradleApiFileCollection(getClassPath(notation));
                }
            });
        } else if (runningFromInstallation && notation.equals(GRADLE_TEST_KIT)) {
            fileCollectionInternal = fileCollectionFactory.create(new GeneratedFileCollection(notation.displayName) {
                @Override
                Set<File> generateFileCollection() {
                    return gradleTestKitFileCollection(getClassPath(notation));
                }
            });
        } else {
            fileCollectionInternal = fileCollectionFactory.resolving(getClassPath(notation));
        }
        SelfResolvingDependency dependency = instantiator.newInstance(DefaultSelfResolvingDependency.class, new OpaqueComponentIdentifier(notation), fileCollectionInternal);
        SelfResolvingDependency alreadyPresent = internCache.putIfAbsent(notation, dependency);
        return alreadyPresent != null ? alreadyPresent : dependency;
    }

    private List<File> getClassPath(DependencyFactory.ClassPathNotation notation) {
        return Lists.newArrayList(classPathRegistry.getClassPath(notation.name()).getAsFiles());
    }

    private Set<File> gradleApiFileCollection(Collection<File> apiClasspath) {
        // Don't inline the Groovy jar as the Groovy “tools locator” searches for it by name
        List<File> groovyImpl = classPathRegistry.getClassPath(LOCAL_GROOVY.name()).getAsFiles();
        List<File> kotlinImpl = kotlinImplFrom(apiClasspath);
        List<File> installationBeacon = classPathRegistry.getClassPath("GRADLE_INSTALLATION_BEACON").getAsFiles();
        apiClasspath.removeAll(groovyImpl);
        apiClasspath.removeAll(installationBeacon);
        // Remove Kotlin DSL and Kotlin jars
        removeKotlin(apiClasspath);

        ImmutableSet.Builder<File> builder = ImmutableSet.builder();
        builder.add(relocatedDepsJar(apiClasspath, RuntimeShadedJarType.API));
        builder.addAll(groovyImpl);
        builder.addAll(kotlinImpl);
        builder.addAll(installationBeacon);
        return builder.build();
    }

    private void removeKotlin(Collection<File> apiClasspath) {
        for (File file : new ArrayList<>(apiClasspath)) {
            String name = file.getName();
            if (file.getName().contains("kotlin")) {
                apiClasspath.remove(file);
            }
        }
    }

    private List<File> kotlinImplFrom(Collection<File> classPath) {
        ArrayList<File> files = new ArrayList<>();
        for (File file : classPath) {
            String name = file.getName();
            if (name.startsWith("kotlin-stdlib-") || name.startsWith("kotlin-reflect-")) {
                files.add(file);
            }
        }
        return files;
    }

    private Set<File> gradleTestKitFileCollection(Collection<File> testKitClasspath) {
        List<File> gradleApi = getClassPath(GRADLE_API);
        testKitClasspath.removeAll(gradleApi);

        ImmutableSet.Builder<File> builder = ImmutableSet.builder();
        builder.add(relocatedDepsJar(testKitClasspath, RuntimeShadedJarType.TEST_KIT));
        builder.addAll(gradleApiFileCollection(gradleApi));
        return builder.build();
    }

    private File relocatedDepsJar(Collection<File> classpath, RuntimeShadedJarType runtimeShadedJarType) {
        return runtimeShadedJarFactory.get(runtimeShadedJarType, classpath);
    }

    abstract static class GeneratedFileCollection implements MinimalFileSet {

        private final String displayName;
        private Set<File> generateFiles;

        public GeneratedFileCollection(String notation) {
            this.displayName = notation + " files";
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public Set<File> getFiles() {
            if (generateFiles == null) {
                generateFiles = generateFileCollection();
            }
            return generateFiles;
        }

        abstract Set<File> generateFileCollection();
    }
}
