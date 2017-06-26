package org.gradle.build.docs

import groovy.transform.CompileStatic
import groovy.transform.ToString
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.NodeList

import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory


/**
 * This is a Gradle task that runs through the user guide's source XML files
 * and checks that all sections have {@code id} attributes and there are no
 * duplicate section IDs.
 */
@CompileStatic
class UserGuideSectionVerifier extends DefaultTask {
    @InputFiles
    FileCollection docbookFiles

    @Input
    String fileEncoding = "UTF-8"

    @TaskAction
    void verify() {
        Map<String, ValidationResult> allResults = docbookFiles.files.findAll { !it.directory }.collectEntries { f ->
            [f.name, validateSections(f)]
        }

        def duplicateSectionIds = allResults.collectMany { filename, result ->
            result.sectionIds
        }.countBy { id -> id }.findAll { id, count -> count > 1 }.keySet()

        logger.info "Number of user guide sections without IDs: " +
                allResults.inject(0) { int sum, String filename, ValidationResult result ->
            sum + result.missingSectionIdCount
        }

        def exceptionMessage = new StringBuilder()
        if (duplicateSectionIds) {
            appendDuplicatesMessagePart(exceptionMessage, duplicateSectionIds, allResults)
        }
        if (hasMissingSectionIds(allResults)) {
            appendMissingIdsMessagePart(exceptionMessage, allResults)
        }

        if (exceptionMessage.size() > 0) {
            throw new GradleException(exceptionMessage.toString())
        }
    }

    private ValidationResult validateSections(File file) {
        final doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        final xpath = XPathFactory.newInstance().newXPath()

        final sectionsWithoutIds = xpath.compile("//section[count(ancestor::section) < 2 and (not(@id) or string-length(@id)=0)]").evaluate(doc, XPathConstants.NODESET) as NodeList
        final sectionIds = xpath.compile("//section/@id").evaluate(doc, XPathConstants.NODESET) as NodeList

        final idList = new ArrayList(sectionIds.length)
        for (int i in 0..<sectionIds.length) {
            idList << sectionIds.item(i).nodeValue
        }

        return new ValidationResult(
                missingSectionIdCount: sectionsWithoutIds.length,
                sectionIds: idList)
    }

    private boolean hasMissingSectionIds(Map<String, ValidationResult> allResults) {
        return allResults.any { filename, result -> result.missingSectionIdCount }
    }

    private StringBuilder appendMissingIdsMessagePart(StringBuilder msg, Map<String, ValidationResult> allResults) {
        if (allResults.any { filename, result -> result.missingSectionIdCount }) {
            msg << "The following files have sections without IDs"
            msg << " - all level 1 and level 2 sections across the user guide should have IDs:\n\n"
            allResults.findAll { filename, r -> r.missingSectionIdCount }.
                       sort { Map.Entry entry -> entry.key }.
                       each { filename, r ->
                msg << " " * 4
                msg << "- ${filename} (${r.missingSectionIdCount})\n"
            }
            msg << "\n"
        }
        return msg
    }

    private StringBuilder appendDuplicatesMessagePart(
            StringBuilder msg,
            Collection<String> duplicateSectionIds,
            Map<String, ValidationResult> allResults) {
        msg << "The following section IDs have duplicates"
        msg << " - all IDs should be unique across the whole user guide:\n\n"
        duplicateSectionIds.each { id ->
            msg << " " * 4
            msg << "- ${id} (" + allResults.findAll { fn, r -> id in r.sectionIds }.keySet().sort().join(", ") + ")\n"
        }
        msg << "\n"
        return msg
    }

    @ToString
    private static class ValidationResult {
        int missingSectionIdCount
        List<String> sectionIds
    }
}
