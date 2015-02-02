/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.test.fixtures.maven.publication

import groovy.io.FileType
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.IntegrityChecker
import org.gradle.test.fixtures.maven.MavenPom
import org.gradle.test.fixtures.maven.ModuleDescriptor

class MavenReleasePublicationModule extends MavenPublicationModule implements IntegrityChecker {
    MavenReleasePublicationModule(TestFile dir, String moduleName) {
        super(dir)
        this.version = dir.name
        createMavenItems(dir, moduleName)
    }


    void createMavenItems(TestFile dir, String moduleName) {
        def baseExpression = /${regexEscapeDots(moduleName)}-${version}\./
        pom = new MavenItem(dir, "${regexEscapeDots(moduleName)}-${version}.pom")
        mavenPom = new MavenPom(pom.file)

        def allFiles = []
        dir.eachFile {
            allFiles << it.name
        }
        knownArtifactTypes.each {
            def expression = baseExpression + /($it)$/
            allFiles.each { String fileName ->
                def matcher = (fileName =~ expression)
                if (matcher) {
                    this."$it" = new MavenItem(dir, fileName)
                    source = new MavenItem(dir, "${moduleName}-${version}-sources.jar")
                    javadoc = new MavenItem(dir, "${moduleName}-${version}-javadoc.jar")
                }
            }

        }
    }


    public String regexEscapeDots(String str) {
        str.replaceAll('\\.', '\\\\.')
    }

    private void containsNoUnknownFiles() {
        def allFiles = [] as Set
        dir.eachFileRecurse(FileType.FILES) { file ->
            allFiles << file.name
        }
        allFiles.removeAll(recognisedFiles())
        assert allFiles.empty
    }

    private recognisedFiles() {
        def all = []
        [pom, jar, ear, war, source, javadoc].each {
            all << it?.file?.name
            all << it?.md5?.name
            all << it?.sha1?.name
        }
        all.findAll { it != null }
    }

    @Override
    void check(ModuleDescriptor moduleDescriptor) {
        containsNoUnknownFiles()
        super.check(moduleDescriptor)
        assert mavenPom.artifactId == moduleDescriptor.moduleName
        assert mavenPom.groupId == moduleDescriptor.organisation
        assert mavenPom.version == moduleDescriptor.revision
    }
}