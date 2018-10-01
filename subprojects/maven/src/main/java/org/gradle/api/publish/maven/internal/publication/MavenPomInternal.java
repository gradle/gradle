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
import org.gradle.api.publish.maven.MavenDependency;
import org.gradle.api.publish.maven.MavenPom;
import org.gradle.api.publish.maven.MavenPomCiManagement;
import org.gradle.api.publish.maven.MavenPomContributor;
import org.gradle.api.publish.maven.MavenPomDeveloper;
import org.gradle.api.publish.maven.MavenPomIssueManagement;
import org.gradle.api.publish.maven.MavenPomLicense;
import org.gradle.api.publish.maven.MavenPomMailingList;
import org.gradle.api.publish.maven.MavenPomOrganization;
import org.gradle.api.publish.maven.MavenPomScm;
import org.gradle.api.publish.maven.internal.dependencies.MavenDependencyInternal;
import org.gradle.api.publish.maven.internal.publisher.MavenProjectIdentity;

import java.util.List;
import java.util.Set;

public interface MavenPomInternal extends MavenPom {

    List<MavenPomLicense> getLicenses();

    MavenPomOrganization getOrganization();

    List<MavenPomDeveloper> getDevelopers();

    List<MavenPomContributor> getContributors();

    MavenPomScm getScm();

    MavenPomIssueManagement getIssueManagement();

    MavenPomCiManagement getCiManagement();

    MavenPomDistributionManagementInternal getDistributionManagement();

    List<MavenPomMailingList> getMailingLists();

    MavenProjectIdentity getProjectIdentity();

    Set<MavenDependency> getApiDependencyManagement();

    Set<MavenDependency> getRuntimeDependencyManagement();

    Set<MavenDependency> getImportDependencyManagement();

    Set<MavenDependencyInternal> getApiDependencies();

    Set<MavenDependencyInternal> getRuntimeDependencies();

    Action<XmlProvider> getXmlAction();
}
