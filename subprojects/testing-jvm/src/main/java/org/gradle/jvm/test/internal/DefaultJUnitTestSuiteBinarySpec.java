/*
 * Copyright 2015 the original author or authors.
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

import com.google.common.collect.Lists;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.component.local.model.DefaultLibraryBinaryIdentifier;
import org.gradle.internal.component.local.model.UsageKind;
import org.gradle.jvm.JvmBinarySpec;
import org.gradle.jvm.internal.*;
import org.gradle.jvm.test.JUnitTestSuiteSpec;
import org.gradle.jvm.test.JvmTestSuiteBinarySpec;
import org.gradle.language.base.DependentSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.model.VariantsMetaData;
import org.gradle.language.base.internal.resolve.LocalComponentResolveContext;
import org.gradle.model.ModelMap;
import org.gradle.platform.base.BinaryTasksCollection;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.Variant;
import org.gradle.platform.base.internal.BinaryTasksCollectionWrapper;
import org.gradle.platform.base.internal.DefaultLibraryBinaryDependencySpec;

import java.util.Collection;
import java.util.List;

public class DefaultJUnitTestSuiteBinarySpec extends DefaultJvmBinarySpec implements JUnitTestSuiteBinarySpecInternal, WithJvmAssembly, WithDependencies {
    private String junitVersion;
    private Collection<DependencySpec> binaryLevelDependencies = Lists.newLinkedList();
    private JvmBinarySpec testedBinary;
    private final DefaultTasksCollection tasks = new DefaultTasksCollection(super.getTasks());
    private VariantsMetaData variantsMetaData;
    private ArtifactDependencyResolver artifactDependencyResolver;
    private List<ResolutionAwareRepository> remoteRepositories;

    @Override
    public JvmTestSuiteBinarySpec.JvmTestSuiteTasks getTasks() {
        return tasks;
    }

    @Override
    public JUnitTestSuiteSpec getTestSuite() {
        return getComponentAs(JUnitTestSuiteSpec.class);
    }

    @Override
    public JvmBinarySpec getTestedBinary() {
        return testedBinary;
    }

    @Override
    protected String getTypeName() {
        return "Test suite";
    }

    @Override
    @Variant
    public String getjUnitVersion() {
        return junitVersion;
    }

    @Override
    public void setjUnitVersion(String version) {
        this.junitVersion = version;
    }

    @Override
    public void setDependencies(Collection<DependencySpec> dependencies) {
        this.binaryLevelDependencies = dependencies;
    }

    @Override
    public Collection<DependencySpec> getDependencies() {
        return binaryLevelDependencies;
    }

    @Override
    public void setTestedBinary(JvmBinarySpec testedBinary) {
        this.testedBinary = testedBinary;
    }

    @Override
    public DependencyResolvingClasspath getRuntimeClasspath() {
        return new DependencyResolvingClasspath(this,
            getDisplayName(),
            artifactDependencyResolver,
            remoteRepositories,
            createResolveContext());
    }

    @Override
    public void setArtifactDependencyResolver(ArtifactDependencyResolver artifactDependencyResolver) {
        this.artifactDependencyResolver = artifactDependencyResolver;
    }

    @Override
    public void setRepositories(List<ResolutionAwareRepository> repositories) {
        this.remoteRepositories = repositories;
    }

    @Override
    public void setVariantsMetaData(VariantsMetaData variantsMetaData) {
        this.variantsMetaData = variantsMetaData;
    }

    private LocalComponentResolveContext createResolveContext() {
        // TODO:Cedric find out why if we use the same ID directly, it fails resolution by trying to get the artifacts
        // from the resolving metadata instead of the resolved metadata
        LibraryBinaryIdentifier thisId = new DefaultLibraryBinaryIdentifier(getId().getProjectPath(), getId().getLibraryName() + "Test", getId().getVariant());
        return new LocalComponentResolveContext(thisId,
            variantsMetaData,
            runtimeDependencies(),
            UsageKind.RUNTIME,
            getDisplayName());
    }

    private List<DependencySpec> runtimeDependencies() {
        List<DependencySpec> dependencies = Lists.newArrayList(binaryLevelDependencies);
        dependencies.add(DefaultLibraryBinaryDependencySpec.of(getId()));
        if (testedBinary != null) {
            JvmBinarySpecInternal binary = (JvmBinarySpecInternal) testedBinary;
            LibraryBinaryIdentifier id = binary.getId();
            dependencies.add(DefaultLibraryBinaryDependencySpec.of(id));
        }
        addSourceSetSpecificDependencies(dependencies, getSources());
        addSourceSetSpecificDependencies(dependencies, getTestSuite().getSources());
        return dependencies;
    }

    private void addSourceSetSpecificDependencies(List<DependencySpec> dependencies, ModelMap<LanguageSourceSet> sources) {
        for (LanguageSourceSet sourceSet : sources) {
            if (sourceSet instanceof DependentSourceSet) {
                dependencies.addAll(((DependentSourceSet) sourceSet).getDependencies().getDependencies());
            }
        }
    }

    static class DefaultTasksCollection extends BinaryTasksCollectionWrapper implements JvmTestSuiteBinarySpec.JvmTestSuiteTasks {
        public DefaultTasksCollection(BinaryTasksCollection delegate) {
            super(delegate);
        }

        @Override
        public Test getRun() {
            return findSingleTaskWithType(Test.class);
        }

    }
}
