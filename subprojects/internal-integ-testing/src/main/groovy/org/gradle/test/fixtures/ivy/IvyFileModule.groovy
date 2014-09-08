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

import groovy.xml.MarkupBuilder
import org.gradle.api.Action
import org.gradle.internal.xml.XmlTransformer
import org.gradle.test.fixtures.AbstractModule
import org.gradle.test.fixtures.file.TestFile

class IvyFileModule extends AbstractModule implements IvyModule {
    final String ivyPattern
    final String artifactPattern
    final TestFile moduleDir
    final String organisation
    final String module
    final String revision
    final boolean m2Compatible
    final List dependencies = []
    final Map<String, Map> configurations = [:]
    final List artifacts = []
    final Map extendsFrom = [:]
    final Map extraAttributes = [:]
    final Map extraInfo = [:]
    String branch = null
    String status = "integration"
    boolean noMetaData
    int publishCount = 1
    XmlTransformer transformer = new XmlTransformer()

    IvyFileModule(String ivyPattern, String artifactPattern, TestFile moduleDir, String organisation, String module, String revision, boolean m2Compatible) {
        this.ivyPattern = ivyPattern
        this.artifactPattern = artifactPattern
        this.moduleDir = moduleDir
        this.organisation = organisation
        this.module = module
        this.revision = revision
        this.m2Compatible = m2Compatible
        configurations['runtime'] = [extendsFrom: [], transitive: true, visibility: 'public']
        configurations['default'] = [extendsFrom: ['runtime'], transitive: true, visibility: 'public']
    }

    IvyDescriptor getParsedIvy() {
        return new IvyDescriptor(ivyFile)
    }

    IvyFileModule configuration(Map<String, ?> options = [:], String name) {
        configurations[name] = [extendsFrom: options.extendsFrom ?: [], transitive: options.transitive ?: true, visibility: options.visibility ?: 'public']
        return this
    }

    IvyFileModule withXml(Closure action) {
        transformer.addAction(action);
        return this
    }

    /**
     * Adds an additional artifact to this module.
     * @param options Can specify any of name, type or classifier
     * @return this
     */
    IvyFileModule artifact(Map<String, ?> options = [:]) {
        artifacts << toArtifact(options)
        return this
    }

    IvyFileModule undeclaredArtifact(Map<String, ?> options) {
        def undeclaredArtifact = toArtifact(options) + [undeclared: true]
        artifacts << undeclaredArtifact
        return this
    }

    Map<String, ?> toArtifact(Map<String, ?> options = [:]) {
        return [name: options.name ?: module, type: options.type ?: 'jar',
                ext: options.ext ?: options.type ?: 'jar', classifier: options.classifier ?: null, conf: options.conf ?: '*']
    }

    IvyFileModule dependsOn(String organisation, String module, String revision) {
        dependsOn([organisation: organisation, module: module, revision: revision])
        return this
    }

    IvyFileModule dependsOn(Map<String, ?> attributes) {
        dependencies << attributes
        return this
    }

    IvyFileModule dependsOn(String... modules) {
        modules.each { dependsOn(organisation, it, revision) }
        return this
    }

    IvyFileModule extendsFrom(Map<String, ?> attributes) {
        this.extendsFrom.clear()
        this.extendsFrom.putAll(attributes)
        return this
    }

    IvyFileModule nonTransitive(String config) {
        configurations[config].transitive = false
        return this
    }

    IvyFileModule withStatus(String status) {
        this.status = status
        return this
    }

    IvyFileModule withBranch(String branch) {
        this.branch = branch
        return this
    }

    IvyFileModule withNoMetaData() {
        noMetaData = true
        return this
    }

    IvyFileModule withExtraAttributes(Map extraAttributes) {
        this.extraAttributes.putAll(extraAttributes)
        return this
    }

    /**
     * Keys in extra info will be prefixed with namespace prefix "ns" in this fixture.
     */
    IvyFileModule withExtraInfo(Map extraInfo) {
        this.extraInfo.putAll(extraInfo)
        return this
    }

    protected String getIvyFilePath() {
        getArtifactFilePath(name: "ivy", type: "ivy", ext: "xml")
    }

    TestFile getIvyFile() {
        return moduleDir.file(ivyFilePath)
    }

    TestFile getJarFile() {
        return moduleDir.file(jarFilePath)
    }

    protected String getJarFilePath() {
        getArtifactFilePath(name: module, type: "jar", ext: "jar")
    }

    TestFile file(Map<String, ?> options) {
        return moduleDir.file(getArtifactFilePath(options))
    }

    protected String getArtifactFilePath(Map<String, ?> options) {
        def artifact = toArtifact(options)
        def tokens = [organisation: organisation, module: module, revision: revision, artifact: artifact.name, type: artifact.type, ext: artifact.ext, classifier: artifact.classifier]
        M2CompatibleIvyPatternHelper.substitute(artifactPattern, m2Compatible, tokens)
    }

