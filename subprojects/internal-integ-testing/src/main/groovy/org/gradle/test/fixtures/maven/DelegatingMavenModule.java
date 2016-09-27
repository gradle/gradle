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

import org.gradle.test.fixtures.Module;
import org.gradle.test.fixtures.file.TestFile;

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
    public TestFile getArtifactFile() {
        return backingModule.getArtifactFile();
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
    public MavenMetaData getRootMetaData() {
        return backingModule.getRootMetaData();
    }

    @Override
    public MavenPom getParsedPom() {
        return backingModule.getParsedPom();
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
}
