/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.jvm.test.internal;

import com.google.common.collect.Sets;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.internal.component.local.model.UsageKind;
import org.gradle.jvm.JarBinarySpec;
import org.gradle.jvm.JvmBinarySpec;
import org.gradle.jvm.JvmComponentSpec;
import org.gradle.jvm.JvmLibrarySpec;
import org.gradle.jvm.internal.BaseDependencyResolvingClasspath;
import org.gradle.jvm.internal.JarBinarySpecInternal;
import org.gradle.jvm.internal.JvmAssembly;
import org.gradle.jvm.internal.WithJvmAssembly;
import org.gradle.jvm.test.JvmTestSuiteBinarySpec;
import org.gradle.jvm.test.JvmTestSuiteSpec;
import org.gradle.language.base.internal.model.DefaultVariantsMetaData;
import org.gradle.language.base.internal.resolve.LocalComponentResolveContext;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.internal.BinarySpecInternal;

import java.io.File;
import java.util.List;
import java.util.Set;

import static org.gradle.jvm.internal.DefaultJvmBinarySpec.collectDependencies;

public class JvmTestSuiteRuntimeClasspath extends BaseDependencyResolvingClasspath {

    private final JvmBinarySpec testedBinary;
    private final JvmAssembly assembly;

    @SuppressWarnings("unchecked")
    public JvmTestSuiteRuntimeClasspath(
        JvmTestSuiteBinarySpec testSuiteBinarySpec,
        JvmComponentSpec testedComponent,
        String descriptor,
        ArtifactDependencyResolver dependencyResolver,
        List<ResolutionAwareRepository> remoteRepositories,
        ModelSchemaStore schemaStore) {
        super((BinarySpecInternal) testSuiteBinarySpec, descriptor, dependencyResolver, remoteRepositories, new LocalComponentResolveContext(
            ((BinarySpecInternal) testSuiteBinarySpec).getId(),
            DefaultVariantsMetaData.extractFrom(testSuiteBinarySpec, schemaStore),
            collectAllDependencies(testSuiteBinarySpec, testedComponent, testSuiteBinarySpec.getTestedBinary()),
            UsageKind.RUNTIME,
            testSuiteBinarySpec.getDisplayName()
        ));
        this.testedBinary = testSuiteBinarySpec.getTestedBinary();
        this.assembly = ((WithJvmAssembly) testSuiteBinarySpec).getAssembly();
    }

    @SuppressWarnings("unchecked")
    private static List<DependencySpec> collectAllDependencies(JvmTestSuiteBinarySpec testSuiteBinarySpec, JvmComponentSpec testedComponent, JvmBinarySpec testedBinary) {
        JvmTestSuiteSpec testSuite = testSuiteBinarySpec.getTestSuite();
        List<DependencySpec> deps = collectDependencies(testSuiteBinarySpec, testSuite, testSuite.getDependencies().getDependencies());
        if (testedBinary instanceof JarBinarySpec && testedComponent instanceof JvmLibrarySpec) {
            List<DependencySpec> testedBinaryDeps = collectDependencies(testedBinary, testedComponent, ((JarBinarySpecInternal)testedBinary).getApiDependencies(), ((JvmLibrarySpec) testedComponent).getDependencies().getDependencies());
            deps.addAll(testedBinaryDeps);
        }
        return deps;
    }

    @Override
    public Set<File> getFiles() {
        // classpath order should not matter, but in practice, it is often the case that you can have multiple
        // instances of the same class on classpath. So we will use a linked hash set and add the elements in that
        // order:
        // 1. classes of the test suite
        // 2. classes of the tested component
        // 3. classes of dependencies of the test suite and dependencies of the tested component
        Set<File> classpath = Sets.newLinkedHashSet();
        addAssemblyToClasspath(classpath, assembly);
        addTestedComponentAssemblyToClasspath(classpath);
        addTestDependenciesToClassPath(classpath, super.getFiles());
        return classpath;
    }

    private void addTestDependenciesToClassPath(Set<File> classpath, Set<File> dependencies) {
        classpath.addAll(dependencies);
    }

    private void addTestedComponentAssemblyToClasspath(Set<File> classpath) {
        if (testedBinary instanceof WithJvmAssembly) {
            JvmAssembly testedComponentAssembly = ((WithJvmAssembly) testedBinary).getAssembly();
            addAssemblyToClasspath(classpath, testedComponentAssembly);
        }
    }

    private void addAssemblyToClasspath(Set<File> classpath, JvmAssembly assembly) {
        classpath.addAll(assembly.getClassDirectories());
        classpath.addAll(assembly.getResourceDirectories());
    }
}
