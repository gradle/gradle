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

package org.gradle.api.publish.maven.internal.tasks;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import org.apache.maven.model.CiManagement;
import org.apache.maven.model.Contributor;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Developer;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.IssueManagement;
import org.apache.maven.model.License;
import org.apache.maven.model.MailingList;
import org.apache.maven.model.Model;
import org.apache.maven.model.Organization;
import org.apache.maven.model.Relocation;
import org.apache.maven.model.Scm;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.maven.internal.dependencies.VersionRangeMapper;
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal;
import org.gradle.api.publish.maven.MavenPomCiManagement;
import org.gradle.api.publish.maven.MavenPomContributor;
import org.gradle.api.publish.maven.MavenPomDeveloper;
import org.gradle.api.publish.maven.MavenPomIssueManagement;
import org.gradle.api.publish.maven.MavenPomLicense;
import org.gradle.api.publish.maven.MavenPomMailingList;
import org.gradle.api.publish.maven.MavenPomOrganization;
import org.gradle.api.publish.maven.MavenPomRelocation;
import org.gradle.api.publish.maven.MavenPomScm;
import org.gradle.api.publish.maven.internal.dependencies.MavenDependencyInternal;
import org.gradle.api.publish.maven.internal.publication.MavenPomDistributionManagementInternal;
import org.gradle.api.publish.maven.internal.publication.MavenPomInternal;
import org.gradle.api.publish.maven.internal.publisher.MavenProjectIdentity;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.util.internal.GUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

public class MavenPomFileGenerator {

    private static final String POM_FILE_ENCODING = "UTF-8";
    private static final String POM_VERSION = "4.0.0";
    private static final Action<XmlProvider> ADD_GRADLE_METADATA_MARKER = new Action<XmlProvider>() {
        @Override
        public void execute(XmlProvider xmlProvider) {
            StringBuilder builder = xmlProvider.asString();
            int idx = builder.indexOf("<modelVersion");
            builder.insert(idx, xmlComments(MetaDataParser.GRADLE_METADATA_MARKER_COMMENT_LINES)
                + "  "
                + xmlComment(MetaDataParser.GRADLE_6_METADATA_MARKER)
                + "  ");
        }
    };

    private final Model model = new Model();
    private final XmlTransformer xmlTransformer = new XmlTransformer();
    private final VersionRangeMapper versionRangeMapper;
    private final VersionMappingStrategyInternal versionMappingStrategy;
    private final ImmutableAttributes compileScopeAttributes;
    private final ImmutableAttributes runtimeScopeAttributes;

    public MavenPomFileGenerator(MavenProjectIdentity identity,
                                 VersionRangeMapper versionRangeMapper,
                                 VersionMappingStrategyInternal versionMappingStrategy,
                                 ImmutableAttributes compileScopeAttributes,
                                 ImmutableAttributes runtimeScopeAttributes,
                                 boolean gradleMetadataMarker) {
        this.versionRangeMapper = versionRangeMapper;
        this.versionMappingStrategy = versionMappingStrategy;
        this.compileScopeAttributes = compileScopeAttributes;
        this.runtimeScopeAttributes = runtimeScopeAttributes;
        model.setModelVersion(POM_VERSION);
        model.setGroupId(identity.getGroupId().get());
        model.setArtifactId(identity.getArtifactId().get());
        model.setVersion(identity.getVersion().get());
        if (gradleMetadataMarker) {
            xmlTransformer.addFinalizer(ADD_GRADLE_METADATA_MARKER);
        }
    }

    private static String xmlComments(String[] lines) {
        return Joiner.on("  ").join(Iterables.transform(Arrays.asList(lines), MavenPomFileGenerator::xmlComment));
    }

    private static String xmlComment(String content) {
        return "<!-- " + content + " -->\n";
    }

    public MavenPomFileGenerator configureFrom(MavenPomInternal pom) {
        model.setPackaging(pom.getPackaging());
        model.setName(pom.getName().getOrNull());
        model.setDescription(pom.getDescription().getOrNull());
        model.setUrl(pom.getUrl().getOrNull());
        model.setInceptionYear(pom.getInceptionYear().getOrNull());
        if (pom.getOrganization() != null) {
            model.setOrganization(convertOrganization(pom.getOrganization()));
        }
        if (pom.getScm() != null) {
            model.setScm(convertScm(pom.getScm()));
        }
        if (pom.getIssueManagement() != null) {
            model.setIssueManagement(convertIssueManagement(pom.getIssueManagement()));
        }
        if (pom.getCiManagement() != null) {
            model.setCiManagement(convertCiManagement(pom.getCiManagement()));
        }
        if (pom.getDistributionManagement() != null) {
            model.setDistributionManagement(convertDistributionManagement(pom.getDistributionManagement()));
        }
        for (MavenPomLicense license : pom.getLicenses()) {
            model.addLicense(convertLicense(license));
        }
        for (MavenPomDeveloper developer : pom.getDevelopers()) {
            model.addDeveloper(convertDeveloper(developer));
        }
        for (MavenPomContributor contributor : pom.getContributors()) {
            model.addContributor(convertContributor(contributor));
        }
        for (MavenPomMailingList mailingList : pom.getMailingLists()) {
            model.addMailingList(convertMailingList(mailingList));
        }
        for (Map.Entry<String, String> property : pom.getProperties().get().entrySet()) {
            model.addProperty(property.getKey(), property.getValue());
        }
        return this;
    }