    /**
     * Publishes ivy.xml plus all artifacts with different content to previous publication.
     */
    IvyFileModule publishWithChangedContent() {
        publishCount++
        publish()
    }

    String getPublicationDate() {
        return String.format("2010010112%04d", publishCount)
    }

    /**
     * Publishes ivy.xml (if enabled) plus all artifacts
     */
    IvyFileModule publish() {
        moduleDir.createDir()

        if (artifacts.empty) {
            artifact([:])
        }

        artifacts.each { artifact ->
            def artifactFile = file(artifact)
            publish(artifactFile) { Writer writer ->
                writer << "${artifactFile.name} : $artifactContent"
            }
        }
        if (noMetaData) {
            return this
        }

        publish(ivyFile) { Writer writer ->
            transformer.transform(writer, { writeTo(it) } as Action)
        }

        return this
    }

    private writeTo(Writer ivyFileWriter) {
        ivyFileWriter << """<?xml version="1.0" encoding="UTF-8"?>
<ivy-module version="1.0" xmlns:m="http://ant.apache.org/ivy/maven" """
        if (extraAttributes) {
            ivyFileWriter << ' xmlns:e="http://ant.apache.org/ivy/extra"'
        }
        ivyFileWriter << "><!--" + artifactContent + "-->"

        def builder = new MarkupBuilder(ivyFileWriter)
        def infoAttrs = [organisation: organisation, module: module, revision: revision, status: status, publication: getPublicationDate()]
        if (branch) {
            infoAttrs.branch = branch
        }
        infoAttrs += extraAttributes.collectEntries {key, value -> ["e:$key", value]}
        builder.info(infoAttrs) {
            if (extendsFrom) {
                "extends"(extendsFrom)
            }
            extraInfo.each { key, value ->
                "ns:${key.name}"('xmlns:ns': "${key.namespace}", value)
            }
        }
        builder.configurations {
            configurations.each { name, config ->
                def confAttrs = [name: name, visibility: config.visibility]
                if (config.extendsFrom) {
                    confAttrs.extends=config.extendsFrom.join(',')
                }
                if (!config.transitive) {
                    confAttrs.transitive='false'
                }
                conf(confAttrs)
            }
        }
        builder.publications {
            artifacts.each { art ->
                if (!art.undeclared) {
                    def attrs = [name: art.name, type:art.type, ext: art.ext, conf:art.conf]
                    if (art.classifier) {
                        attrs["m:classifier"] = art.classifier
                    }
                    builder.artifact(attrs)
                }
            }
        }
        builder.dependencies {
            dependencies.each { dep ->
                def depAttrs = [org: dep.organisation, name: dep.module, rev: dep.revision]
                if (dep.conf) {
                    depAttrs.conf = dep.conf
                }
                if (dep.revConstraint) {
                    depAttrs.revConstraint = dep.revConstraint
                }
                dependency(depAttrs)
            }
        }

ivyFileWriter << '</ivy-module>'
    }

    @Override
    protected onPublish(TestFile file) {
        sha1File(file)
    }

    private String getArtifactContent() {
        // Some content to include in each artifact, so that its size and content varies on each publish
        return (0..publishCount).join("-")
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
        for (name in names) {
            assertChecksumPublishedFor(moduleDir.file(name))
        }
    }

    void assertChecksumPublishedFor(TestFile testFile) {
        def sha1File = sha1File(testFile)
        sha1File.assertIsFile()
        assert new BigInteger(sha1File.text, 16) == getHash(testFile, "SHA1")
    }

    void assertNotPublished() {
        ivyFile.assertDoesNotExist()
    }

    void assertIvyAndJarFilePublished() {
        assertArtifactsPublished(ivyFile.name, jarFile.name)
        assertPublished()
    }

    void assertPublished() {
        assert ivyFile.assertIsFile()
        assert parsedIvy.organisation == organisation
        assert parsedIvy.module == module
        assert parsedIvy.revision == revision
    }

    void assertPublishedAsJavaModule() {
        assertPublished()
        assertArtifactsPublished("${module}-${revision}.jar", "ivy-${revision}.xml")
        parsedIvy.expectArtifact(module, "jar").hasAttributes("jar", "jar", ["runtime"], null)
    }

    void assertPublishedAsWebModule() {
        assertPublished()
        assertArtifactsPublished("${module}-${revision}.war", "ivy-${revision}.xml")
        parsedIvy.expectArtifact(module, "war").hasAttributes("war", "war", ["master"])
    }

    void assertPublishedAsEarModule() {
        assertPublished()
        assertArtifactsPublished("${module}-${revision}.ear", "ivy-${revision}.xml")
        parsedIvy.expectArtifact(module, "ear").hasAttributes("ear", "ear", ["master"])
    }
}
