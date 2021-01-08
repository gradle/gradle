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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.report;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang.StringEscapeUtils;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.RepositoryAwareVerificationFailure;
import org.gradle.api.internal.artifacts.verification.verifier.ChecksumVerificationFailure;
import org.gradle.api.internal.artifacts.verification.verifier.DeletedArtifact;
import org.gradle.api.internal.artifacts.verification.verifier.MissingChecksums;
import org.gradle.api.internal.artifacts.verification.verifier.MissingSignature;
import org.gradle.api.internal.artifacts.verification.verifier.OnlyIgnoredKeys;
import org.gradle.api.internal.artifacts.verification.verifier.SignatureVerificationFailure;
import org.gradle.api.internal.artifacts.verification.verifier.VerificationFailure;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

/**
 * Generates an HTML report for verification. This report, unlike the text report,
 * is cumulative, meaning that it keeps state and will be incrementally feeded with
 * new contents.
 */
class HtmlDependencyVerificationReportRenderer implements DependencyVerificationReportRenderer {
    private final Map<String, Section> sections = Maps.newTreeMap();
    private Section currentSection;
    private final StringBuilder contents = new StringBuilder();
    private final DocumentationRegistry documentationRegistry;
    private final File verificationFile;
    private final List<String> writeFlags;
    private final File htmlReportOutputDirectory;

    HtmlDependencyVerificationReportRenderer(DocumentationRegistry documentationRegistry, File verificationFile, List<String> writeFlags, File htmlReportOutputDirectory) {
        this.documentationRegistry = documentationRegistry;
        this.verificationFile = verificationFile;
        this.writeFlags = writeFlags;
        this.htmlReportOutputDirectory = htmlReportOutputDirectory;
    }

    @Override
    public void startNewSection(String title) {
        currentSection = sections.get(title);
        if (currentSection == null) {
            currentSection = new Section(title);
            this.sections.put(title, currentSection);
        }
    }

    @Override
    public void startArtifactErrors(Runnable action) {
        action.run();
    }

    @Override
    public void startNewArtifact(ModuleComponentArtifactIdentifier key, Runnable action) {
        currentSection.newArtifact(new ArtifactErrors(key));
        action.run();

    }

    @Override
    public void reportFailure(RepositoryAwareVerificationFailure failure) {
        currentSection.currentArtifact.addFailure(failure);
    }

    @Override
    public void reportAsMultipleErrors(Runnable action) {
        action.run();
    }

    @Override
    public void finish(VerificationHighLevelErrors highLevelErrors) {

    }

    public void renderNavBar() {
        contents.append("<nav class=\"uk-navbar-container\" uk-navbar>\n" +
            "    <div class=\"uk-navbar-left\">\n" +
            "        <a href=\"\" class=\"uk-navbar-item uk-logo\"><img src=\"img/gradle-logo.png\" width=\"120\"></a>\n" +
            "        <ul class=\"uk-navbar-nav\">\n" +
            "<li class=\"uk-active\"><a href=\"#\">Dependency verification report</a></li>\n" +
            "        </ul>\n" +
            "    </div>\n" +
            "</nav>\n" +
            "\n");
    }

    public void renderSections() {
        contents.append("<div class=\"uk-container uk-container-expand\">\n");
        contents.append("        <ul uk-accordion>\n");
        boolean first = true;
        for (Section section : sections.values()) {
            if (first) {
                contents.append("            <li class=\"uk-open\">\n");
            } else {
                contents.append("            <li>\n");
            }
            prerenderSection(section);
            renderSection(section);
            contents.append("            </li>\n");
            first = false;
        }
        contents.append("         </ul>\n");
        contents.append("        </div>\n");
    }

    File writeReport() {
        generateContent();
        ensureReportDirectoryCreated();
        copyReportResources();
        return doWriteReport();
    }

    private void ensureReportDirectoryCreated() {
        htmlReportOutputDirectory.mkdirs();
    }

    private void copyReportResources() {
        copyReportResource(htmlReportOutputDirectory, "css", "uikit.min.css");
        copyReportResource(htmlReportOutputDirectory, "js", "uikit.min.js");
        copyReportResource(htmlReportOutputDirectory, "js", "uikit-icons.min.js");
        copyReportResource(htmlReportOutputDirectory, "img", "gradle-logo.png");
    }

