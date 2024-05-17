/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.report


import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.RepositoryAwareVerificationFailure
import org.gradle.api.internal.artifacts.verification.model.ChecksumKind
import org.gradle.api.internal.artifacts.verification.verifier.ChecksumVerificationFailure
import org.gradle.api.internal.artifacts.verification.verifier.DeletedArtifact
import org.gradle.api.internal.artifacts.verification.verifier.MissingChecksums
import org.gradle.api.internal.artifacts.verification.verifier.MissingSignature
import org.gradle.api.internal.artifacts.verification.verifier.OnlyIgnoredKeys
import org.gradle.api.internal.artifacts.verification.verifier.SignatureVerificationFailure
import org.gradle.api.internal.artifacts.verification.verifier.VerificationFailure
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.ModuleComponentFileArtifactIdentifier
import org.gradle.security.internal.PublicKeyResultBuilder
import org.gradle.security.internal.PublicKeyService
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static java.nio.charset.StandardCharsets.UTF_8
import static org.gradle.api.internal.artifacts.verification.verifier.SignatureVerificationFailure.FailureKind.FAILED
import static org.gradle.api.internal.artifacts.verification.verifier.SignatureVerificationFailure.FailureKind.IGNORED_KEY
import static org.gradle.api.internal.artifacts.verification.verifier.SignatureVerificationFailure.FailureKind.MISSING_KEY
import static org.gradle.api.internal.artifacts.verification.verifier.SignatureVerificationFailure.FailureKind.PASSED_NOT_TRUSTED

class HtmlDependencyVerificationReportRendererTest extends Specification {
    static private File dummyFile = new File("dummy")
    static private File dummyFileSig = new File("dummy.asc")

    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    File verificationFile = temporaryFolder.createFile("verification-metadata.xml")
    File reportsDir = temporaryFolder.testDirectory
    File currentReportDir
    File currentReportFile
    Document report

    @Subject
    HtmlDependencyVerificationReportRenderer renderer = new HtmlDependencyVerificationReportRenderer(
        Mock(DocumentationRegistry),
        verificationFile,
        ["pgp", "sha512"],
        reportsDir
    )

    def "copies required resources"() {
        when:
        generateReport()

        then:
        ['css': ['uikit.min.css'],
         'js': ['uikit.min.js', 'uikit-icons.min.js'],
         'img': ['gradle-logo.png']].each { dir, files ->
            def resourceDir = new File(currentReportDir, dir)
            def resources = resourceDir.list() as Set<String>
            def expectedFiles = files as Set<String>
            assert expectedFiles == resources
        }
    }

    def "can add different sections"() {
        given:
        renderer.startNewSection("First section")

        when:
        generateReport()

        then:
        bodyContains("First section")
        bodyContainsExact("First section 0 error")

        when:
        renderer.startNewSection("Second section")
        generateReport()

        then:
        bodyContains("First section")
        bodyContains("Second section")
        bodyContainsExact("First section 0 error")
        bodyContainsExact("Second section 0 error")
    }

    @Unroll("reports verification errors (#failure)")
    def "reports verification errors"() {
        given:
        renderer.startNewSection(":someConfiguration")
        renderer.startNewArtifact(artifact()) {
            renderer.reportFailure(failure)
        }

        when:
        generateReport()

        def errors = errorsFor(":someConfiguration")
        then:

        verifyAll(errors[0]) {
            module == 'org:foo:1.0'
            artifact == 'foo-1.0.jar'
            artifactTooltip == "From repository 'Maven'"
            problem == expectedProblem
        }

        where:
        failure                                                                 | expectedProblem
        checksumFailure()                                                       | 'Expected a sha256 checksum of 0abcd but was 0000'
        missingChecksums()                                                      | 'Checksums are missing from verification metadata'
        deletedArtifact()                                                       | 'Artifact has been deleted from dependency cache'
        missingSignature()                                                      | 'Signature file is missing'
        onlyIgnoredKeys()                                                       | 'All public keys have been ignored'
        signatureFailure()                                                      | "Key abcd123 (not found) couldn't be found in any key server so verification couldn't be performed"
        signatureFailure("Maven", ['abcd': signatureError(FAILED)])             | 'Artifact was signed with key abcd (not found) but signature didn\'t match'
        signatureFailure("Maven", ['abcd': signatureError(IGNORED_KEY)])        | 'Artifact was signed with an ignored key: abcd (not found)'
        signatureFailure("Maven", ['abcd': signatureError(PASSED_NOT_TRUSTED)]) | 'Artifact was signed with key abcd (not found) but this key is not in your trusted key list'
    }

