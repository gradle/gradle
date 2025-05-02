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

import org.gradle.test.fixtures.file.TestFile

class IvyFileRepository implements IvyRepository {
    final TestFile rootDir
    final boolean m2Compatible
    final String dirPattern
    final String ivyFilePattern
    final String artifactFilePattern

    IvyFileRepository(TestFile rootDir, boolean m2Compatible = false, String dirPattern = null, String ivyFilePattern = null, String artifactFilePattern = null) {
        this.rootDir = rootDir
        this.m2Compatible = m2Compatible
        this.dirPattern = dirPattern ?: "[organisation]/[module]/[revision]"
        this.ivyFilePattern = ivyFilePattern ?: "ivy-[revision].xml"
        this.artifactFilePattern = artifactFilePattern ?: "[artifact]-[revision](-[classifier])(.[ext])"
    }

    URI getUri() {
        return rootDir.toURI()
    }

    private String getUriWithoutTrailingSlash() {
        String uri = getUri().toASCIIString()
        if (uri.endsWith('/')) {
            uri = uri.substring(0, uri.length() - 1)
        }
        return uri
    }

    String getIvyPattern() {
        return "${uriWithoutTrailingSlash}/${baseIvyPattern}"
    }

    String getArtifactPattern() {
        return "${uriWithoutTrailingSlash}/${baseArtifactPattern}"
    }

    String getBaseIvyPattern() {
        "$dirPattern/$ivyFilePattern"
    }

    String getBaseArtifactPattern() {
        "$dirPattern/$artifactFilePattern"
    }

    String getDirPath(String organisation, String module, String revision) {
        M2CompatibleIvyPatternHelper.substitute(dirPattern, organisation, module, revision, m2Compatible)
    }

    TestFile moduleDir(String organisation, String module) {
        return this.module(organisation, module).moduleDir.parentFile
    }

    IvyFileModule module(String organisation, String module, String revision = '1.0') {
        return createModule(organisation, module, revision as String)
    }

    IvyFileModule module(String module) {
        return createModule("org.gradle.test", module, '1.0')
    }

    private IvyFileModule createModule(String organisation, String module, String revision) {
        def modulePath = getDirPath(organisation, module, revision)
        def moduleDir = rootDir.file(modulePath)
        return new IvyFileModule(ivyFilePattern, artifactFilePattern, modulePath, moduleDir, organisation, module, revision, m2Compatible)
    }
}




