/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.test.fixtures.maven

import groovy.xml.MarkupBuilder
import groovy.xml.XmlParser
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.test.fixtures.AbstractModule
import org.gradle.test.fixtures.GradleModuleMetadata
import org.gradle.test.fixtures.Module
import org.gradle.test.fixtures.ModuleArtifact
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.gradle.ArtifactSelectorSpec
import org.gradle.test.fixtures.gradle.DependencyConstraintSpec
import org.gradle.test.fixtures.gradle.DependencySpec
import org.gradle.test.fixtures.gradle.FileSpec
import org.gradle.test.fixtures.gradle.GradleFileModuleAdapter
import org.gradle.test.fixtures.gradle.VariantMetadataSpec

import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter

abstract class AbstractMavenModule extends AbstractModule implements MavenModule {
    private final TestFile rootDir
    final TestFile moduleDir
    final String groupId
    final String artifactId
    final String version
    Map<String, String> parentPom
    String type = 'jar'
    String packaging
    int publishCount = 1
    private boolean hasPom = true
    private boolean gradleMetadataRedirect = false
    private final List<VariantMetadataSpec> variants = [new VariantMetadataSpec("api", [(Usage.USAGE_ATTRIBUTE.name): Usage.JAVA_API, (LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE.name): LibraryElements.JAR, (Category.CATEGORY_ATTRIBUTE.name): Category.LIBRARY]),
                                                        new VariantMetadataSpec("runtime", [(Usage.USAGE_ATTRIBUTE.name): Usage.JAVA_RUNTIME, (LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE.name): LibraryElements.JAR, (Category.CATEGORY_ATTRIBUTE.name): Category.LIBRARY])]
    private final List dependencies = []
    private Map<String, ?> mainArtifact = [:]
    private final List artifacts = []
    private boolean extraChecksums = true

    final Set<String> missingExtraChecksums = []
    final updateFormat = new SimpleDateFormat("yyyyMMddHHmmss")
    final timestampFormat = new SimpleDateFormat("yyyyMMdd.HHmmss")

    AbstractMavenModule(TestFile rootDir, TestFile moduleDir, String groupId, String artifactId, String version) {
        this.rootDir = rootDir
        this.moduleDir = moduleDir
        this.groupId = groupId
        this.artifactId = artifactId
        this.version = version
    }

    @Override
    String getGroup() {
        return groupId
    }

    @Override
    String getModule() {
        return artifactId
    }

    @Override
    String getPath() {
        if (!version) {
            return moduleRootPath
        }
        return "${moduleRootPath}/${version}"
    }

    String getModuleRootPath() {
        return "${groupId ? groupId.replace('.', '/') + '/' : ''}${artifactId}"
    }

    @Override
    MavenModule parent(String group, String artifactId, String version) {
        parentPom = [groupId: group, artifactId: artifactId, version: version]
        return this
    }

    TestFile getArtifactFile(Map options = [:]) {
        return getArtifact(options).file
    }

    @Override
    String getPublishArtifactVersion() {
        if (uniqueSnapshots && version.endsWith("-SNAPSHOT")) {
            return "${version.replaceFirst('-SNAPSHOT$', '')}-${getUniqueSnapshotVersion()}"
        }
        return version
    }

    String getUniqueSnapshotVersion() {
        assert uniqueSnapshots && version.endsWith('-SNAPSHOT')
        if (metaDataFile.isFile()) {
            def metaData = new XmlParser().parse(metaDataFile.assertIsFile())
            def timestamp = metaData.versioning.snapshot.timestamp[0].text().trim()
            def build = metaData.versioning.snapshot.buildNumber[0].text().trim()
            return "${timestamp}-${build}"
        }
        return "${timestampFormat.format(publishTimestamp)}-${publishCount}"
    }

    @Override
    MavenModule dependsOnModules(String... dependencyArtifactIds) {
        for (String id : dependencyArtifactIds) {
            dependsOn(groupId, id, '1.0')
        }
        return this
    }

    @Override
    MavenModule dependsOn(Module target) {
        dependsOn(target.group, target.module, target.version)
    }

