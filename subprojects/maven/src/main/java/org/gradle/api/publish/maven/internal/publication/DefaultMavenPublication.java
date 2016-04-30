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

package org.gradle.api.publish.maven.internal.publication;

import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.*;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.DefaultExcludeRule;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.Usage;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.UnionFileCollection;
import org.gradle.api.publish.internal.ProjectDependencyPublicationResolver;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.MavenArtifactSet;
import org.gradle.api.publish.maven.MavenPom;
import org.gradle.api.publish.maven.internal.artifact.DefaultMavenArtifactSet;
import org.gradle.api.publish.maven.internal.dependencies.DefaultMavenDependency;
import org.gradle.api.publish.maven.internal.dependencies.MavenDependencyInternal;
import org.gradle.api.publish.maven.internal.publisher.MavenNormalizedPublication;
import org.gradle.api.publish.maven.internal.publisher.MavenProjectIdentity;
import org.gradle.api.specs.Spec;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultMavenPublication implements MavenPublicationInternal {

    /**
     * Maven supports wildcards in exclusion rules according to:
     * http://www.smartjava.org/content/maven-and-wildcard-exclusions
     * https://issues.apache.org/jira/browse/MNG-3832
     * This should be used for non-transitive dependencies
     * @return
     */
    private static final Set<ExcludeRule> EXCLUDE_ALL_RULE = Collections.<ExcludeRule>singleton(new DefaultExcludeRule("*", "*"));
    private final String name;
    private final MavenPomInternal pom;
    private final MavenProjectIdentity projectIdentity;
    private final DefaultMavenArtifactSet mavenArtifacts;
    private final Set<MavenDependencyInternal> runtimeDependencies = new LinkedHashSet<MavenDependencyInternal>();
    private final ProjectDependencyPublicationResolver projectDependencyResolver;
    private FileCollection pomFile;
    private SoftwareComponentInternal component;

    public DefaultMavenPublication(
            String name, MavenProjectIdentity projectIdentity, NotationParser<Object, MavenArtifact> mavenArtifactParser, Instantiator instantiator,
            ProjectDependencyPublicationResolver projectDependencyResolver, FileCollectionFactory fileCollectionFactory
    ) {
        this.name = name;
        this.projectDependencyResolver = projectDependencyResolver;
        this.projectIdentity = new DefaultMavenProjectIdentity(projectIdentity.getGroupId(), projectIdentity.getArtifactId(), projectIdentity.getVersion());
        mavenArtifacts = instantiator.newInstance(DefaultMavenArtifactSet.class, name, mavenArtifactParser, fileCollectionFactory);
        pom = instantiator.newInstance(DefaultMavenPom.class, this);
    }

    public String getName() {
        return name;
    }

    public MavenPomInternal getPom() {
        return pom;
    }

    public void setPomFile(FileCollection pomFile) {
        this.pomFile = pomFile;
    }


    public void pom(Action<? super MavenPom> configure) {
        configure.execute(pom);
    }

    public void from(SoftwareComponent component) {
        if (this.component != null) {
            throw new InvalidUserDataException(String.format("Maven publication '%s' cannot include multiple components", name));
        }
        this.component = (SoftwareComponentInternal) component;

        for (Usage usage : this.component.getUsages()) {
            // TODO Need a smarter way to map usage to artifact classifier
            for (PublishArtifact publishArtifact : usage.getArtifacts()) {
                artifact(publishArtifact);
            }

            // TODO Need a smarter way to map usage to scope
            for (ModuleDependency dependency : usage.getDependencies()) {
                if (dependency instanceof ProjectDependency) {
                    addProjectDependency((ProjectDependency) dependency);
                } else {
                    addModuleDependency(dependency);
                }
            }
        }
    }

    private void addProjectDependency(ProjectDependency dependency) {
        ModuleVersionIdentifier identifier = projectDependencyResolver.resolve(dependency);
        runtimeDependencies.add(new DefaultMavenDependency(identifier.getGroup(), identifier.getName(), identifier.getVersion(), Collections.<DependencyArtifact>emptyList(), getExcludeRules(dependency)));
    }

    private void addModuleDependency(ModuleDependency dependency) {
        runtimeDependencies.add(new DefaultMavenDependency(dependency.getGroup(), dependency.getName(), dependency.getVersion(), dependency.getArtifacts(), getExcludeRules(dependency)));
    }

    private static Set<ExcludeRule> getExcludeRules(ModuleDependency dependency) {
        return dependency.isTransitive() ? dependency.getExcludeRules() : EXCLUDE_ALL_RULE;
    }

    public MavenArtifact artifact(Object source) {
        return mavenArtifacts.artifact(source);
    }

    public MavenArtifact artifact(Object source, Action<? super MavenArtifact> config) {
        return mavenArtifacts.artifact(source, config);
    }

    public MavenArtifactSet getArtifacts() {
        return mavenArtifacts;
    }

    public void setArtifacts(Iterable<?> sources) {
        mavenArtifacts.clear();
        for (Object source : sources) {
            artifact(source);
        }
    }

    public String getGroupId() {
        return projectIdentity.getGroupId();
    }

    public void setGroupId(String groupId) {
        projectIdentity.setGroupId(groupId);
    }

    public String getArtifactId() {
        return projectIdentity.getArtifactId();
    }

    public void setArtifactId(String artifactId) {
        projectIdentity.setArtifactId(artifactId);
    }

    public String getVersion() {
        return projectIdentity.getVersion();
    }

    public void setVersion(String version) {
        projectIdentity.setVersion(version);
    }

    public FileCollection getPublishableFiles() {
        return new UnionFileCollection(mavenArtifacts.getFiles(), pomFile);
    }

    public MavenProjectIdentity getMavenProjectIdentity() {
        return projectIdentity;
    }

    public Set<MavenDependencyInternal> getRuntimeDependencies() {
        return runtimeDependencies;
    }

    public MavenNormalizedPublication asNormalisedPublication() {
        return new MavenNormalizedPublication(name, getPomFile(), projectIdentity, getArtifacts(), determineMainArtifact());
    }

    private File getPomFile() {
        if (pomFile == null) {
            throw new IllegalStateException("pomFile not set for publication");
        }
        return pomFile.getSingleFile();
    }

    public String determinePackagingFromArtifacts() {
        Set<MavenArtifact> unclassifiedArtifacts = getUnclassifiedArtifactsWithExtension();
        if (unclassifiedArtifacts.size() == 1) {
            return unclassifiedArtifacts.iterator().next().getExtension();
        }
        return "pom";
    }

    private MavenArtifact determineMainArtifact() {
        Set<MavenArtifact> unclassifiedArtifacts = getUnclassifiedArtifactsWithExtension();
        if (unclassifiedArtifacts.isEmpty()) {
            return null;
        }
        if (unclassifiedArtifacts.size() == 1) {
            // Pom packaging doesn't matter when we have a single unclassified artifact
            return unclassifiedArtifacts.iterator().next();
        }
        for (MavenArtifact unclassifiedArtifact : unclassifiedArtifacts) {
            // With multiple unclassified artifacts, choose the one with extension matching pom packaging
            String packaging = pom.getPackaging();
            if (unclassifiedArtifact.getExtension().equals(packaging)) {
                return unclassifiedArtifact;
            }
        }
        return null;
    }

    private Set<MavenArtifact> getUnclassifiedArtifactsWithExtension() {
        return CollectionUtils.filter(mavenArtifacts, new Spec<MavenArtifact>() {
            public boolean isSatisfiedBy(MavenArtifact mavenArtifact) {
                return hasNoClassifier(mavenArtifact) && hasExtension(mavenArtifact);
            }
        });
    }

    private boolean hasNoClassifier(MavenArtifact element) {
        return element.getClassifier() == null || element.getClassifier().length() == 0;
    }

    private boolean hasExtension(MavenArtifact element) {
        return element.getExtension() != null && element.getExtension().length() > 0;
    }

    public ModuleVersionIdentifier getCoordinates() {
        return new DefaultModuleVersionIdentifier(getGroupId(), getArtifactId(), getVersion());
    }
}