    def "reports multiple verification errors on single configuration"() {
        given:
        renderer.startNewSection(":someConfiguration")
        renderer.startNewArtifact(artifact()) {
            renderer.reportFailure(checksumFailure())
            renderer.reportFailure(missingSignature())
        }
        renderer.startNewArtifact(artifact("com", "acme", "2.0", "acme-2.0.pom")) {
            renderer.reportFailure(onlyIgnoredKeys("Ivy"))
        }

        when:
        generateReport()

        def errors = errorsFor(":someConfiguration")

        then:
        verifyAll(errors[0]) {
            module == 'org:foo:1.0'
            artifact == 'foo-1.0.jar'
            artifactTooltip == "From repository 'Maven'"
            problems == ["Expected a sha256 checksum of 0abcd but was 0000", "Signature file is missing"]
        }
        verifyAll(errors[1]) {
            module == 'com:acme:2.0'
            artifact == 'acme-2.0.pom'
            artifactTooltip == "From repository 'Ivy'"
            problem == "All public keys have been ignored"
        }
    }

    def "reports multiple verification errors on different configurations"() {
        given:
        renderer.startNewSection(":someConfiguration")
        renderer.startNewArtifact(artifact()) {
            renderer.reportFailure(checksumFailure())
            renderer.reportFailure(missingSignature())
        }
        renderer.startNewSection(":other:configuration")
        renderer.startNewArtifact(artifact("com", "acme", "2.0", "acme-2.0.pom")) {
            renderer.reportFailure(onlyIgnoredKeys("Ivy"))
        }

        when:
        generateReport()

        def errors1 = errorsFor(":someConfiguration")
        def errors2 = errorsFor(":other:configuration")

        then:
        verifyAll(errors1[0]) {
            module == 'org:foo:1.0'
            artifact == 'foo-1.0.jar'
            artifactTooltip == "From repository 'Maven'"
            problems == ["Expected a sha256 checksum of 0abcd but was 0000", "Signature file is missing"]
        }
        verifyAll(errors2[0]) {
            module == 'com:acme:2.0'
            artifact == 'acme-2.0.pom'
            artifactTooltip == "From repository 'Ivy'"
            problem == "All public keys have been ignored"
        }
    }

    def "aggregates errors on the same configurations"() {
        given:
        renderer.startNewSection(":someConfiguration")
        renderer.startNewArtifact(artifact()) {
            renderer.reportFailure(checksumFailure())
        }
        renderer.startNewSection(":other:configuration")
        renderer.startNewArtifact(artifact("com", "acme", "2.0", "acme-2.0.pom")) {
            renderer.reportFailure(onlyIgnoredKeys("Ivy"))
        }
        renderer.startNewSection(":someConfiguration")
        renderer.startNewArtifact(artifact()) {
            renderer.reportFailure(missingSignature())
        }

        when:
        generateReport()

        def errors1 = errorsFor(":someConfiguration")
        def errors2 = errorsFor(":other:configuration")

        then:
        verifyAll(errors1[0]) {
            module == 'org:foo:1.0'
            artifact == 'foo-1.0.jar'
            artifactTooltip == "From repository 'Maven'"
            problem == "Expected a sha256 checksum of 0abcd but was 0000"
        }
        verifyAll(errors1[1]) {
            module == 'org:foo:1.0'
            artifact == 'foo-1.0.jar'
            artifactTooltip == "From repository 'Maven'"
            problem == "Signature file is missing"
        }
        verifyAll(errors2[0]) {
            module == 'com:acme:2.0'
            artifact == 'acme-2.0.pom'
            artifactTooltip == "From repository 'Ivy'"
            problem == "All public keys have been ignored"
        }
    }

