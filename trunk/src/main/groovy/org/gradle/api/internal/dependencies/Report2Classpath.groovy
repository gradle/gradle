/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.internal.dependencies

import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.report.ArtifactDownloadReport
import org.apache.ivy.core.report.ConfigurationResolveReport
import org.apache.ivy.core.report.ResolveReport
import org.gradle.api.GradleException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Hans Dockter
 */
class Report2Classpath {
    Logger logger = LoggerFactory.getLogger(Report2Classpath)

    public List getClasspath(String configuration, ResolveReport resolveReport) {
        getAllArtifactReports(resolveReport, configuration).collect {ArtifactDownloadReport artifactDownloadReport ->
            artifactDownloadReport.getLocalFile()
        }
    }

    private Collection getAllArtifactReports(ResolveReport report, String[] confs) {
        logger.debug("using internal report instance to get artifacts list");
        confs.inject(new LinkedHashSet()) { Set all, String conf ->
            ConfigurationResolveReport configurationReport = report.getConfigurationReport(conf);
            if (configurationReport == null) {
                throw new GradleException("bad confs provided: " + conf
                        + " not found among " + Arrays.asList(report.getConfigurations()));
            }
            configurationReport.getModuleRevisionIds().each { ModuleRevisionId revId ->
                all += configurationReport.getDownloadReports(revId) as Set
            }
            all
        }
    }
}
