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
package org.gradle.jvm.plugins;

import com.google.common.collect.Lists;
import org.apache.commons.lang.WordUtils;
import org.gradle.api.Action;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.Incubating;
import org.gradle.api.Task;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestTaskReports;
import org.gradle.internal.Cast;
import org.gradle.internal.Transformers;
import org.gradle.internal.component.local.model.DefaultLibraryBinaryIdentifier;
import org.gradle.jvm.internal.resolve.UsageKind;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.JvmBinarySpec;
import org.gradle.jvm.internal.DependencyResolvingClasspath;
import org.gradle.jvm.internal.JvmAssembly;
import org.gradle.jvm.internal.JvmBinarySpecInternal;
import org.gradle.jvm.internal.WithJvmAssembly;
import org.gradle.jvm.test.JvmTestSuiteBinarySpec;
import org.gradle.jvm.test.internal.JvmTestSuiteBinarySpecInternal;
import org.gradle.language.base.DependentSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.jvm.internal.resolve.DefaultVariantsMetaData;
import org.gradle.jvm.internal.resolve.JvmLibraryResolveContext;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.model.*;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.platform.base.BinaryTasks;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.platform.base.internal.DefaultLibraryBinaryDependencySpec;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.List;

/**
 * The base plugin that needs to be applied by all plugins which provide testing support
 * for the Java software model.
 *
 * @since 2.12
 */
@Incubating
@SuppressWarnings("UnusedDeclaration")
public class JvmTestSuiteBasePlugin extends RuleSource {
    @BinaryTasks
    void createJvmTestSuiteTasks(ModelMap<Task> tasks,
                                 final JvmTestSuiteBinarySpecInternal binary,
                                 final @Path("buildDir") File buildDir) {
        final JvmAssembly jvmAssembly = ((WithJvmAssembly) binary).getAssembly();
        tasks.create(testTaskNameFor(binary), Test.class, new Action<Test>() {
            @Override
            public void execute(final Test test) {
                test.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
                test.setDescription(String.format("Runs %s.", WordUtils.uncapitalize(binary.getDisplayName())));
                test.dependsOn(jvmAssembly);
                test.setTestClassesDir(binary.getClassesDir());
                test.setClasspath(binary.getRuntimeClasspath());
                configureReports((JvmTestSuiteBinarySpecInternal) binary, test);
            }

            private void configureReports(JvmTestSuiteBinarySpecInternal binary, Test test) {
                // todo: improve configuration of reports
                TestTaskReports reports = test.getReports();
                File reportsDirectory = new File(buildDir, "reports");
                File reportsOutputDirectory = binary.getNamingScheme().getOutputDirectory(reportsDirectory);
                File htmlDir = new File(reportsOutputDirectory, "tests");
                File xmlDir = new File(buildDir, "test-results");
                File xmlDirOutputDirectory = binary.getNamingScheme().getOutputDirectory(xmlDir);
                File binDir = new File(xmlDirOutputDirectory, "binary");
                reports.getHtml().setDestination(htmlDir);
                reports.getJunitXml().setDestination(xmlDirOutputDirectory);
                test.setBinResultsDir(binDir);
            }
        });
    }

    private static String testTaskNameFor(JvmTestSuiteBinarySpec binary) {
        return ((BinarySpecInternal) binary).getProjectScopedName() + "Test";
    }

    @Finalize
    public void configureRuntimeClasspath(@Each JvmTestSuiteBinarySpecInternal testBinary, ServiceRegistry serviceRegistry, ModelSchemaStore modelSchemaStore) {
        ArtifactDependencyResolver dependencyResolver = serviceRegistry.get(ArtifactDependencyResolver.class);
        RepositoryHandler repositories = serviceRegistry.get(RepositoryHandler.class);
        List<ResolutionAwareRepository> resolutionAwareRepositories = CollectionUtils.collect(repositories, Transformers.cast(ResolutionAwareRepository.class));
        ModelSchema<? extends JvmTestSuiteBinarySpec> schema = Cast.uncheckedCast(modelSchemaStore.getSchema(((BinarySpecInternal) testBinary).getPublicType()));
        AttributesSchema attributesSchema = serviceRegistry.get(AttributesSchema.class);
        testBinary.setRuntimeClasspath(configureRuntimeClasspath(testBinary, dependencyResolver, resolutionAwareRepositories, schema, attributesSchema));
    }

    private static DependencyResolvingClasspath configureRuntimeClasspath(JvmTestSuiteBinarySpecInternal testBinary, ArtifactDependencyResolver dependencyResolver, List<ResolutionAwareRepository> resolutionAwareRepositories, ModelSchema<? extends JvmTestSuiteBinarySpec> schema, AttributesSchema attributesSchema) {
        return new DependencyResolvingClasspath(testBinary, testBinary.getDisplayName(), dependencyResolver, resolutionAwareRepositories, createResolveContext(testBinary, schema), attributesSchema);
    }

    private static JvmLibraryResolveContext createResolveContext(JvmTestSuiteBinarySpecInternal testBinary, ModelSchema<? extends JvmTestSuiteBinarySpec> schema) {
        // TODO:Cedric find out why if we use the same ID directly, it fails resolution by trying to get the artifacts
        // from the resolving metadata instead of the resolved metadata
        LibraryBinaryIdentifier id = testBinary.getId();
        LibraryBinaryIdentifier thisId = new DefaultLibraryBinaryIdentifier(id.getProjectPath(), id.getLibraryName() + "Test", id.getVariant());
        return new JvmLibraryResolveContext(thisId,
            DefaultVariantsMetaData.extractFrom(testBinary, schema),
            runtimeDependencies(testBinary),
            UsageKind.RUNTIME,
            testBinary.getDisplayName());
    }

    private static List<DependencySpec> runtimeDependencies(JvmTestSuiteBinarySpecInternal testBinary) {
        List<DependencySpec> dependencies = Lists.newArrayList(testBinary.getDependencies());
        JvmBinarySpec testedBinary = testBinary.getTestedBinary();
        dependencies.add(DefaultLibraryBinaryDependencySpec.of(testBinary.getId()));
        if (testedBinary != null) {
            JvmBinarySpecInternal binary = (JvmBinarySpecInternal) testedBinary;
            LibraryBinaryIdentifier id = binary.getId();
            dependencies.add(DefaultLibraryBinaryDependencySpec.of(id));
        }
        addSourceSetSpecificDependencies(dependencies, testBinary.getSources());
        addSourceSetSpecificDependencies(dependencies, testBinary.getTestSuite().getSources());
        return dependencies;
    }

    private static void addSourceSetSpecificDependencies(List<DependencySpec> dependencies, ModelMap<LanguageSourceSet> sources) {
        for (LanguageSourceSet sourceSet : sources) {
            if (sourceSet instanceof DependentSourceSet) {
                dependencies.addAll(((DependentSourceSet) sourceSet).getDependencies().getDependencies());
            }
        }
    }
}