    private static RepositoryAwareVerificationFailure checksumFailure(String repo = "Maven", ChecksumKind kind = ChecksumKind.sha256, String expected = "0abcd", String actual = "0000") {
        return wrap(repo, new ChecksumVerificationFailure(dummyFile, kind, expected, actual))
    }

    private static RepositoryAwareVerificationFailure missingChecksums(String repo = "Maven") {
        return wrap(repo, new MissingChecksums(dummyFile))
    }

    private static RepositoryAwareVerificationFailure deletedArtifact(String repo = "Maven") {
        return wrap(repo, new DeletedArtifact(dummyFile))
    }

    private static RepositoryAwareVerificationFailure missingSignature(String repo = "Maven") {
        return wrap(repo, new MissingSignature(dummyFile))
    }

    private static RepositoryAwareVerificationFailure onlyIgnoredKeys(String repo = "Maven") {
        return wrap(repo, new OnlyIgnoredKeys(dummyFile))
    }

    private static RepositoryAwareVerificationFailure signatureFailure(String repo = "Maven", Map<String, SignatureVerificationFailure.SignatureError> errors = ['abcd123': signatureError()]) {
        return wrap(repo, new SignatureVerificationFailure(dummyFile, dummyFileSig, errors, new DummyKeyService()))
    }

    private static SignatureVerificationFailure.SignatureError signatureError(SignatureVerificationFailure.FailureKind kind = MISSING_KEY) {
        return new SignatureVerificationFailure.SignatureError(null, kind)
    }

    private static RepositoryAwareVerificationFailure wrap(String repo, VerificationFailure vf) {
        return new RepositoryAwareVerificationFailure(vf, repo)
    }

    private static ModuleComponentArtifactIdentifier artifact(String group = "org", String name = "foo", String version = "1.0", String fileName = "foo-1.0.jar") {
        return new ModuleComponentFileArtifactIdentifier(
            DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId(group, name), version),
            fileName
        )
    }

    private void generateReport() {
        currentReportFile = renderer.writeReport()
        currentReportDir = currentReportFile.parentFile
        Jsoup.parse(currentReportFile, UTF_8.name())
        report = Jsoup.parse(currentReportFile, UTF_8.name())
    }

    private boolean bodyContains(String text) {
        def node = report.body().select("*").find { norm(it).contains(text) }
        node != null // this is just so that we can put a breakpoint for debugging
    }

    private boolean bodyContainsExact(String text) {
        def node = report.body().select("*").find { norm(it) == text }
        node != null // this is just so that we can put a breakpoint for debugging
    }

    private static String norm(Element node) {
        norm(node.text())
    }

    private static String norm(String s) {
        // replaces non-breaking space with space, makes testing easier
        s.replace(' ', ' ').trim()
    }

    private List<ReportedError> errorsFor(String section) {
        Element handle = report.body().select(".uk-accordion-title").find { it.text().startsWith(section) }
        handle.parent().select("tbody tr").collect {
            new ReportedError(it)
        }
    }

    private static class ReportedError {
        final Element row
        final String module
        final String artifact
        final List<String> problems = []
        final boolean hasSignature

        ReportedError(Element row) {
            this.row = row
            def tableDataElements = row.select("td")
            module = norm(tableDataElements.get(0))
            def artifactText = norm(tableDataElements.get(1))
            artifact = artifactText - ' (.asc)'
            hasSignature = artifactText.contains(" (.asc)")
            tableDataElements.get(2).select("p").collect(problems) {
                norm(it)
            }
        }

        String getArtifactTooltip() {
            row.select("td > div[uk-tooltip]").attr("uk-tooltip") - 'title: '
        }

        String getProblem() {
            if (problems.size() != 1) {
                throw new AssertionError("Expected a single problem but there were ${problems.size()}")
            }
            problems[0]
        }
    }

    private static class DummyKeyService implements PublicKeyService {
        @Override
        void findByLongId(long keyId, PublicKeyResultBuilder builder) {

        }

        @Override
        void findByFingerprint(byte[] fingerprint, PublicKeyResultBuilder builder) {

        }

        @Override
        void close() throws IOException {

        }
    }
}
