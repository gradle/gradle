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
import org.gradle.api.XmlProvider;
import org.gradle.api.internal.UserCodeAction;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.publish.maven.MavenPomCiManagement;
import org.gradle.api.publish.maven.MavenPomContributor;
import org.gradle.api.publish.maven.MavenPomContributorSpec;
import org.gradle.api.publish.maven.MavenPomDeveloper;
import org.gradle.api.publish.maven.MavenPomDeveloperSpec;
import org.gradle.api.publish.maven.MavenPomDistributionManagement;
import org.gradle.api.publish.maven.MavenPomIssueManagement;
import org.gradle.api.publish.maven.MavenPomLicense;
import org.gradle.api.publish.maven.MavenPomLicenseSpec;
import org.gradle.api.publish.maven.MavenPomMailingList;
import org.gradle.api.publish.maven.MavenPomMailingListSpec;
import org.gradle.api.publish.maven.MavenPomOrganization;
import org.gradle.api.publish.maven.MavenPomScm;
import org.gradle.api.publish.maven.internal.dependencies.MavenPomDependencies;
import org.gradle.api.publish.maven.internal.publisher.MavenPublicationCoordinates;
import org.gradle.internal.MutableActionSet;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public abstract class DefaultMavenPom implements MavenPomInternal, MavenPomLicenseSpec, MavenPomDeveloperSpec, MavenPomContributorSpec, MavenPomMailingListSpec {

    private final MutableActionSet<XmlProvider> xmlAction = new MutableActionSet<>();
    private final ObjectFactory objectFactory;
    private final MavenPublicationCoordinates mavenPublicationCoordinates;
    private final List<MavenPomLicense> licenses = new ArrayList<>();
    private MavenPomOrganization organization;
    private final List<MavenPomDeveloper> developers = new ArrayList<>();
    private final List<MavenPomContributor> contributors = new ArrayList<>();
    private MavenPomScm scm;
    private MavenPomIssueManagement issueManagement;
    private MavenPomCiManagement ciManagement;
    private MavenPomDistributionManagementInternal distributionManagement;
    private final List<MavenPomMailingList> mailingLists = new ArrayList<>();

    @Inject
    public DefaultMavenPom(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
        this.mavenPublicationCoordinates = objectFactory.newInstance(MavenPublicationCoordinates.class);
    }

    @Override
    public void withXml(Action<? super XmlProvider> action) {
        xmlAction.add(new UserCodeAction<>("Could not apply withXml() to generated POM", action));
    }

    @Override
    public Action<XmlProvider> getXmlAction() {
        return xmlAction;
    }

    @Override
    public abstract Property<Boolean> getWriteGradleMetadataMarker();

    @Override
    public abstract Property<String> getPackaging();

    @Override
    public abstract Property<String> getName();

    @Override
    public abstract Property<String> getDescription();

    @Override
    public abstract Property<String> getUrl();

    @Override
    public abstract Property<String> getInceptionYear();

    @Override
    public void licenses(Action<? super MavenPomLicenseSpec> action) {
        action.execute(this);
    }

    @Override
    public void license(Action<? super MavenPomLicense> action) {
        configureAndAdd(DefaultMavenPomLicense.class, action, licenses);
    }

    @Override
    public List<MavenPomLicense> getLicenses() {
        return licenses;
    }

    @Override
    public void organization(Action<? super MavenPomOrganization> action) {
        if (organization == null) {
            organization = objectFactory.newInstance(DefaultMavenPomOrganization.class, objectFactory);
        }
        action.execute(organization);
    }

    @Override
    public MavenPomOrganization getOrganization() {
        return organization;
    }

    @Override
    public void developers(Action<? super MavenPomDeveloperSpec> action) {
        action.execute(this);
    }

    @Override
    public void developer(Action<? super MavenPomDeveloper> action) {
        configureAndAdd(DefaultMavenPomDeveloper.class, action, developers);
    }

    @Override
    public List<MavenPomDeveloper> getDevelopers() {
        return developers;
    }

    @Override
    public void contributors(Action<? super MavenPomContributorSpec> action) {
        action.execute(this);
    }

    @Override
    public void contributor(Action<? super MavenPomContributor> action) {
        configureAndAdd(DefaultMavenPomDeveloper.class, action, contributors);
    }

    @Override
    public List<MavenPomContributor> getContributors() {
        return contributors;
    }

    @Override
    public MavenPomScm getScm() {
        return scm;
    }

    @Override
    public void scm(Action<? super MavenPomScm> action) {
        if (scm == null) {
            scm = objectFactory.newInstance(DefaultMavenPomScm.class, objectFactory);
        }
        action.execute(scm);
    }

    @Override
    public void issueManagement(Action<? super MavenPomIssueManagement> action) {
        if (issueManagement == null) {
            issueManagement = objectFactory.newInstance(DefaultMavenPomProjectManagement.class, objectFactory);
        }
        action.execute(issueManagement);
    }

    @Override
    public MavenPomIssueManagement getIssueManagement() {
        return issueManagement;
    }

    @Override
    public void ciManagement(Action<? super MavenPomCiManagement> action) {
        if (ciManagement == null) {
            ciManagement = objectFactory.newInstance(DefaultMavenPomProjectManagement.class, objectFactory);
        }
        action.execute(ciManagement);
    }

    @Override
    public MavenPomCiManagement getCiManagement() {
        return ciManagement;
    }

    @Override
    public void distributionManagement(Action<? super MavenPomDistributionManagement> action) {
        if (distributionManagement == null) {
            distributionManagement = objectFactory.newInstance(DefaultMavenPomDistributionManagement.class, objectFactory);
        }
        action.execute(distributionManagement);
    }

    @Override
    public MavenPomDistributionManagementInternal getDistributionManagement() {
        return distributionManagement;
    }

    @Override
    public void mailingLists(Action<? super MavenPomMailingListSpec> action) {
        action.execute(this);
    }

    @Override
    public void mailingList(Action<? super MavenPomMailingList> action) {
        configureAndAdd(DefaultMavenPomMailingList.class, action, mailingLists);
    }

    @Override
    public List<MavenPomMailingList> getMailingLists() {
        return mailingLists;
    }

    @Override
    public abstract MapProperty<String, String> getProperties();

    @Override
    public MavenPublicationCoordinates getCoordinates() {
        return mavenPublicationCoordinates;
    }

    @Override
    public abstract Property<MavenPomDependencies> getDependencies();

    private <T> void configureAndAdd(Class<? extends T> clazz, Action<? super T> action, List<T> items) {
        T item = objectFactory.newInstance(clazz, objectFactory);
        action.execute(item);
        items.add(item);
    }
}
