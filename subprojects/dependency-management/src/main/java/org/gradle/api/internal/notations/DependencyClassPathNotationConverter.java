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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.artifacts.SelfResolvingDependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.artifacts.dependencies.DefaultSelfResolvingDependency;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.BuildDependenciesOnlyFileCollectionResolveContext;
import org.gradle.api.internal.file.collections.FileCollectionAdapter;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.internal.file.collections.SingletonFileSet;
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
import java.util.concurrent.ConcurrentMap;

import static org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory.ClassPathNotation.GRADLE_API;
import static org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory.ClassPathNotation.GRADLE_KOTLIN_DSL;
import static org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory.ClassPathNotation.GRADLE_TEST_KIT;
import static org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory.ClassPathNotation.LOCAL_GROOVY;

public class DependencyClassPathNotationConverter implements NotationConverter<DependencyFactory.ClassPathNotation, SelfResolvingDependency> {

    private final ClassPathRegistry classPathRegistry;
    private final Instantiator instantiator;
    private final FileResolver fileResolver;
    private final RuntimeShadedJarFactory runtimeShadedJarFactory;
    private final CurrentGradleInstallation currentGradleInstallation;
    private final ConcurrentMap<DependencyFactory.ClassPathNotation, SelfResolvingDependency> internCache = Maps.newConcurrentMap();

    public DependencyClassPathNotationConverter(
        Instantiator instantiator,
        ClassPathRegistry classPathRegistry,
        FileResolver fileResolver,
        RuntimeShadedJarFactory runtimeShadedJarFactory,
        CurrentGradleInstallation currentGradleInstallation) {
        this.instantiator = instantiator;
        this.classPathRegistry = classPathRegistry;
        this.fileResolver = fileResolver;
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
            fileCollectionInternal = new GeneratedFileCollection(notation.displayName) {
                @Override
                FileCollection generateFileCollection() {
                    return gradleApiFileCollection(getClassPath(notation));
                }
            };
        } else if (runningFromInstallation && notation.equals(GRADLE_TEST_KIT)) {
            fileCollectionInternal = new GeneratedFileCollection(notation.displayName) {
                @Override
                FileCollection generateFileCollection() {
                    return gradleTestKitFileCollection(getClassPath(notation));
                }
            };
        } else {
            fileCollectionInternal = fileResolver.resolveFiles((Collection<File>) getClassPath(notation));
        }
        SelfResolvingDependency dependency = instantiator.newInstance(DefaultSelfResolvingDependency.class, new OpaqueComponentIdentifier(notation.displayName), fileCollectionInternal);
        SelfResolvingDependency alreadyPresent = internCache.putIfAbsent(notation, dependency);
        return alreadyPresent != null ? alreadyPresent : dependency;
    }

    private List<File> getClassPath(DependencyFactory.ClassPathNotation notation) {
        return Lists.newArrayList(classPathRegistry.getClassPath(notation.name()).getAsFiles());
    }

    private FileCollectionInternal gradleApiFileCollection(Collection<File> apiClasspath) {
        // Don't inline the Groovy jar as the Groovy “tools locator” searches for it by name
        List<File> groovyImpl = classPathRegistry.getClassPath(LOCAL_GROOVY.name()).getAsFiles();
        // Remove optional Kotlin DSL and Kotlin jars
        List<File> kotlinDsl = classPathRegistry.getClassPath(GRADLE_KOTLIN_DSL.name()).getAsFiles();
        List<File> kotlinImpl = kotlinImplFrom(apiClasspath);
        List<File> installationBeacon = classPathRegistry.getClassPath("GRADLE_INSTALLATION_BEACON").getAsFiles();
        apiClasspath.removeAll(groovyImpl);
        apiClasspath.removeAll(kotlinDsl);
        apiClasspath.removeAll(installationBeacon);

        return (FileCollectionInternal) relocatedDepsJar(apiClasspath, "gradleApi()", RuntimeShadedJarType.API)
            .plus(fileResolver.resolveFiles(groovyImpl, kotlinImpl, installationBeacon));
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

    private FileCollectionInternal gradleTestKitFileCollection(Collection<File> testKitClasspath) {
        List<File> gradleApi = getClassPath(GRADLE_API);
        testKitClasspath.removeAll(gradleApi);

        return (FileCollectionInternal) relocatedDepsJar(testKitClasspath, "gradleTestKit()", RuntimeShadedJarType.TEST_KIT)
            .plus(gradleApiFileCollection(gradleApi));
    }

    private FileCollectionInternal relocatedDepsJar(Collection<File> classpath, String displayName, RuntimeShadedJarType runtimeShadedJarType) {
        File gradleImplDepsJar = runtimeShadedJarFactory.get(runtimeShadedJarType, classpath);
        return new FileCollectionAdapter(new SingletonFileSet(gradleImplDepsJar, displayName));
    }

    abstract class GeneratedFileCollection extends CompositeFileCollection {

        private final String displayName;
        private FileCollection generatedCollection;

        public GeneratedFileCollection(String notation) {
            this.displayName = notation + " files";
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public void visitContents(FileCollectionResolveContext context) {
            // we assume generated file collections have no build dependencies
            if (context instanceof BuildDependenciesOnlyFileCollectionResolveContext) {
                return;
            }
            if (generatedCollection == null) {
                generatedCollection = generateFileCollection();
            }
            context.add(generatedCollection);
        }

        abstract FileCollection generateFileCollection();
    }
}
