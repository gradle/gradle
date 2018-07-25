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

package org.gradle.build

import com.google.common.base.Charsets
import com.google.common.base.Preconditions
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.util.GradleVersion

import javax.inject.Inject

class UpdateReleasedVersions extends DefaultTask {

    @Internal
    File releasedVersionsFile

    @Internal
    final Property<ReleasedVersion> currentReleasedVersion

    @Inject
    UpdateReleasedVersions(ObjectFactory objectFactory) {
        currentReleasedVersion = objectFactory.property(ReleasedVersion)
    }

    @TaskAction
    void updateVersions() {
        ReleasedVersion currentReleasedVersionValue = currentReleasedVersion.get()
        Preconditions.checkNotNull(releasedVersionsFile, "File to update not specified")
        def releasedVersions = new JsonSlurper().parse(releasedVersionsFile, Charsets.UTF_8.name())
        def newReleasedVersions = updateReleasedVersions(currentReleasedVersionValue, releasedVersions)

        releasedVersionsFile.withWriter(Charsets.UTF_8.name()) { writer ->
            writer.append(new JsonBuilder(newReleasedVersions).toPrettyString())
        }
    }

    static Map updateReleasedVersions(ReleasedVersion currentReleasedVersion, releasedVersions) {
        def result = [
            latestReleaseSnapshot: releasedVersions.latestReleaseSnapshot,
            latestRc: releasedVersions.latestRc,
            finalReleases: new ArrayList(releasedVersions.finalReleases)
        ]
        def currentReleasedGradleVersion = currentReleasedVersion.version
        if (currentReleasedGradleVersion.isSnapshot()) {
            result.latestReleaseSnapshot = newerVersion(currentReleasedVersion, releasedVersions.latestReleaseSnapshot)
        } else if (!currentReleasedVersion.finalRelease) {
            result.latestRc = newerVersion(currentReleasedVersion, releasedVersions.latestRc)
        } else {
            result.finalReleases = (releasedVersions.finalReleases + currentReleasedVersion.asMap()).sort { GradleVersion.version(it.version) }.reverse()
        }
        return result
    }

    private static newerVersion(ReleasedVersion releasedVersion, Map other) {
        releasedVersion.version > GradleVersion.version(other.version) ? releasedVersion.asMap() : other
    }

}