    @Override
    MavenModule dependsOn(Map<String, ?> attributes, Module target) {
        this.dependencies << [groupId: target.group, artifactId: target.module, version: target.version,
                              type: attributes.type, scope: attributes.scope, classifier: attributes.classifier,
                              optional: attributes.optional, exclusions: attributes.exclusions, rejects: attributes.rejects,
                              prefers: attributes.prefers, strictly: attributes.strictly,
                              endorseStrictVersions: attributes.endorseStrictVersions, reason: attributes.reason,
                              requireCapability: attributes.requireCapability
        ]
        return this
    }

    MavenModule dependsOn(String group, String artifactId, String version, String type = null, String scope = null, String classifier = null, Collection<Map> exclusions = null) {
        this.dependencies << [groupId: group, artifactId: artifactId, version: version, type: type, scope: scope, classifier: classifier, exclusions: exclusions]
        return this
    }

    @Override
    MavenModule dependencyConstraint(Module target) {
        this.dependencies << [groupId: target.group, artifactId: target.module, version: target.version, optional: true]
        return this
    }

    @Override
    MavenModule dependencyConstraint(Map<String, ?> attributes, Module target) {
        attributes['optional'] = true
        dependsOn(attributes, target)
        return this
    }

    @Override
    MavenModule hasPackaging(String packaging) {
        this.packaging = packaging
        return this
    }

    @Override
    MavenModule hasType(String type) {
        this.type = type
        return this
    }

    @Override
    MavenModule variant(String variant, Map<String, String> attributes) {
        createVariant(variant, attributes)
        return this
    }

