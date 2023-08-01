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
import com.google.common.collect.Streams;
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
import org.gradle.api.UncheckedIOException;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.lambdas.SerializableLambdas;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.maven.MavenPomCiManagement;
import org.gradle.api.publish.maven.MavenPomContributor;
import org.gradle.api.publish.maven.MavenPomDeveloper;
import org.gradle.api.publish.maven.MavenPomIssueManagement;
import org.gradle.api.publish.maven.MavenPomLicense;
import org.gradle.api.publish.maven.MavenPomMailingList;
import org.gradle.api.publish.maven.MavenPomOrganization;
import org.gradle.api.publish.maven.MavenPomRelocation;
import org.gradle.api.publish.maven.MavenPomScm;
import org.gradle.api.publish.maven.internal.dependencies.MavenDependency;
import org.gradle.api.publish.maven.internal.dependencies.MavenPomDependencies;
import org.gradle.api.publish.maven.internal.publication.MavenPomDistributionManagementInternal;
import org.gradle.api.publish.maven.internal.publication.MavenPomInternal;
import org.gradle.api.publish.maven.internal.publisher.MavenPublicationCoordinates;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.util.internal.GUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class MavenPomFileGenerator {

    private static final String POM_FILE_ENCODING = "UTF-8";
    private static final String POM_VERSION = "4.0.0";

    private MavenPomFileGenerator() {}

    public static MavenPomSpec generateSpec(MavenPomInternal pom) {
        Model model = new Model();
        model.setModelVersion(POM_VERSION);

        MavenPublicationCoordinates coordinates = pom.getCoordinates();
        model.setGroupId(coordinates.getGroupId().get());
        model.setArtifactId(coordinates.getArtifactId().get());
        model.setVersion(coordinates.getVersion().get());

        model.setPackaging(pom.getPackagingProperty().getOrNull());
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

        MavenPomDependencies dependencies = pom.getDependencies().getOrNull();
        if (dependencies != null) {
            model.setDependencyManagement(convertDependencyManagement(dependencies));
            model.setDependencies(convertDependencies(dependencies));
        }

        XmlTransformer xmlTransformer = new XmlTransformer();
        xmlTransformer.addAction(pom.getXmlAction());
        if (pom.getWriteGradleMetadataMarker().get()) {
            xmlTransformer.addFinalizer(SerializableLambdas.action(MavenPomFileGenerator::insertGradleMetadataMarker));
        }

        return new MavenPomSpec(model, xmlTransformer);
    }

    private static Organization convertOrganization(MavenPomOrganization source) {
        Organization target = new Organization();
        target.setName(source.getName().getOrNull());
        target.setUrl(source.getUrl().getOrNull());
        return target;
    }

    private static License convertLicense(MavenPomLicense source) {
        License target = new License();
        target.setName(source.getName().getOrNull());
        target.setUrl(source.getUrl().getOrNull());
        target.setDistribution(source.getDistribution().getOrNull());
        target.setComments(source.getComments().getOrNull());
        return target;
    }

    private static Developer convertDeveloper(MavenPomDeveloper source) {
        Developer target = new Developer();
        target.setId(source.getId().getOrNull());
        target.setName(source.getName().getOrNull());
        target.setEmail(source.getEmail().getOrNull());
        target.setUrl(source.getUrl().getOrNull());
        target.setOrganization(source.getOrganization().getOrNull());
        target.setOrganizationUrl(source.getOrganizationUrl().getOrNull());
        target.setRoles(new ArrayList<>(source.getRoles().get()));
        target.setTimezone(source.getTimezone().getOrNull());
        target.setProperties(convertProperties(source.getProperties()));
        return target;
    }

    private static Contributor convertContributor(MavenPomContributor source) {
        Contributor target = new Contributor();
        target.setName(source.getName().getOrNull());
        target.setEmail(source.getEmail().getOrNull());
        target.setUrl(source.getUrl().getOrNull());
        target.setOrganization(source.getOrganization().getOrNull());
        target.setOrganizationUrl(source.getOrganizationUrl().getOrNull());
        target.setRoles(new ArrayList<>(source.getRoles().get()));
        target.setTimezone(source.getTimezone().getOrNull());
        target.setProperties(convertProperties(source.getProperties()));
        return target;
    }

    private static Properties convertProperties(Provider<Map<String, String>> source) {
        Properties target = new Properties();
        target.putAll(source.getOrElse(Collections.emptyMap()));
        return target;
    }

    private static Scm convertScm(MavenPomScm source) {
        Scm target = new Scm();
        target.setConnection(source.getConnection().getOrNull());
        target.setDeveloperConnection(source.getDeveloperConnection().getOrNull());
        target.setUrl(source.getUrl().getOrNull());
        target.setTag(source.getTag().getOrNull());
        return target;
    }

    private static IssueManagement convertIssueManagement(MavenPomIssueManagement source) {
        IssueManagement target = new IssueManagement();
        target.setSystem(source.getSystem().getOrNull());
        target.setUrl(source.getUrl().getOrNull());
        return target;
    }

    private static CiManagement convertCiManagement(MavenPomCiManagement source) {
        CiManagement target = new CiManagement();
        target.setSystem(source.getSystem().getOrNull());
        target.setUrl(source.getUrl().getOrNull());
        return target;
    }

    private static DistributionManagement convertDistributionManagement(MavenPomDistributionManagementInternal source) {
        DistributionManagement target = new DistributionManagement();
        target.setDownloadUrl(source.getDownloadUrl().getOrNull());
        if (source.getRelocation() != null) {
            target.setRelocation(convertRelocation(source.getRelocation()));
        }
        return target;
    }

    private static Relocation convertRelocation(MavenPomRelocation source) {
        Relocation target = new Relocation();
        target.setGroupId(source.getGroupId().getOrNull());
        target.setArtifactId(source.getArtifactId().getOrNull());
        target.setVersion(source.getVersion().getOrNull());
        target.setMessage(source.getMessage().getOrNull());
        return target;
    }

    private static MailingList convertMailingList(MavenPomMailingList source) {
        MailingList target = new MailingList();
        target.setName(source.getName().getOrNull());
        target.setSubscribe(source.getSubscribe().getOrNull());
        target.setUnsubscribe(source.getUnsubscribe().getOrNull());
        target.setPost(source.getPost().getOrNull());
        target.setArchive(source.getArchive().getOrNull());
        target.setOtherArchives(new ArrayList<>(source.getOtherArchives().get()));
        return target;
    }

    private static DependencyManagement convertDependencyManagement(MavenPomDependencies dependencies) {
        if (dependencies.getDependencyManagement().isEmpty()) {
            return null;
        }

        List<Dependency> converted = dependencies.getDependencyManagement().stream()
            .map(MavenPomFileGenerator::convertDependencyManagementDependency)
            .collect(Collectors.toList());

        DependencyManagement dm = new DependencyManagement();
        dm.setDependencies(converted);
        return dm;
    }

    private static List<Dependency> convertDependencies(MavenPomDependencies dependencies) {
        return dependencies.getDependencies().stream()
            .map(MavenPomFileGenerator::convertDependency)
            .collect(Collectors.toList());
    }

    private static Dependency convertDependency(MavenDependency dependency) {
        Dependency mavenDependency = new Dependency();
        mavenDependency.setGroupId(dependency.getGroupId());
        mavenDependency.setArtifactId(dependency.getArtifactId());
        mavenDependency.setVersion(dependency.getVersion());
        mavenDependency.setType(dependency.getType());
        mavenDependency.setScope(dependency.getScope());
        mavenDependency.setClassifier(dependency.getClassifier());
        if (dependency.isOptional()) {
            // Not using setOptional(optional) in order to avoid <optional>false</optional> in the common case
            mavenDependency.setOptional(true);
        }

        for (ExcludeRule excludeRule : dependency.getExcludeRules()) {
            Exclusion exclusion = new Exclusion();
            exclusion.setGroupId(GUtil.elvis(excludeRule.getGroup(), "*"));
            exclusion.setArtifactId(GUtil.elvis(excludeRule.getModule(), "*"));
            mavenDependency.addExclusion(exclusion);
        }
        return mavenDependency;
    }

    private static Dependency convertDependencyManagementDependency(MavenDependency dependency) {
        Dependency mavenDependency = new Dependency();
        mavenDependency.setGroupId(dependency.getGroupId());
        mavenDependency.setArtifactId(dependency.getArtifactId());
        mavenDependency.setVersion(dependency.getVersion());
        String type = dependency.getType();
        if (type != null) {
            mavenDependency.setType(type);
        }
        mavenDependency.setScope(dependency.getScope());
        return mavenDependency;
    }

    private static void insertGradleMetadataMarker(XmlProvider xmlProvider) {
        String comment = Joiner.on("").join(
            Streams.concat(
                Arrays.stream(MetaDataParser.GRADLE_METADATA_MARKER_COMMENT_LINES),
                Stream.of(MetaDataParser.GRADLE_6_METADATA_MARKER)
            )
            .map(content -> "<!-- " + content + " -->\n  ")
            .iterator()
        );

        StringBuilder builder = xmlProvider.asString();
        int idx = builder.indexOf("<modelVersion");
        builder.insert(idx, comment);
    }

    public static class MavenPomSpec {

        private final Model model;
        private final XmlTransformer xmlTransformer;

        public MavenPomSpec(Model model, XmlTransformer xmlTransformer) {
            this.model = model;
            this.xmlTransformer = xmlTransformer;
        }

        public void writeTo(File file) {
            xmlTransformer.transform(file, POM_FILE_ENCODING, writer -> {
                try {
                    new MavenXpp3Writer().write(writer, model);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
