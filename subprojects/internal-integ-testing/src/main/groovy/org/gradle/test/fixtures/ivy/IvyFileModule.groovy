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

import groovy.util.slurpersupport.GPathResult
import org.apache.ivy.core.IvyPatternHelper
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.util.TestFile
import org.gradle.util.hash.HashUtil

class IvyFileModule extends AbstractIvyModule {
    final String ivyPattern
    final String artifactPattern
    final TestFile moduleDir
    final String organisation
    final String module
    final String revision
    final List dependencies = []
    final Map<String, Map> configurations = [:]
    final List artifacts = []
    String status = "integration"
    boolean noMetaData
    int publishCount = 1

    IvyFileModule(String ivyPattern, String artifactPattern, TestFile moduleDir, String organisation, String module, String revision) {
        this.ivyPattern = ivyPattern
        this.artifactPattern = artifactPattern
        this.moduleDir = moduleDir
        this.organisation = organisation
        this.module = module
        this.revision = revision
        artifact([:])
        configurations['runtime'] = [extendsFrom: [], transitive: true]
        configurations['default'] = [extendsFrom: ['runtime'], transitive: true]
    }

    /**
     * Adds an additional artifact to this module.
     * @param options Can specify any of name, type or classifier
     * @return this
     */
    IvyFileModule artifact(Map<String, ?> options) {
        artifacts << [name: options.name ?: module, type: options.type ?: 'jar', classifier: options.classifier ?: null]
        return this
    }

    IvyFileModule dependsOn(String organisation, String module, String revision) {
        dependencies << [organisation: organisation, module: module, revision: revision]
        return this
    }

    IvyFileModule dependsOn(String ... modules) {
        modules.each { dependsOn(organisation, it, revision) }
        return this
    }

    IvyFileModule nonTransitive(String config) {
        configurations[config].transitive = false
        return this
    }

    IvyFileModule withStatus(String status) {
        this.status = status;
        return this
    }

    IvyFileModule withNoMetaData() {
        noMetaData = true;
        return this
    }

    TestFile getIvyFile() {
        def path = IvyPatternHelper.substitute(ivyPattern, ModuleRevisionId.newInstance(organisation, module, revision))
        return moduleDir.file(path)
    }

    GPathResult getIvyXml() {
        new XmlSlurper().parse(ivyFile)
    }

    TestFile getJarFile() {
        def path = IvyPatternHelper.substitute(artifactPattern, ModuleRevisionId.newInstance(organisation, module, revision), null, "jar", "jar")
        return moduleDir.file(path)
    }

    TestFile sha1File(File file) {
        return moduleDir.file("${file.name}.sha1")
    }

    TestFile artifactFile(String name) {
        return file(artifacts.find{ it.name == name })
    }

    /**
     * Publishes ivy.xml plus all artifacts with different content to previous publication.
     */
    IvyModule publishWithChangedContent() {
        publishCount++
        publish()
    }

    /**
     * Publishes ivy.xml (if enabled) plus all artifacts
     */
    IvyModule publish() {
        moduleDir.createDir()

        artifacts.each { artifact ->
            def artifactFile = file(artifact)
            publish(artifactFile) {
                artifactFile.text = "${artifactFile.name} : $publishCount"
            }
        }
        if (noMetaData) {
            return this
        }

        publish(ivyFile) {
            ivyFile.text = """<?xml version="1.0" encoding="UTF-8"?>
<ivy-module version="1.0" xmlns:m="http://ant.apache.org/ivy/maven">
    <!-- ${publishCount} -->
	<info organisation="${organisation}"
		module="${module}"
		revision="${revision}"
		status="${status}"
	/>
	<configurations>"""
            configurations.each { name, config ->
                ivyFile << "<conf name='$name' visibility='public'"
                if (config.extendsFrom) {
                    ivyFile << " extends='${config.extendsFrom.join(',')}'"
                }
                if (!config.transitive) {
                    ivyFile << " transitive='false'"
                }
                ivyFile << "/>"
            }
            ivyFile << """</configurations>
	<publications>
"""
            artifacts.each { artifact ->
                ivyFile << """<artifact name="${artifact.name}" type="${artifact.type}" ext="${artifact.type}" conf="*" m:classifier="${artifact.classifier ?: ''}"/>
"""
            }
            ivyFile << """
	</publications>
	<dependencies>
"""
            dependencies.each { dep ->
                ivyFile << """<dependency org="${dep.organisation}" name="${dep.module}" rev="${dep.revision}"/>
"""
            }
            ivyFile << """
    </dependencies>
</ivy-module>
        """
        }
        return this
    }

    TestFile file(artifact) {
        return moduleDir.file("${artifact.name}-${revision}${artifact.classifier ? '-' + artifact.classifier : ''}.${artifact.type}")
    }

    private publish(File file, Closure cl) {
        def lastModifiedTime = file.exists() ? file.lastModified() : null
        cl.call(file)
        if (lastModifiedTime != null) {
            file.setLastModified(lastModifiedTime + 2000)
        }
        sha1File(file).text = getHash(file, "SHA1")
    }

    /**
     * Asserts that exactly the given artifacts have been published.
     */
    void assertArtifactsPublished(String... names) {
        Set allFileNames = []
        for (name in names) {
            allFileNames.addAll([name, "${name}.sha1"])
        }
        assert moduleDir.list() as Set == allFileNames
    }

    void assertChecksumPublishedFor(TestFile testFile) {
        def sha1File = sha1File(testFile)
        sha1File.assertIsFile()
        new BigInteger(sha1File.text, 16) == new BigInteger(getHash(testFile, "SHA1"), 16)
    }

    String getHash(File file, String algorithm) {
        return HashUtil.createHash(file, algorithm).asHexString()
    }

}
