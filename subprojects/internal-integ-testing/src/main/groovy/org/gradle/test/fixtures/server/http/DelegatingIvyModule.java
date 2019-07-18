/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.test.fixtures.server.http;

import groovy.lang.Closure;
import org.gradle.internal.Cast;
import org.gradle.test.fixtures.GradleModuleMetadata;
import org.gradle.test.fixtures.Module;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.test.fixtures.ivy.IvyDescriptor;
import org.gradle.test.fixtures.ivy.IvyModule;

import java.util.Collections;
import java.util.Map;

public abstract class DelegatingIvyModule<T extends IvyModule> implements IvyModule {
    private final IvyModule backingModule;

    protected DelegatingIvyModule(IvyModule backingModule) {
        this.backingModule = backingModule;
    }

    private T t() {
        return Cast.uncheckedCast(this);
    }

    @Override
    public String getGroup() {
        return backingModule.getGroup();
    }

    @Override
    public String getOrganisation() {
        return backingModule.getOrganisation();
    }

    @Override
    public String getModule() {
        return backingModule.getModule();
    }

    @Override
    public String getVersion() {
        return backingModule.getVersion();
    }

    @Override
    public String getRevision() {
        return backingModule.getRevision();
    }

    @Override
    public IvyDescriptor getParsedIvy() {
        return backingModule.getParsedIvy();
    }

    @Override
    public GradleModuleMetadata getParsedModuleMetadata() {
        return backingModule.getParsedModuleMetadata();
    }

    @Override
    public void assertPublished() {
        backingModule.assertPublished();
    }

    @Override
    public void assertArtifactsPublished(String... names) {
        backingModule.assertArtifactsPublished(names);
    }

    @Override
    public void assertPublishedAsJavaModule() {
        backingModule.assertPublishedAsJavaModule();
    }

    @Override
    public void assertPublishedAsWebModule() {
        backingModule.assertPublishedAsWebModule();
    }

    @Override
    public T publish() {
        backingModule.publish();
        return t();
    }

    @Override
    public T publishWithChangedContent() {
        backingModule.publishWithChangedContent();
        return t();
    }

    @Override
    public T withModuleMetadata() {
        backingModule.withModuleMetadata();
        return t();
    }

    @Override
    public T withNoMetaData() {
        backingModule.withNoMetaData();
        return t();
    }

    @Override
    public IvyModule withNoIvyMetaData() {
        backingModule.withNoIvyMetaData();
        return t();
    }

    @Override
    public T withStatus(String status) {
        backingModule.withStatus(status);
        return t();
    }

    @Override
    public IvyModule withGradleMetadataRedirection() {
        backingModule.withGradleMetadataRedirection();
        return t();
    }

    @Override
    public IvyModule withBranch(String branch) {
        backingModule.withBranch(branch);
        return t();
    }

    @Override
    public T dependsOn(String organisation, String module, String revision) {
        backingModule.dependsOn(organisation, module, revision);
        return t();
    }

    @Override
    public T dependsOn(Map<String, ?> attributes) {
        backingModule.dependsOn(attributes);
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
    public IvyModule dependencyConstraint(Map<String, ?> attributes, Module module) {
        backingModule.dependencyConstraint(attributes, module);
        return t();
    }

    /**
     * Adds an additional artifact to this module.
     * @param options Can specify any of name, type, ext or classifier
     * @return t();
     */
    @Override
    public T artifact(Map<String, ?> options) {
        backingModule.artifact(options);
        return t();
    }

    public T artifact() {
        return artifact(Collections.<String, Object>emptyMap());
    }

    @Override
    public T undeclaredArtifact(Map<String, ?> options) {
        backingModule.undeclaredArtifact(options);
        return t();
    }

    public T undeclaredArtifact() {
        backingModule.undeclaredArtifact(Collections.<String, Object>emptyMap());
        return t();
    }

    @Override
    public T extendsFrom(Map<String, ?> attributes) {
        backingModule.extendsFrom(attributes);
        return t();
    }

    @Override
    public T configuration(Map<String, ?> options, String name) {
        backingModule.configuration(options, name);
        return t();
    }

    @Override
    public T configuration(String name) {
        backingModule.configuration(Collections.<String, Object>emptyMap(), name);
        return t();
    }

    @Override
    public IvyModule variant(String variant, Map<String, String> attributes) {
        backingModule.variant(variant, attributes);
        return t();
    }

    @Override
    public T withXml(Closure action) {
        backingModule.withXml(action);
        return t();
    }

    @Override
    public TestFile getIvyFile() {
        return backingModule.getIvyFile();
    }

    @Override
    public TestFile getModuleMetadataFile() {
        return backingModule.getModuleMetadataFile();
    }

    @Override
    public TestFile getJarFile() {
        return backingModule.getJarFile();
    }

    @Override
    public void assertIvyAndJarFilePublished() {
        backingModule.assertIvyAndJarFilePublished();
    }

    @Override
    public void assertMetadataAndJarFilePublished() {
        backingModule.assertMetadataAndJarFilePublished();
    }

    @Override
    public void withVariant(String name, Closure<?> action) {
        backingModule.withVariant(name, action);
    }

    @Override
    public Map<String, String> getAttributes() {
        return backingModule.getAttributes();
    }
}
