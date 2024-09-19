/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.maven.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.provider.ProviderApiDeprecationLogger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.maven.MavenPom;
import org.gradle.api.publish.maven.internal.dependencies.VersionRangeMapper;
import org.gradle.api.publish.maven.internal.publication.MavenPomInternal;
import org.gradle.api.publish.maven.internal.tasks.MavenPomFileGenerator;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.instrumentation.api.annotations.BytecodeUpgrade;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.serialization.Transient;

import javax.inject.Inject;
import java.io.File;

/**
 * Generates a Maven module descriptor (POM) file.
 *
 * @since 1.4
 */
@UntrackedTask(because = "Gradle doesn't understand the data structures used to configure this task")
public abstract class GenerateMavenPom extends DefaultTask {

    private final Transient<Property<MavenPom>> pom = Transient.of(getObjectFactory().property(MavenPom.class));

    private final Provider<MavenPomFileGenerator.MavenPomSpec> mavenPomSpec = getPom().map(pom ->
        MavenPomFileGenerator.generateSpec((MavenPomInternal) pom)
    );

    /**
     * Get the version range mapper.
     *
     * @deprecated This method will be removed in Gradle 9.0
     */
    @Inject
    @Deprecated
    protected VersionRangeMapper getVersionRangeMapper() {
        throw new UnsupportedOperationException();
    }

    /**
     * The values set by this method are ignored.
     *
     * @deprecated This method will be removed in Gradle 9.0.
     */
    @Deprecated
    public GenerateMavenPom withCompileScopeAttributes(ImmutableAttributes compileScopeAttributes) {

        DeprecationLogger.deprecateMethod(GenerateMavenPom.class, "withCompileScopeAttributes(ImmutableAttributes)")
            .withContext("This method was never intended for public use.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "generate_maven_pom_method_deprecations")
            .nagUser();

        return this;
    }

    /**
     * The values set by this method are ignored.
     *
     * @deprecated This method will be removed in Gradle 9.0.
     */
    @Deprecated
    public GenerateMavenPom withRuntimeScopeAttributes(ImmutableAttributes runtimeScopeAttributes) {

        DeprecationLogger.deprecateMethod(GenerateMavenPom.class, "runtimeScopeAttributes(ImmutableAttributes)")
            .withContext("This method was never intended for public use.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "generate_maven_pom_method_deprecations")
            .nagUser();

        return this;
    }

    /**
     * The Maven POM.
     */
    @Internal
    @ReplacesEagerProperty
    public Property<MavenPom> getPom() {
        return pom.get();
    }

    /**
     * The file the POM will be written to.
     */
    @OutputFile
    @ReplacesEagerProperty(adapter = GenerateMavenPomAdapter.class)
    public abstract RegularFileProperty getDestination();

    @TaskAction
    public void doGenerate() {
        mavenPomSpec.get().writeTo(getDestination().getAsFile().get());
    }

    @Inject
    protected abstract ObjectFactory getObjectFactory();


    @Inject
    @Deprecated
    @SuppressWarnings("DeprecatedIsStillUsed")
    protected abstract FileResolver getFileResolver();

    static class GenerateMavenPomAdapter {
        @BytecodeUpgrade
        static File getDestination(GenerateMavenPom self) {
            return self.getDestination().getAsFile().getOrNull();
        }

        @BytecodeUpgrade
        static void setDestination(GenerateMavenPom self, File destination) {
            self.getDestination().fileValue(destination);
        }

        @BytecodeUpgrade
        static void setDestination(GenerateMavenPom self, Object destination) {
            ProviderApiDeprecationLogger.logDeprecation(GenerateMavenPom.class, "setDestination(Object)", "getDestination()");
            self.getDestination().fileValue(self.getFileResolver().resolve(destination));
        }
    }
}
