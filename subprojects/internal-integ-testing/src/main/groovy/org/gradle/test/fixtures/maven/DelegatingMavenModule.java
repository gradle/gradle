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

package org.gradle.test.fixtures.maven;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.test.fixtures.GradleModuleMetadata;
import org.gradle.test.fixtures.Module;
import org.gradle.test.fixtures.ModuleArtifact;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.test.fixtures.gradle.VariantMetadataSpec;

import java.io.File;
import java.util.Map;

public abstract class DelegatingMavenModule<T extends MavenModule> implements MavenModule {
    private final MavenModule backingModule;

    public DelegatingMavenModule(MavenModule backingModule) {
        this.backingModule = backingModule;
    }

    private T t() {
        return (T) this;
    }

    @Override
    public String getGroup() {
        return backingModule.getGroup();
    }

    @Override
    public String getModule() {
        return backingModule.getModule();
    }

    @Override
    public String getPath() {
        return backingModule.getPath();
    }

    @Override
    public void assertNotPublished() {
        backingModule.assertNotPublished();
    }

    @Override
    public void assertPublished() {
        backingModule.assertPublished();
    }

    @Override
    public void assertPublishedAsJavaModule() {
        backingModule.assertPublishedAsJavaModule();
    }

    @Override
    public T dependsOnModules(String... dependencyArtifactIds) {
        backingModule.dependsOnModules(dependencyArtifactIds);
        return t();
    }

    @Override
    public T dependsOn(String group, String artifactId, String version) {
        backingModule.dependsOn(group, artifactId, version);
        return t();
    }

    @Override
    public T dependsOn(String group, String artifactId, String version, String type, String scope, String classifier) {
        backingModule.dependsOn(group, artifactId, version, type, scope, classifier);
        return t();
    }

    @Override
    public T dependsOn(Module module) {
        backingModule.dependsOn(module);
        return t();
    }

    @Override
    public T dependsOn(Map<String, ?> attributes, Module module) {
        backingModule.dependsOn(attributes, module);
        return t();
    }

    @Override
    public T dependencyConstraint(Module module) {
        backingModule.dependencyConstraint(module);
        return t();
    }

    @Override
    public T dependencyConstraint(Map<String, ?> attributes, Module module) {
        backingModule.dependencyConstraint(attributes, module);
        return t();
    }

    @Override
    public MavenModule withoutGradleMetadataRedirection() {
        backingModule.withoutGradleMetadataRedirection();
        return t();
    }

    @Override
    public MavenModule withoutExtraChecksums() {
        backingModule.withoutExtraChecksums();
        return t();
    }

    @Override
    public MavenModule withExtraChecksums() {
        backingModule.withoutGradleMetadataRedirection();
        return t();
    }

    @Override
    public TestFile getArtifactFile() {
        return backingModule.getArtifactFile();
    }

    @Override
    public TestFile getArtifactFile(Map options) {
        return backingModule.getArtifactFile(options);
    }

    @Override
    public ModuleArtifact getArtifact() {
        return backingModule.getArtifact();
    }

    @Override
    public ModuleArtifact getArtifact(Map<String, ?> options) {
        return backingModule.getArtifact(options);
    }

    @Override
    public ModuleArtifact getArtifact(String relativePath) {
        return backingModule.getArtifact(relativePath);
    }

    @Override
    public ModuleArtifact getPom() {
        return backingModule.getPom();
    }

    @Override
    public ModuleArtifact getModuleMetadata() {
        return backingModule.getModuleMetadata();
    }

    @Override
    public String getArtifactId() {
        return backingModule.getArtifactId();
    }

    @Override
    public String getGroupId() {
        return backingModule.getGroupId();
    }

    @Override
    public TestFile getMetaDataFile() {
        return backingModule.getMetaDataFile();
    }

    @Override
    public ModuleArtifact getRootMetaData() {
        return backingModule.getRootMetaData();
    }

    @Override
    public ModuleArtifact getSnapshotMetaData() {
        return backingModule.getSnapshotMetaData();
    }

    @Override
    public MavenPom getParsedPom() {
        return backingModule.getParsedPom();
    }

    @Override
    public GradleModuleMetadata getParsedModuleMetadata() {
        return backingModule.getParsedModuleMetadata();
    }

    @Override
    public TestFile getPomFile() {
        return backingModule.getPomFile();
    }

    @Override
    public String getPublishArtifactVersion() {
        return backingModule.getPublishArtifactVersion();
    }

    @Override
    public String getVersion() {
        return backingModule.getVersion();
    }

    @Override
    public T hasPackaging(String packaging) {
        backingModule.hasPackaging(packaging);
        return t();
    }

    @Override
    public T hasType(String type) {
        backingModule.hasType(type);
        return t();
    }

    @Override
    public MavenModule variant(String variant, Map<String, String> attributes) {
        backingModule.variant(variant, attributes);
        return t();
    }

    @Override
    public MavenModule variant(String variant, Map<String, String> attributes, @DelegatesTo(value=VariantMetadataSpec.class, strategy=Closure.DELEGATE_FIRST) Closure<?> variantConfiguration) {
        backingModule.variant(variant, attributes, variantConfiguration);
        return t();
    }

    @Override
    public MavenModule adhocVariants() {
        backingModule.adhocVariants();
        return t();
    }

    @Override
    public T parent(String group, String artifactId, String version) {
        backingModule.parent(group, artifactId, version);
        return t();
    }

    @Override
    public T publish() {
        backingModule.publish();
        return t();
    }

    @Override
    public T publishPom() {
        backingModule.publishPom();
        return t();
    }

    @Override
    public T publishWithChangedContent() {
        backingModule.publishWithChangedContent();
        return t();
    }

    @Override
    public T withNonUniqueSnapshots() {
        backingModule.withNonUniqueSnapshots();
        return t();
    }

    @Override
    public T withNoPom() {
        backingModule.withNoPom();
        return t();
    }

    @Override
    public T withModuleMetadata() {
        backingModule.withModuleMetadata();
        return t();
    }

    @Override
    public boolean getUniqueSnapshots() {
        return backingModule.getUniqueSnapshots();
    }

    @Override
    public String getUniqueSnapshotVersion() {
        return backingModule.getUniqueSnapshotVersion();
    }

    @Override
    public MavenModule withVariant(String name, Closure<?> action) {
        backingModule.withVariant(name, action);
        return this;
    }

    @Override
    public Map<String, String> getAttributes() {
        return backingModule.getAttributes();
    }

    @Override
    public T withoutDefaultVariants() {
        backingModule.withoutDefaultVariants();
        return t();
    }

    @Override
    public Module withSignature(@DelegatesTo(value = File.class, strategy = Closure.DELEGATE_FIRST) Closure<?> signer) {
        backingModule.withSignature(signer);
        return t();
    }
}
