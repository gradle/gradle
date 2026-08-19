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
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.util.regex.Pattern

import static org.gradle.api.internal.artifacts.verification.verifier.SignatureVerificationFailure.FailureKind.FAILED
import static org.gradle.api.internal.artifacts.verification.verifier.SignatureVerificationFailure.FailureKind.IGNORED_KEY
import static org.gradle.api.internal.artifacts.verification.verifier.SignatureVerificationFailure.FailureKind.MISSING_KEY
import static org.gradle.api.internal.artifacts.verification.verifier.SignatureVerificationFailure.FailureKind.PASSED_NOT_TRUSTED

class HtmlDependencyVerificationReportRendererTest extends Specification {
    static private File dummyFile = new File("dummy")
    static private File dummyFileSig = new File("dummy.asc")

    private static final Pattern TAG = ~/(?s)<[^>]*>/
    private static final Pattern BODY = ~/(?s)<body>(.*)<\/body>/
    private static final Pattern SECTION = ~/(?s)<a class="uk-accordion-title"[^>]*>(.*?)<\/a>(.*?)<\/table>/
    private static final Pattern TABLE_BODY = ~/(?s)<tbody>(.*?)<\/tbody>/
    private static final Pattern ROW = ~/(?s)<tr>(.*?)<\/tr>/
    private static final Pattern CELL = ~/(?s)<td[^>]*>(.*?)<\/td>/
    private static final Pattern TOOLTIP = ~/uk-tooltip="title: ([^"]*)"/
    private static final Pattern PROBLEM = ~/(?s)<p>(.*?)<\/p>/

    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    File verificationFile = temporaryFolder.createFile("verification-metadata.xml")
    File reportsDir = temporaryFolder.testDirectory
    File currentReportDir
    File currentReportFile
    String reportHtml

    @Subject
    HtmlDependencyVerificationReportRenderer noKeyServerRenderer = new HtmlDependencyVerificationReportRenderer(
        Mock(DocumentationRegistry),
        verificationFile,
        ["pgp", "sha512"],
        reportsDir,
        false
    )

    @Subject
    HtmlDependencyVerificationReportRenderer useKeyServerRenderer = new HtmlDependencyVerificationReportRenderer(
        Mock(DocumentationRegistry),
        verificationFile,
        ["pgp", "sha512"],
        reportsDir,
        true
    )

    def "copies required resources"() {
        when:
        generateReport(noKeyServerRenderer)

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
        noKeyServerRenderer.startNewSection("First section")

        when:
        generateReport(noKeyServerRenderer)

        then:
        sectionSummaries() == ["First section 0 error"]

        when:
        noKeyServerRenderer.startNewSection("Second section")
        generateReport(noKeyServerRenderer)

        then:
        sectionSummaries() == ["First section 0 error", "Second section 0 error"]
    }

    @Issue("https://github.com/gradle/gradle/issues/20135")
    @Unroll("reports sticky tip for (#failure) using a key server")
    def "reports sticky tip for (#failure) using a key server"() {
        given:
        useKeyServerRenderer.startNewSection(":someConfiguration")
        useKeyServerRenderer.startNewArtifact(artifact()) {
            useKeyServerRenderer.reportFailure(failure)
        }

        when:
        generateReport(useKeyServerRenderer)

        then:
        bodyContains(stickyTipMessage)

        where:
        failure                                                                 | stickyTipMessage
        checksumFailure()                                                       |'./gradlew --write-verification-metadata pgp,sha512 help'
        missingChecksums()                                                      |'./gradlew --write-verification-metadata pgp,sha512 help'
        deletedArtifact()                                                       |'./gradlew --write-verification-metadata pgp,sha512 help'
        missingSignature()                                                      |'./gradlew --write-verification-metadata pgp,sha512 help'
        onlyIgnoredKeys()                                                       |'./gradlew --write-verification-metadata pgp,sha512 help'
        signatureFailure()                                                      |'./gradlew --write-verification-metadata pgp,sha512 help'
        signatureFailure("Maven", ['abcd': signatureError(FAILED)])             |'./gradlew --write-verification-metadata pgp,sha512 help'
        signatureFailure("Maven", ['abcd': signatureError(IGNORED_KEY)])        |'./gradlew --write-verification-metadata pgp,sha512 help'
        signatureFailure("Maven", ['abcd': signatureError(PASSED_NOT_TRUSTED)]) |'./gradlew --write-verification-metadata pgp,sha512 help'
        signatureFailure("Maven", ['abcd': signatureError(MISSING_KEY)])        |'./gradlew --write-verification-metadata pgp,sha512 help'
    }

    @Issue("https://github.com/gradle/gradle/issues/20135")
    @Unroll("reports sticky tip for (#failure) without using a key server")
    def "reports sticky tip for (#failure) without using a key server"() {
        given:
        noKeyServerRenderer.startNewSection(":someConfiguration")
        noKeyServerRenderer.startNewArtifact(artifact()) {
            noKeyServerRenderer.reportFailure(failure)
        }

        when:
        generateReport(noKeyServerRenderer)

        then:
        bodyContains(stickyTipMessage)

        where:
        failure                                                                 | stickyTipMessage
        checksumFailure()                                                       | './gradlew --write-verification-metadata pgp,sha512 help'
        missingChecksums()                                                      | './gradlew --write-verification-metadata pgp,sha512 help'
        deletedArtifact()                                                       | './gradlew --write-verification-metadata pgp,sha512 help'
        missingSignature()                                                      | './gradlew --write-verification-metadata pgp,sha512 help'
        onlyIgnoredKeys()                                                       | './gradlew --write-verification-metadata pgp,sha512 help'
        signatureFailure()                                                      | './gradlew --write-verification-metadata pgp,sha512 --export-keys help'
        signatureFailure("Maven", ['abcd': signatureError(FAILED)])             | './gradlew --write-verification-metadata pgp,sha512 help'
        signatureFailure("Maven", ['abcd': signatureError(IGNORED_KEY)])        | './gradlew --write-verification-metadata pgp,sha512 help'
        signatureFailure("Maven", ['abcd': signatureError(PASSED_NOT_TRUSTED)]) | './gradlew --write-verification-metadata pgp,sha512 help'
        signatureFailure("Maven", ['abcd': signatureError(MISSING_KEY)])        | './gradlew --write-verification-metadata pgp,sha512 --export-keys help'
    }

    @Unroll("reports verification errors without key server (#failure)")
    def "reports verification errors without key server"() {
        given:
        noKeyServerRenderer.startNewSection(":someConfiguration")
        noKeyServerRenderer.startNewArtifact(artifact()) {
            noKeyServerRenderer.reportFailure(failure)
        }

        when:
        generateReport(noKeyServerRenderer)

        def errors = errorsFor(":someConfiguration")
        then:
        bodyContains("./gradlew --write-verification-metadata")
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
        signatureFailure()                                                      | 'Key abcd123 (not found) couldn\'t be found in local key file so verification couldn\'t be performed. Enable key resolution with --export-keys.'
        signatureFailure("Maven", ['abcd': signatureError(FAILED)])             | 'Artifact was signed with key abcd (not found) but signature didn\'t match'
        signatureFailure("Maven", ['abcd': signatureError(IGNORED_KEY)])        | 'Artifact was signed with an ignored key: abcd (not found)'
        signatureFailure("Maven", ['abcd': signatureError(PASSED_NOT_TRUSTED)]) | 'Artifact was signed with key abcd (not found) but this key is not in your trusted key list'
    }

    @Issue("https://github.com/gradle/gradle/issues/20100")
    def "missing key error includes the other-trusted-keys note in the HTML report"() {
        given:
        def trustedKeys = new SignatureVerificationFailure.TrustedKeys("org", "foo",
            ["AAAA1111AAAA1111AAAA1111AAAA1111AAAA1111"] as Set,
            ["BBBB2222BBBB2222BBBB2222BBBB2222BBBB2222"] as Set)
        noKeyServerRenderer.startNewSection(":someConfiguration")
        noKeyServerRenderer.startNewArtifact(artifact()) {
            noKeyServerRenderer.reportFailure(signatureFailureWithTrustedKeys(trustedKeys))
        }

        when:
        generateReport(noKeyServerRenderer)

        def errors = errorsFor(":someConfiguration")
        then:
        verifyAll(errors[0]) {
            problem == "Key abcd123 (not found) couldn't be found in local key file so verification couldn't be performed. Enable key resolution with --export-keys. (1 other key is already trusted for module 'org:foo'; 1 other key is already trusted for group 'org')"
        }
    }

    @Unroll("reports verification errors with key server (#failure)")
    def "reports verification errors with key server"() {
        given:
        useKeyServerRenderer.startNewSection(":someConfiguration")
        useKeyServerRenderer.startNewArtifact(artifact()) {
            useKeyServerRenderer.reportFailure(failure)
        }

        when:
        generateReport(useKeyServerRenderer)

        def errors = errorsFor(":someConfiguration")
        then:
        bodyContains("./gradlew --write-verification-metadata")
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
        signatureFailure()                                                      | 'Key abcd123 (not found) couldn\'t be found in local key file or remote key servers so verification couldn\'t be performed.'
        signatureFailure("Maven", ['abcd': signatureError(FAILED)])             | 'Artifact was signed with key abcd (not found) but signature didn\'t match'
        signatureFailure("Maven", ['abcd': signatureError(IGNORED_KEY)])        | 'Artifact was signed with an ignored key: abcd (not found)'
        signatureFailure("Maven", ['abcd': signatureError(PASSED_NOT_TRUSTED)]) | 'Artifact was signed with key abcd (not found) but this key is not in your trusted key list'
    }

    def "reports multiple verification errors on single configuration"() {
        given:
        noKeyServerRenderer.startNewSection(":someConfiguration")
        noKeyServerRenderer.startNewArtifact(artifact()) {
            noKeyServerRenderer.reportFailure(checksumFailure())
            noKeyServerRenderer.reportFailure(missingSignature())
        }
        noKeyServerRenderer.startNewArtifact(artifact("com", "acme", "2.0", "acme-2.0.pom")) {
            noKeyServerRenderer.reportFailure(onlyIgnoredKeys("Ivy"))
        }

        when:
        generateReport(noKeyServerRenderer)

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
        noKeyServerRenderer.startNewSection(":someConfiguration")
        noKeyServerRenderer.startNewArtifact(artifact()) {
            noKeyServerRenderer.reportFailure(checksumFailure())
            noKeyServerRenderer.reportFailure(missingSignature())
        }
        noKeyServerRenderer.startNewSection(":other:configuration")
        noKeyServerRenderer.startNewArtifact(artifact("com", "acme", "2.0", "acme-2.0.pom")) {
            noKeyServerRenderer.reportFailure(onlyIgnoredKeys("Ivy"))
        }

        when:
        generateReport(noKeyServerRenderer)

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
        noKeyServerRenderer.startNewSection(":someConfiguration")
        noKeyServerRenderer.startNewArtifact(artifact()) {
            noKeyServerRenderer.reportFailure(checksumFailure())
        }
        noKeyServerRenderer.startNewSection(":other:configuration")
        noKeyServerRenderer.startNewArtifact(artifact("com", "acme", "2.0", "acme-2.0.pom")) {
            noKeyServerRenderer.reportFailure(onlyIgnoredKeys("Ivy"))
        }
        noKeyServerRenderer.startNewSection(":someConfiguration")
        noKeyServerRenderer.startNewArtifact(artifact()) {
            noKeyServerRenderer.reportFailure(missingSignature())
        }

        when:
        generateReport(noKeyServerRenderer)

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

    private static RepositoryAwareVerificationFailure signatureFailureWithTrustedKeys(SignatureVerificationFailure.TrustedKeys trustedKeys, Map<String, SignatureVerificationFailure.SignatureError> errors = ['abcd123': signatureError()], String repo = "Maven") {
        return wrap(repo, new SignatureVerificationFailure(dummyFile, dummyFileSig, errors, new DummyKeyService(), trustedKeys))
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

    private void generateReport(HtmlDependencyVerificationReportRenderer renderer) {
        currentReportFile = renderer.writeReport()
        currentReportDir = currentReportFile.parentFile
        reportHtml = currentReportFile.getText("UTF-8")
    }

    private boolean bodyContains(String text) {
        // extracted into a variable so that we can put a breakpoint for debugging
        def body = textOf(reportHtml.find(BODY) { it[1] })
        body.contains(text)
    }

    /**
     * The rendered title of every section, in report order, e.g. {@code "First section 0 error"}.
     */
    private List<String> sectionSummaries() {
        sections().collect { it.title }
    }

    private List<ReportedError> errorsFor(String section) {
        def match = sections().find { it.title.startsWith(section) }
        assert match != null : "No section titled '$section' in the report"
        match.table.find(TABLE_BODY) { it[1] }.findAll(ROW) { it[1] }.collect { new ReportedError(it) }
    }

    private List<Map<String, String>> sections() {
        reportHtml.findAll(SECTION) { [title: textOf(it[1]), table: it[2]] }
    }

    /**
     * Turns a fragment of the generated report into the text a browser would show for it:
     * markup dropped, entities decoded and whitespace collapsed.
     */
    private static String textOf(String html) {
        html.replaceAll(TAG, ' ')
            .replace('&nbsp;', ' ')
            .replace('&lt;', '<')
            .replace('&gt;', '>')
            .replace('&quot;', '"')
            .replace('&#39;', "'")
            .replace('&amp;', '&')
            .replaceAll(/\s+/, ' ')
            .trim()
    }

    private static class ReportedError {
        final String module
        final String artifact
        final String artifactTooltip
        final List<String> problems

        ReportedError(String row) {
            def cells = row.findAll(CELL) { it[1] }
            module = textOf(cells[0])
            artifact = textOf(cells[1]) - ' (.asc)'
            artifactTooltip = cells[1].find(TOOLTIP) { it[1] }
            problems = cells[2].findAll(PROBLEM) { textOf(it[1]) }
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
