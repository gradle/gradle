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

import org.gradle.api.XmlProvider;
import org.gradle.api.provider.Property;
import org.gradle.api.publish.maven.MavenPom;
import org.gradle.api.publish.maven.MavenPomCiManagement;
import org.gradle.api.publish.maven.MavenPomContributor;
import org.gradle.api.publish.maven.MavenPomDeveloper;
import org.gradle.api.publish.maven.MavenPomIssueManagement;
import org.gradle.api.publish.maven.MavenPomLicense;
import org.gradle.api.publish.maven.MavenPomMailingList;
import org.gradle.api.publish.maven.MavenPomOrganization;
import org.gradle.api.publish.maven.MavenPomScm;
import org.gradle.api.publish.maven.internal.dependencies.MavenPomDependencies;
import org.gradle.api.publish.maven.internal.publisher.MavenPublicationCoordinates;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.MutableActionSet;
import org.jspecify.annotations.Nullable;

import java.util.List;

public interface MavenPomInternal extends MavenPom {

    @Internal
    Property<String> getPackagingProperty();

    @Nested
    List<MavenPomLicense> getLicenses();

    @Nested
    @Optional
    @Nullable
    MavenPomOrganization getOrganization();

    @Nested
    List<MavenPomDeveloper> getDevelopers();

    @Nested
    List<MavenPomContributor> getContributors();

    @Nested
    @Optional
    @Nullable
    MavenPomScm getScm();

    @Nested
    @Optional
    @Nullable
    MavenPomIssueManagement getIssueManagement();

    @Nested
    @Optional
    @Nullable
    MavenPomCiManagement getCiManagement();

    @Nested
    @Optional
    @Nullable
    MavenPomDistributionManagementInternal getDistributionManagement();

    @Nested
    List<MavenPomMailingList> getMailingLists();

    @Nested
    MavenPublicationCoordinates getCoordinates();

    @Nested
    @Optional
    Property<MavenPomDependencies> getDependencies();

    @Internal
    MutableActionSet<XmlProvider> getXmlAction();

    @Input
    Property<Boolean> getWriteGradleMetadataMarker();

}
