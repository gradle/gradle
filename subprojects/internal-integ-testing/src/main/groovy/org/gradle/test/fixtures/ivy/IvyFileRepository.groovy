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
package org.gradle.test.fixtures.ivy

import org.apache.ivy.core.IvyPatternHelper
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.util.TestFile

class IvyFileRepository implements IvyRepository {
    final TestFile rootDir

    IvyFileRepository(TestFile rootDir) {
        this.rootDir = rootDir
    }

    URI getUri() {
        return rootDir.toURI()
    }

    String getIvyPattern() {
        return "${uri}/${baseIvyPattern}"
    }

    String getArtifactPattern() {
        return "${uri}/${baseArtifactPattern}"
    }

    String getIvyFilePattern() {
        "ivy-[revision].xml"
    }

    String getBaseIvyPattern() {
        "$dirPattern/$ivyFilePattern"
    }

    String getArtifactFilePattern() {
        "[artifact]-[revision](.[ext])"
    }

    String getBaseArtifactPattern() {
        "$dirPattern/$artifactFilePattern"
    }

    String getDirPattern() {
        "[organisation]/[module]/[revision]"
    }

    IvyFileModule module(String organisation, String module, Object revision = '1.0') {
        def revisionString = revision.toString()
        def path = IvyPatternHelper.substitute(dirPattern, ModuleRevisionId.newInstance(organisation, module, revisionString))
        def moduleDir = rootDir.file(path)
        return new IvyFileModule(ivyFilePattern, artifactFilePattern, moduleDir, organisation, module, revisionString)
    }
}