    private Organization convertOrganization(MavenPomOrganization source) {
        Organization target = new Organization();
        target.setName(source.getName().getOrNull());
        target.setUrl(source.getUrl().getOrNull());
        return target;
    }

    private License convertLicense(MavenPomLicense source) {
        License target = new License();
        target.setName(source.getName().getOrNull());
        target.setUrl(source.getUrl().getOrNull());
        target.setDistribution(source.getDistribution().getOrNull());
        target.setComments(source.getComments().getOrNull());
        return target;
    }

    private Developer convertDeveloper(MavenPomDeveloper source) {
        Developer target = new Developer();
        target.setId(source.getId().getOrNull());
        target.setName(source.getName().getOrNull());
        target.setEmail(source.getEmail().getOrNull());
        target.setUrl(source.getUrl().getOrNull());
        target.setOrganization(source.getOrganization().getOrNull());
        target.setOrganizationUrl(source.getOrganizationUrl().getOrNull());
        target.setRoles(new ArrayList<String>(source.getRoles().get()));
        target.setTimezone(source.getTimezone().getOrNull());
        target.setProperties(convertProperties(source.getProperties()));
        return target;
    }

    private Contributor convertContributor(MavenPomContributor source) {
        Contributor target = new Contributor();
        target.setName(source.getName().getOrNull());
        target.setEmail(source.getEmail().getOrNull());
        target.setUrl(source.getUrl().getOrNull());
        target.setOrganization(source.getOrganization().getOrNull());
        target.setOrganizationUrl(source.getOrganizationUrl().getOrNull());
        target.setRoles(new ArrayList<String>(source.getRoles().get()));
        target.setTimezone(source.getTimezone().getOrNull());
        target.setProperties(convertProperties(source.getProperties()));
        return target;
    }

    private Properties convertProperties(Provider<Map<String, String>> source) {
        Properties target = new Properties();
        target.putAll(source.getOrElse(Collections.<String, String>emptyMap()));
        return target;
    }

    private Scm convertScm(MavenPomScm source) {
        Scm target = new Scm();
        target.setConnection(source.getConnection().getOrNull());
        target.setDeveloperConnection(source.getDeveloperConnection().getOrNull());
        target.setUrl(source.getUrl().getOrNull());
        target.setTag(source.getTag().getOrNull());
        return target;
    }

    private IssueManagement convertIssueManagement(MavenPomIssueManagement source) {
        IssueManagement target = new IssueManagement();
        target.setSystem(source.getSystem().getOrNull());
        target.setUrl(source.getUrl().getOrNull());
        return target;
    }

    private CiManagement convertCiManagement(MavenPomCiManagement source) {
        CiManagement target = new CiManagement();
        target.setSystem(source.getSystem().getOrNull());
        target.setUrl(source.getUrl().getOrNull());
        return target;
    }

    private DistributionManagement convertDistributionManagement(MavenPomDistributionManagementInternal source) {
        DistributionManagement target = new DistributionManagement();
        target.setDownloadUrl(source.getDownloadUrl().getOrNull());
        if (source.getRelocation() != null) {
            target.setRelocation(convertRelocation(source.getRelocation()));
        }
        return target;
    }

    private Relocation convertRelocation(MavenPomRelocation source) {
        Relocation target = new Relocation();
        target.setGroupId(source.getGroupId().getOrNull());
        target.setArtifactId(source.getArtifactId().getOrNull());
        target.setVersion(source.getVersion().getOrNull());
        target.setMessage(source.getMessage().getOrNull());
        return target;
    }

    private MailingList convertMailingList(MavenPomMailingList source) {
        MailingList target = new MailingList();
        target.setName(source.getName().getOrNull());
        target.setSubscribe(source.getSubscribe().getOrNull());
        target.setUnsubscribe(source.getUnsubscribe().getOrNull());
        target.setPost(source.getPost().getOrNull());
        target.setArchive(source.getArchive().getOrNull());
        target.setOtherArchives(new ArrayList<String>(source.getOtherArchives().get()));
        return target;
    }

    public void addCompileDependencyManagement(MavenDependencyInternal apiDependency) {
        addDependencyManagement(apiDependency, "compile");
    }

    public void addRuntimeDependencyManagement(MavenDependencyInternal dependency) {
        addDependencyManagement(dependency, "runtime");
    }

    public void addImportDependencyManagement(MavenDependencyInternal dependency) {
        addDependencyManagement(dependency, "import");
    }

    public void addRuntimeDependency(MavenDependencyInternal dependency) {
        addDependency(dependency, "runtime", false);
    }

    public void addOptionalRuntimeDependency(MavenDependencyInternal optionalDependency) {
        addDependency(optionalDependency, "runtime", true);
    }

    public void addCompileDependency(MavenDependencyInternal apiDependency) {
        addDependency(apiDependency, "compile", false);
    }

