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

package org.gradle.api.publication.maven.internal.action;

import org.gradle.internal.UncheckedException;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.deployment.DeployRequest;
import org.sonatype.aether.impl.MetadataGenerator;
import org.sonatype.aether.impl.MetadataGeneratorFactory;
import org.sonatype.aether.impl.internal.DefaultDeployer;
import org.sonatype.aether.impl.internal.DefaultRepositorySystem;
import org.sonatype.aether.installation.InstallRequest;
import org.sonatype.aether.metadata.Metadata;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;

class SnapshotVersionManager implements MetadataGeneratorFactory, MetadataGenerator {
    private boolean uniqueVersion = true;

    public void setUniqueVersion(boolean uniqueVersion) {
        this.uniqueVersion = uniqueVersion;
    }

    public void install(RepositorySystem repositorySystem) {
        try {
            Field field = DefaultRepositorySystem.class.getDeclaredField("deployer");
            field.setAccessible(true);
            DefaultDeployer deployer = (DefaultDeployer) field.get(repositorySystem);
            deployer.addMetadataGeneratorFactory(this);
        } catch (NoSuchFieldException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } catch (IllegalAccessException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public int getPriority() {
        return -100;
    }

    @Override
    public MetadataGenerator newInstance(RepositorySystemSession session, InstallRequest request) {
        return null;
    }

    @Override
    public MetadataGenerator newInstance(RepositorySystemSession session, DeployRequest request) {
        return uniqueVersion ? null : this;
    }

    @Override
    public Collection<? extends Metadata> prepare(Collection<? extends Artifact> artifacts) {
        return Collections.emptySet();
    }

    @Override
    public Artifact transformArtifact(Artifact artifact) {
        if (artifact.isSnapshot()) {
            artifact = artifact.setVersion(artifact.getBaseVersion());
        }
        return artifact;
    }

    @Override
    public Collection<? extends Metadata> finish(Collection<? extends Artifact> artifacts) {
        return Collections.emptySet();
    }
}