    @Override
    MavenModule variant(String variant, Map<String, String> attributes, @DelegatesTo(value = VariantMetadataSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> variantConfiguration) {
        def v = createVariant(variant, attributes)
        variantConfiguration.delegate = v
        variantConfiguration.resolveStrategy = Closure.DELEGATE_FIRST
        variantConfiguration()
        return this
    }

    @Override
    MavenModule adhocVariants() {
        variants.clear()
        this
    }

    private VariantMetadataSpec createVariant(String variant, Map<String, String> attributes) {
        def variantMetadata = new VariantMetadataSpec(variant, attributes)
        variants.removeAll { it.name == variant }
        variants.add(variantMetadata)
        return variantMetadata
    }

    MavenModule mainArtifact(Map<String, ?> options) {
        mainArtifact = options
        return this
    }

    /**
     * Adds an additional artifact to this module.
     * @param options Can specify any of: type or classifier
     */
    MavenModule artifact(Map<String, ?> options) {
        artifacts << options
        return this
    }

    /**
     * Same as {@link #artifact(Map)} since all additional artifacts are undeclared in maven.
     */
    MavenModule undeclaredArtifact(Map<String, ?> options) {
        return artifact(options)
    }

    String getPackaging() {
        return packaging
    }

    List getDependencies() {
        return dependencies
    }

    List getArtifacts() {
        return artifacts
    }

    List<VariantMetadataSpec> getVariants() {
        return variants
    }

    void assertNotPublished() {
        pomFile.assertDoesNotExist()
        moduleMetadata.file.assertDoesNotExist()
    }

    void assertPublished() {
        assert pomFile.assertExists()
        assert parsedPom.groupId == groupId
        assert parsedPom.artifactId == artifactId
        assert parsedPom.version == version
        def checkExtraChecksums = extraChecksums
        Set<String> missingExtra = missingExtraChecksums
        if (getModuleMetadata().file.exists()) {
            def metadata = parsedModuleMetadata
            if (metadata.component) {
                assert metadata.component.group == groupId
                assert metadata.component.module == artifactId
                assert metadata.component.version == version
            }
            if (metadata.owner) {
                def otherMetadataArtifact = getArtifact(metadata.owner.url)
                assert otherMetadataArtifact.file.file
            }
            metadata.variants.each { variant ->
                def ref = variant.availableAt
                if (ref != null) {
                    // Verify the modules are connected together correctly
                    def otherMetadataArtifact = getArtifact(this.correctURLForSnapshot(ref.url))
                    assert otherMetadataArtifact.file.file
                    def otherMetadata = new GradleModuleMetadata(otherMetadataArtifact.file)
                    def owner = otherMetadata.owner
                    assert otherMetadataArtifact.file.parentFile.file(this.correctURLForSnapshot(owner.url)) == getModuleMetadata().file
                    assert owner.group == groupId
                    assert owner.module == artifactId
                    assert owner.version == version
                    assert variant.dependencies.empty
                    assert variant.files.empty
                }
                variant.files.each { file ->
                    def artifact = getArtifact(file.url)
                    assert artifact.file.file
                    assert artifact.file.length() == file.size
                    assert Hashing.sha1().hashFile(artifact.file) == file.sha1
                    if (checkExtraChecksums && (!artifact.file.name in missingExtra)) {
                        assert Hashing.sha256().hashFile(artifact.file) == file.sha256
                        assert Hashing.sha512().hashFile(artifact.file) == file.sha512
                    }
                    assert Hashing.md5(). hashFile(artifact.file) == file.md5
                }
            }
        }
    }

    void assertPublishedAsPomModule() {
        assertPublished()
        assertArtifactsPublished("${artifactId}-${publishArtifactVersion}.pom")
        assert parsedPom.packaging == "pom"
    }

    @Override
    void assertPublishedAsJavaModule() {
        assertPublishedWithSingleArtifact("jar", null)
    }

    void assertPublishedAsWebModule() {
        assertPublishedWithSingleArtifact('war')
    }

    void assertPublishedAsEarModule() {
        assertPublishedWithSingleArtifact('ear')
    }

    private assertPublishedWithSingleArtifact(String extension, String packaging = extension) {
        assertPublished()
        def expectedArtifacts = ["${artifactId}-${publishArtifactVersion}.${extension}", "${artifactId}-${publishArtifactVersion}.pom"]
        if (hasModuleMetadata) {
            expectedArtifacts << "${artifactId}-${publishArtifactVersion}.module"
        }
        assertArtifactsPublished(expectedArtifacts)
        assert parsedPom.packaging == packaging
    }

    /**
     * Asserts that exactly the given artifacts have been deployed, along with their checksum files
     */
    void assertArtifactsPublished(String[] names) {
        TreeSet allFileNames = []
        for (name in names) {
            allFileNames.addAll([name, "${name}.sha1", "${name}.md5"]*.toString())
            if (extraChecksums && !(name in missingExtraChecksums)) {
                allFileNames.addAll(["${name}.sha256", "${name}.sha512"]*.toString())
            }
        }
        def actualModuleDirFiles = moduleDir.list() as TreeSet
        assert actualModuleDirFiles == allFileNames
        for (name in names) {
            assertChecksumsPublishedFor(moduleDir.file(name))
        }
    }

    /**
     * Asserts that exactly the given artifacts have been deployed, along with their checksum files
     */
    void assertArtifactsPublished(Iterable<String> names) {
        assertArtifactsPublished(names as String[])
    }

    void assertChecksumsPublishedFor(TestFile testFile) {
        def sha1File = sha1File(testFile)
        sha1File.assertIsFile()
        assert HashCode.fromString(sha1File.text) == Hashing.sha1().hashFile(testFile)
        if (extraChecksums && !(testFile.name in missingExtraChecksums)) {
            def sha256File = sha256File(testFile)
            sha256File.assertIsFile()
            assert HashCode.fromString(sha256File.text) == Hashing.sha256().hashFile(testFile)
            def sha512File = sha512File(testFile)
            sha512File.assertIsFile()
            assert HashCode.fromString(sha512File.text) == Hashing.sha512().hashFile(testFile)
        }
        def md5File = md5File(testFile)
        md5File.assertIsFile()
        assert HashCode.fromString(md5File.text) == Hashing.md5().hashFile(testFile)
    }

    String correctURLForSnapshot(String url) {
        if (url.contains('-SNAPSHOT')) {
            return url.replaceFirst('SNAPSHOT\\.', uniqueSnapshotVersion + '.')
        } else {
            return url
        }
    }

    @Override
    MavenPom getParsedPom() {
        return new MavenPom(pomFile)
    }

    @Override
    GradleModuleMetadata getParsedModuleMetadata() {
        return new GradleModuleMetadata(artifactFile(type: 'module'))
    }

    @Override
    DefaultRootMavenMetaData getRootMetaData() {
        new DefaultRootMavenMetaData("$moduleRootPath/${metadataFileName}", rootMetaDataFile)
    }

    @Override
    DefaultSnapshotMavenMetaData getSnapshotMetaData() {
        new DefaultSnapshotMavenMetaData("$path/${metadataFileName}", snapshotMetaDataFile)
    }

    @Override
    ModuleArtifact getArtifact() {
        return getArtifact([:])
    }

    @Override
    ModuleArtifact getPom() {
        return getArtifact(type: 'pom')
    }

    @Override
    ModuleArtifact getModuleMetadata() {
        return getArtifact(type: 'module')
    }

    @Override
    TestFile getPomFile() {
        return getPom().file
    }

    TestFile getPomFileForPublish() {
        return moduleDir.file("$artifactId-${publishArtifactVersion}.pom")
    }

    @Override
    TestFile getMetaDataFile() {
        moduleDir.file(metadataFileName)
    }

    TestFile getRootMetaDataFile() {
        moduleDir.parentFile.file(metadataFileName)
    }

    TestFile getSnapshotMetaDataFile() {
        moduleDir.file(metadataFileName)
    }

    protected String getMetadataFileName() {
        "maven-metadata.xml"
    }

    TestFile artifactFile(Map<String, ?> options) {
        return getArtifact(options).file
    }

    @Override
    ModuleArtifact getArtifact(String relativePath) {
        def file = moduleDir.file(relativePath)
        def path = file.relativizeFrom(rootDir).path
        return new ModuleArtifact() {
            @Override
            String getPath() {
                return path
            }

            @Override
            TestFile getFile() {
                return file
            }

            @Override
            String getName() {
                return file.name
            }
        }
    }

    @Override
    ModuleArtifact getArtifact(Map<String, ?> options) {
        def artifact = toArtifact(options)
        def suffix = (artifact.classifier ? "-${artifact.classifier}" : "") + (artifact.type ? ".${artifact.type}" : "")
        def artifactName = artifact.name ? artifact.name : artifactId
        return new ModuleArtifact() {
            String getFileName() {
                if (version.endsWith("-SNAPSHOT") && !metaDataFile.exists() && uniqueSnapshots) {
                    return "${artifactName}-${version}${suffix}"
                } else {
                    return "$artifactName-${publishArtifactVersion}${suffix}"
                }
            }

            @Override
            String getPath() {
                return "${AbstractMavenModule.this.getPath()}/$fileName"
            }

            @Override
            TestFile getFile() {
                return moduleDir.file(fileName)
            }

            @Override
            String getName() {
                return "${artifactName}-${version}${suffix}"
            }
        }
    }

    @Override
    MavenModule publishWithChangedContent() {
        publishCount++
        return publish()
    }

    protected Map<String, Object> toArtifact(Map<String, ?> options) {
        options = new HashMap<String, Object>(options)
        def artifact = [type: options.containsKey('type') ? options.remove('type') : type, classifier: options.remove('classifier') ?: null, name: options.containsKey('name') ? options.remove('name') : null, content: options.containsKey('content') ? options.remove('content') : null]
        assert options.isEmpty(): "Unknown options : ${options.keySet()}"
        return artifact
    }

    Date getPublishTimestamp() {
        return new Date(updateFormat.parse("20100101120000").time + publishCount * 1000)
    }

    String getFormattedPublishTimestamp() {
        publishTimestamp.toLocalDateTime()
            .atZone(ZoneId.of("GMT"))
            .format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss z").withLocale(Locale.ENGLISH))
    }

    private void publishModuleMetadata() {
        def defaultArtifacts = getArtifact([:]).collect {
            new FileSpec(it.name)
        }
        GradleFileModuleAdapter adapter = new GradleFileModuleAdapter(groupId, artifactId, version, publishArtifactVersion,
            variants.collect { v ->
                def artifacts = v.artifacts
                if (!artifacts && v.useDefaultArtifacts) {
                    artifacts = defaultArtifacts
                }
                new VariantMetadataSpec(
                    v.name,
                    v.attributes,
                    v.dependencies + dependencies.findAll { !it.optional }.collect { d ->
                        new DependencySpec(d.groupId, d.artifactId, d.version, d.prefers, d.strictly, d.rejects, d.exclusions, d.endorseStrictVersions, d.reason, d.attributes,
                            d.classifier ? new ArtifactSelectorSpec(d.artifactId, 'jar', 'jar', d.classifier) : null, d.requireCapability)
                    },
                    v.dependencyConstraints + dependencies.findAll { it.optional }.collect { d ->
                        new DependencyConstraintSpec(d.groupId, d.artifactId, d.version, d.prefers, d.strictly, d.rejects, d.reason, d.attributes)
                    },
                    artifacts,
                    v.capabilities,
                    v.availableAt,
                )
            },
            attributes + ['org.gradle.status': version.endsWith('-SNAPSHOT') ? 'integration' : 'release']
        )
        def moduleFile = moduleDir.file("$artifactId-${publishArtifactVersion}.module")
        publish(moduleFile) {
            adapter.publishTo(it, publishCount)
        }
    }

    @Override
    MavenModule publishPom() {
        moduleDir.createDir()
        def rootMavenMetaData = getRootMetaDataFile()

        updateRootMavenMetaData(rootMavenMetaData)

        if (publishesMetaDataFile()) {
            publish(metaDataFile) { Writer writer ->
                writer << getMetaDataFileContent()
            }
        }
        boolean writeRedirect = gradleMetadataRedirect
        publish(pomFileForPublish) { Writer writer ->
            def pomPackaging = packaging ?: type
            new MarkupBuilder(writer).project {
                mkp.comment(artifactContent)
                if (writeRedirect) {
                    mkp.comment(MetaDataParser.GRADLE_6_METADATA_MARKER)
                }
                modelVersion("4.0.0")
                groupId(groupId)
                artifactId(artifactId)
                version(version)
                packaging(pomPackaging)
                description("Published on ${formattedPublishTimestamp}")
                if (parentPom) {
                    parent {
                        groupId(parentPom.groupId)
                        artifactId(parentPom.artifactId)
                        version(parentPom.version)
                    }
                }
                boolean isBom = pomPackaging == 'pom' && !dependencies.isEmpty() && dependencies.findAll { it.optional }.size() == dependencies.size()

                if (isBom) {
                    dependencyManagement {
                        dependencies {
                            dependencies.each { dep ->
                                dependency {
                                    groupId(dep.groupId)
                                    artifactId(dep.artifactId)
                                    if (dep.version) {
                                        version(dep.version)
                                    }
                                    // not sure if we need the following for a BOM
                                    if (dep.type) {
                                        type(dep.type)
                                    }
                                    if (dep.scope) {
                                        scope(dep.scope)
                                    }
                                    if (dep.classifier) {
                                        classifier(dep.classifier)
                                    }
                                    if (dep.exclusions) {
                                        exclusions {
                                            for (exc in dep.exclusions) {
                                                exclusion {
                                                    groupId(exc.group ?: '*')
                                                    artifactId(exc.module ?: '*')
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (dependencies || !variants.dependencies.flatten().empty) {
                    dependencies {
                        dependencies.each { dep ->
                            dependency {
                                groupId(dep.groupId)
                                artifactId(dep.artifactId)
                                if (dep.version) {
                                    version(dep.version)
                                }
                                if (dep.type) {
                                    type(dep.type)
                                }
                                if (dep.scope) {
                                    scope(dep.scope)
                                }
                                if (dep.classifier) {
                                    classifier(dep.classifier)
                                }
                                if (dep.optional) {
                                    optional(true)
                                }
                                if (dep.exclusions) {
                                    exclusions {
                                        for (exc in dep.exclusions) {
                                            exclusion {
                                                groupId(exc.group ?: '*')
                                                artifactId(exc.module ?: '*')
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        def compileDependencies = variants.find { it.name == 'api' }?.dependencies
                        def runtimeDependencies = variants.find { it.name == 'runtime' }?.dependencies
                        if (compileDependencies) {
                            compileDependencies.each { dep ->
                                dependency {
                                    groupId(dep.group)
                                    artifactId(dep.module)
                                    if (dep.version) {
                                        version(dep.version)
                                    }
                                    scope('compile')
                                }
                            }
                        }
                        if (runtimeDependencies) {
                            (runtimeDependencies - compileDependencies).each { dep ->
                                dependency {
                                    groupId(dep.group)
                                    artifactId(dep.module)
                                    if (dep.version) {
                                        version(dep.version)
                                    }
                                    scope('runtime')
                                }
                            }
                        }
                    }
                }
            }
        }
        return this
    }

    private void updateRootMavenMetaData(TestFile rootMavenMetaData) {
        def allVersions = rootMavenMetaData.exists() ? new XmlParser().parseText(rootMavenMetaData.text).versioning.versions.version*.value().flatten() : []
        allVersions << version
        publish(rootMavenMetaData) { Writer writer ->
            def builder = new MarkupBuilder(writer)
            builder.metadata {
                groupId(groupId)
                artifactId(artifactId)
                version(allVersions.max())
                versioning {
                    versions {
                        allVersions.each { currVersion ->
                            version(currVersion)
                        }
                    }
                }
            }
        }
    }

    abstract String getMetaDataFileContent()

    @Override
    MavenModule withNoPom() {
        hasPom = false
        return this
    }

    @Override
    MavenModule publish() {
        if (hasPom) {
            publishPom()
        }
        if (hasModuleMetadata) {
            publishModuleMetadata()
        }

        artifacts.each { artifact ->
            publishArtifact(artifact)
        }
        if (type != 'pom') {
            publishArtifact(mainArtifact)
        }

        variants.each {
            it.artifacts.findAll { it.name }.each {
                def variantArtifact = moduleDir.file(it.publishUrl)
                publish(variantArtifact) { Writer writer ->
                    writer << "${it.name} : Variant artifact $it.publishUrl"
                }
            }
        }

        return this
    }

    File publishArtifact(Map<String, ?> artifact) {
        def artifactFile = artifactFile(artifact)

        publish(artifactFile, { Writer writer ->
            writer << "${artifactFile.name} : $artifactContent"
        }, (byte[]) artifact.content)
        return artifactFile
    }

    protected String getArtifactContent() {
        // Some content to include in each artifact, so that its size and content varies on each publish
        return (0..publishCount).join("-")
    }

    protected abstract boolean publishesMetaDataFile()

    @Override
    MavenModule withModuleMetadata() {
        gradleMetadataRedirect = true
        super.withModuleMetadata()
        this
    }

    @Override
    MavenModule asGradlePlatform() {
        variants.clear()
        variant('api',  [(Usage.USAGE_ATTRIBUTE.name): Usage.JAVA_API, (Category.CATEGORY_ATTRIBUTE.name): Category.REGULAR_PLATFORM]) {
            useDefaultArtifacts = false
        }
        variant('runtime', [(Usage.USAGE_ATTRIBUTE.name): Usage.JAVA_RUNTIME, (Category.CATEGORY_ATTRIBUTE.name): Category.REGULAR_PLATFORM]) {
            useDefaultArtifacts = false
        }
        hasType('pom')
        withModuleMetadata()
    }

    @Override
    MavenModule withoutGradleMetadataRedirection() {
        gradleMetadataRedirect = false
        return this
    }

    @Override
    MavenModule withoutExtraChecksums() {
        extraChecksums = false
        return this
    }

    @Override
    MavenModule withExtraChecksums() {
        extraChecksums = true
        return this
    }

    @Override
    MavenModule withVariant(String name, @DelegatesTo(value = VariantMetadataSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> action) {
        def variant = variants.find { it.name == name }
        if (variant == null) {
            variant = createVariant(name, [:])
        }
        action.resolveStrategy = Closure.DELEGATE_FIRST
        action.delegate = variant
        action()
        return this
    }

    @Override
    MavenModule eachVariant(@DelegatesTo(value = VariantMetadataSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> action) {
        variants.each { variant ->
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.delegate = variant
            action()
        }
        return this
    }

    @Override
    MavenModule withoutDefaultVariants() {
        variants.clear()
        this
    }

    @Override
    MavenModule withSignature(@DelegatesTo(value = File, strategy = Closure.DELEGATE_FIRST) Closure<?> signer) {
        super.withSignature(signer)
        this
    }
}