    private File doWriteReport() {
        File reportFile = new File(htmlReportOutputDirectory, "dependency-verification-report.html");
        try (Writer prn = new OutputStreamWriter(new FileOutputStream(reportFile, false), StandardCharsets.UTF_8)) {
            prn.write(contents.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return reportFile;
    }

    private void generateContent() {
        contents.setLength(0);
        contents.append("<!DOCTYPE html>\n" +
            "<html>\n" +
            "    <head>\n" +
            "        <title>Dependency verification report</title>\n" +
            "        <meta charset=\"utf-8\">\n" +
            "        <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" +
            "        <link rel=\"stylesheet\" href=\"css/uikit.min.css\" />\n" +
            "        <script src=\"js/uikit.min.js\"></script>\n" +
            "        <script src=\"js/uikit-icons.min.js\"></script>\n" +
            "    </head>\n" +
            "    <body>\n");
        renderNavBar();
        renderSections();
        registerModals();
        registerStickyTip();
        contents.append("    </body>\n" +
            "</html>\n" +
            "\n");
    }

    private void registerStickyTip() {
        contents.append("    <div class=\"uk-container uk-padding\">\n");
        contents.append("        <div class=\"uk-card uk-card-default uk-card-body\" style=\"z-index: 980;\" uk-sticky=\"bottom: true\">\n");
        contents.append("            <h2 class=\"uk-modal-title\">Troubleshooting</h2>\n");
        contents.append("            <p>Please review the errors reported above carefully.");
        contents.append("            Click on the icons near to the error descriptions for information about how to fix a particular problem.");
        contents.append("            It is recommended that you edit the ").append(verificationFileLink()).append(" manually. ");
        contents.append("            However, if you are confident that those are false positives, Gradle can help you by generating the missing verification metadata.");
        contents.append("            In this case, you can run with the following command-line:</p>");
        contents.append("            <pre>gradle --write-verification-metadata ").append(verificationOptions()).append(" help</pre>");
        contents.append("            <p>In any case you <b>must review the result</b> of this operation.");
        contents.append("            <p>Please refer to the <a href=\"").append(documentationRegistry.getDocumentationFor("dependency_verification")).append("\" target=\"_blank\">documentation</a> for more information.</p>\n");
        contents.append("        </div>\n");
        contents.append("    </div>\n");
    }

    private String verificationOptions() {
        return String.join(",", writeFlags);
    }

    private String verificationFileLink() {
        return "<a href=\"" + verificationFile.toURI().toASCIIString() + "\">verification file</a>";
    }

    private void registerModal(String id, String title, String... lines) {
        contents.append("<div id=\"").append(id).append("\" uk-modal>\n")
            .append("    <div class=\"uk-modal-dialog uk-modal-body\">\n")
            .append("        <h2 class=\"uk-modal-title\">")
            .append(title)
            .append("</h2>\n");
        for (String line : lines) {
            contents.append(line).append("\n");
        }
        contents.append("        <p>See the <a href=\"").append(documentationRegistry.getDocumentationFor("dependency_verification", "sec:signature-verification")).append("\" target=\"_blank\">documentation</a> to get more information.</p>\n")
            .append("        <button class=\"uk-button uk-button-primary uk-modal-close\" type=\"button\">Ok</button>\n")
            .append("    </div>\n")
            .append("</div>\n");
    }

    private void registerModals() {
        registerModal("verified-not-trusted", "Key isn't trusted",
            "        <p>This indicates that a dependency was <i>verified</i>, that the signature matched, but that you don't <i>trust</i> this signature.</p>\n",
            "        <p>If you trust the author of the signature, you need to add the key to the trusted keys.</p>");
        registerModal("signature-didnt-match", "Signature didn't match",
            "        <p>This indicates that a dependency was <i>signed</i> but that the signature verification <b>failed</b>.</p>",
            "        <p>This happens when a dependency was compromised or that the signature was made for a different artifact than the one you got.</p>",
            "        <p>It's important that you <b>carefully review this problem</b>.</p>");
        registerModal("ignored-key", "A key was ignored",
            "        <p>This indicates that a dependency was <i>signed with an ignored key</i>.</p>",
            "        <p>You must provide at least one checksum so that verification can pass.</p>");
        registerModal("missing-key", "Public key couldn't be found",
            "        <p>This indicates that a dependency was <i>signed</i> but that Gradle couldn't download the public key to verify the signature.</p>",
            "        <p>You should check if the key is valid, and if so, provide a key server where to download it.</p>");
        registerModal("missing-checksums", "Checksums are missing",
            "        <p>This indicates that the dependency verification file doesn't contain at least one checksum for this artifact.</p>",
            "        <p>You must provide at least one checksum for artifact verification to pass.</p>");
        registerModal("checksum-mismatch", "Incorrect checksum",
            "        <p>This indicates that the dependency verification file <b>failed</b> because the actual checksum of the dependency artifact didn't match the expected checksum declared in the verification metadata.</p>",
            "        <p>This happens when a dependency was compromised or that downloaded artifact isn't the one that you expected.</p>",
            "        <p>It's important that you <b>carefully review this problem</b>.</p>");
        registerModal("deleted-artifact", "Deleted artifact",
            "        <p>This error usually indicated that the local dependency cache was tampered with.</p>",
            "        <p>This happens when someone manually deletes an artifact from the Gradle dependency cache, which is now corrupt.</p>");
        registerModal("signature-file-missing", "Missing signature file",
            "        <p>The signature file for this artifact wasn't found.</p>",
            "        <p>Usually it indicates that the signature doesn't exist in the repository the artifact was downloaded from.</p>",
            "        <p>In general this is not a problem but you should then declare at least one checksum for verification to pass.</p>");
    }

    private void copyReportResource(File outputDirectory, String dirName, String fileName) {
        File targetDir = new File(outputDirectory, dirName);
        targetDir.mkdirs();
        copyResource(fileName, new File(targetDir, fileName));
    }

    private void copyResource(String name, File outputFile) {
        try (InputStream in = getClass().getResourceAsStream(name)) {
            Files.copy(in, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void prerenderSection(Section section) {
        int size = section.errors.size();
        contents.append("<a class=\"uk-accordion-title\" href=\"#\"><span uk-icon=\"chevron-right\"></span>").append(section.title)
            .append("&nbsp;<span class=\"uk-badge\">")
            .append(size)
            .append(size < 2 ? " error" : " errors")
            .append("</span></a>");
    }

    private void renderSection(Section section) {
        contents.append("<div class=\"uk-accordion-content\">")
            .append("<table class=\"uk-table uk-table-hover uk-table-divider uk-table-middle uk-table-small\">\n")
            .append("    <thead>\n")
            .append("        <tr>\n")
            .append("            <th class=\"uk-table-shrink uk-width-auto uk-text-nowrap\">Module</th>\n")
            .append("            <th class=\"uk-table-shrink uk-width-auto uk-text-nowrap\">Artifact</th>\n")
            .append("            <th class=\"uk-table-shrink uk-width-expand\">Problem(s)</th>\n")
            .append("        </tr>\n")
            .append("    </thead>\n")
            .append("    <tbody>\n");
        section.errors.forEach(this::formatErrors);
        contents.append("    </tbody>\n" +
            "</table>")
            .append("</div>");
    }

    private void formatErrors(ArtifactErrors currentArtifact) {
        contents.append("        <tr>\n");
        RepositoryAwareVerificationFailure firstFailure = currentArtifact.failures.get(0);
        reportItem(currentArtifact.key.getComponentIdentifier().getDisplayName());
        reportItem(createFileLink(currentArtifact.key, firstFailure.getFailure(), firstFailure.getRepositoryName()));
        contents.append("            <td>\n");
        currentArtifact.failures.forEach(this::formatError);
        contents.append("            </td>\n");
        contents.append("        </tr>\n");
    }

    private void formatError(RepositoryAwareVerificationFailure failure) {
        VerificationFailure vf = failure.getFailure();
        reportSignatureProblems(vf);
        reportChecksumProblems(vf);
        reportOtherProblems(vf);
    }

    private String createFileLink(ModuleComponentArtifactIdentifier key, VerificationFailure vf, String repositoryName) {
        String fileLink = "<div uk-tooltip=\"title: From repository '" + repositoryName + "'\">";
        fileLink += "<a href=\"" + vf.getFilePath().toURI().toASCIIString() + "\">" + key.getFileName() + "</a>";
        if (vf instanceof SignatureVerificationFailure) {
            File signatureFile = ((SignatureVerificationFailure) vf).getSignatureFile();
            if (signatureFile != null) {
                fileLink += "&nbsp;<a href=\"" + signatureFile.toURI().toASCIIString() + "\">(.asc)</a>";
            }
        }
        fileLink += "</div>";
        return fileLink;
    }

    private void reportOtherProblems(VerificationFailure vf) {
        if (vf instanceof DeletedArtifact) {
            String reason = "Artifact has been deleted from dependency cache";
            reportItem(reason, "deleted-artifact", "info");
        }
    }

    private void reportChecksumProblems(VerificationFailure vf) {
        if (vf instanceof MissingChecksums) {
            String reason = warning("Checksums are missing from verification metadata");
            reportItem(reason, "missing-checksums", "info");
        } else if (vf instanceof ChecksumVerificationFailure) {
            ChecksumVerificationFailure cvf = (ChecksumVerificationFailure) vf;
            String reason = "Expected a " + cvf.getKind() + " checksum of " + expected(cvf.getExpected()) + " but was " + actual(cvf.getActual());
            reportItem(reason, "checksum-mismatch", "warning");
        } else if (vf instanceof OnlyIgnoredKeys) {
            String reason = "All public keys have been ignored";
            reportItem(reason, "missing-checksums", "info");
        }
    }

    private static String expected(String text) {
        return emphatize(text, "blue");
    }

    private static String actual(String text) {
        return emphatize(text, "#ee442f");
    }

    private static String warning(String text) {
        return emphatize(text, "#c59434");
    }

    private static String grey(String text) {
        return emphatize(text, "#cccccc");
    }

    private static String emphatize(String text, String color) {
        return "<span style=\"font-weight:bold; color: " + color + "\">" + text + "</span>";
    }

    private void reportSignatureProblems(VerificationFailure vf) {
        if (vf instanceof MissingSignature) {
            reportItem("Signature file is missing", "signature-file-missing", "info");
        } else if (vf instanceof SignatureVerificationFailure) {
            SignatureVerificationFailure svf = (SignatureVerificationFailure) vf;
            Map<String, SignatureVerificationFailure.SignatureError> errors = svf.getErrors();
            errors.forEach((keyId, error) -> {
                StringBuilder sb = new StringBuilder();
                PGPPublicKey publicKey = error.getPublicKey();
                if (publicKey != null) {
                    svf.appendKeyDetails(sb, publicKey);
                } else {
                    sb.append("(not found)");
                }
                String keyDetails = StringEscapeUtils.escapeHtml(sb.toString());
                String keyInfo = "<b>" + keyId + " " + keyDetails + "</b>";
                switch (error.getKind()) {
                    case PASSED_NOT_TRUSTED:
                        String reason = warning("Artifact was signed with key " + keyInfo + " but this key is not in your trusted key list");
                        reportItem(reason, "verified-not-trusted", "question");
                        break;
                    case FAILED:
                        reason = actual("Artifact was signed with key " + keyInfo + " but signature didn't match");
                        reportItem(reason, "signature-didnt-match", "warning");
                        break;
                    case IGNORED_KEY:
                        reason = grey("Artifact was signed with an ignored key: " + keyInfo);
                        reportItem(reason, "ignored-key", "info");
                        break;
                    case MISSING_KEY:
                        reason = warning("Key " + keyInfo + " couldn't be found in any key server so verification couldn't be performed");
                        reportItem(reason, "missing-key", "warning");
                        break;
                }
            });
        }
    }

    private void reportItem(String item) {
        contents.append("            <td class=\"uk-text-nowrap\"");
        contents.append(">").append(item).append("</td>\n");
    }

    private void reportItem(String item, String tipTarget, String tipIcon) {
        String tip = "<a uk-toggle=\"target: #" + tipTarget + "\" uk-icon=\"icon: " + tipIcon + "\"></a>";
        contents.append("            <p>").append(item).append("&nbsp;").append(tip).append("</p>\n");
    }

    private static class Section {
        private final String title;
        private final List<ArtifactErrors> errors = Lists.newArrayList();
        private ArtifactErrors currentArtifact;

        private Section(String title) {
            this.title = title;
        }

        public void newArtifact(ArtifactErrors artifactErrors) {
            errors.add(artifactErrors);
            currentArtifact = artifactErrors;
        }

        public String getTitle() {
            return title;
        }

    }

    private static class ArtifactErrors {
        final ModuleComponentArtifactIdentifier key;
        final List<RepositoryAwareVerificationFailure> failures = Lists.newArrayList();

        private ArtifactErrors(ModuleComponentArtifactIdentifier key) {
            this.key = key;
        }

        void addFailure(RepositoryAwareVerificationFailure failure) {
            failures.add(failure);
        }
    }

}