    public void addOptionalCompileDependency(MavenDependencyInternal optionalDependency) {
        addDependency(optionalDependency, "compile", true);
    }

    public void addDependency(MavenDependencyInternal mavenDependency, String scope, boolean optional) {
        if (mavenDependency.getArtifacts().size() == 0) {
            addDependency(mavenDependency, mavenDependency.getArtifactId(), scope, null, null, optional);
        } else {
            for (DependencyArtifact artifact : mavenDependency.getArtifacts()) {
                addDependency(mavenDependency, artifact.getName(), scope, artifact.getType(), artifact.getClassifier(), optional);
            }
        }
    }

    private void addDependency(MavenDependencyInternal dependency, String artifactId, String scope, String type, String classifier, boolean optional) {
        Dependency mavenDependency = new Dependency();
        String groupId = dependency.getGroupId();
        String dependencyVersion = dependency.getVersion();
        String projectPath = dependency.getProjectPath();
        ImmutableAttributes attributes = attributesForScope(scope);
        ModuleVersionIdentifier resolvedVersion = versionMappingStrategy.findStrategyForVariant(attributes).maybeResolveVersion(groupId, artifactId, projectPath);
        mavenDependency.setGroupId(resolvedVersion != null ? resolvedVersion.getGroup() : groupId);
        mavenDependency.setArtifactId(resolvedVersion != null ? resolvedVersion.getName() : artifactId);
        mavenDependency.setVersion(resolvedVersion != null ? resolvedVersion.getVersion() : mapToMavenSyntax(dependencyVersion));
        mavenDependency.setType(type);
        mavenDependency.setScope(scope);
        mavenDependency.setClassifier(classifier);
        if (optional) {
            // Not using setOptional(optional) in order to avoid <optional>false</optional> in the common case
            mavenDependency.setOptional(true);
        }

        for (ExcludeRule excludeRule : dependency.getExcludeRules()) {
            Exclusion exclusion = new Exclusion();
            exclusion.setGroupId(GUtil.elvis(excludeRule.getGroup(), "*"));
            exclusion.setArtifactId(GUtil.elvis(excludeRule.getModule(), "*"));
            mavenDependency.addExclusion(exclusion);
        }

        model.addDependency(mavenDependency);
    }

    private ImmutableAttributes attributesForScope(String scope) {
        if ("compile".equals(scope)) {
            return compileScopeAttributes;
        } else if ("runtime".equals(scope) || "import".equals(scope)) {
            return runtimeScopeAttributes;
        }
        throw new IllegalStateException("Unexpected scope : " + scope);
    }

    public void addDependencyManagement(MavenDependencyInternal dependency, String scope) {
        Dependency mavenDependency = new Dependency();
        String groupId = dependency.getGroupId();
        String artifactId = dependency.getArtifactId();
        String projectPath = dependency.getProjectPath();
        ImmutableAttributes attributes = attributesForScope(scope);
        ModuleVersionIdentifier resolvedVersion = versionMappingStrategy.findStrategyForVariant(attributes).maybeResolveVersion(groupId, artifactId, projectPath);
        mavenDependency.setGroupId(resolvedVersion != null ? resolvedVersion.getGroup() : groupId);
        mavenDependency.setArtifactId(resolvedVersion != null ? resolvedVersion.getName() : artifactId);
        mavenDependency.setVersion(resolvedVersion == null ? mapToMavenSyntax(dependency.getVersion()) : resolvedVersion.getVersion());
        String type = dependency.getType();
        if (type != null) {
            mavenDependency.setType(type);
        }
        // Only publish the import scope, others have too different meanings than what Gradle expresses
        if ("import".equals(scope)) {
            mavenDependency.setScope(scope);
        }

        DependencyManagement dependencyManagement = model.getDependencyManagement();
        if (dependencyManagement == null) {
            dependencyManagement = new DependencyManagement();
            model.setDependencyManagement(dependencyManagement);
        }
        dependencyManagement.addDependency(mavenDependency);
    }

    private String mapToMavenSyntax(String version) {
        return versionRangeMapper.map(version);
    }

    public MavenPomFileGenerator withXml(final Action<XmlProvider> action) {
        xmlTransformer.addAction(action);
        return this;
    }

    public MavenPomFileGenerator writeTo(File file) {
        writeTo(file, model, xmlTransformer);
        return this;
    }

    public MavenPomSpec toSpec() {
        return new MavenPomSpec(
            model,
            xmlTransformer
        );
    }

    public static class MavenPomSpec {

        private final Model model;
        private final XmlTransformer xmlTransformer;

        public MavenPomSpec(Model model, XmlTransformer xmlTransformer) {
            this.model = model;
            this.xmlTransformer = xmlTransformer;
        }

        public void writeTo(File file) {
            MavenPomFileGenerator.writeTo(file, model, xmlTransformer);
        }
    }

    private static void writeTo(File file, Model model, XmlTransformer xmlTransformer) {
        xmlTransformer.transform(file, POM_FILE_ENCODING, writer -> {
            try {
                new MavenXpp3Writer().write(writer, model);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
