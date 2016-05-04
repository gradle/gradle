package org.gradle.build.docs

import groovy.transform.CompileStatic
import groovy.transform.ToString
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Document
import org.w3c.dom.NodeList

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPath
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory


/**
 * Created by pledbrook on 03/05/2016.
 */
@CompileStatic
class UserGuideSectionVerifierTask extends DefaultTask {
    @InputFiles
    FileCollection docbookFiles

    @Input
    String fileEncoding = "UTF-8"

    @TaskAction
    void verify() {
        Map<String, ValidationResult> allResults = docbookFiles.collectEntries { f ->
            [f.name, this.validateSections(f)]
        }

        def duplicateSectionIds = allResults.collectMany { filename, result ->
            result.sectionIds
        }.countBy { id -> id }.findAll { id, count -> count > 1 }.keySet()

        logger.info "Number of user guide sections without IDs: " +
                allResults.inject(0) { int sum, String filename, ValidationResult result ->
            sum + result.missingSectionIdCount
        }

        if (duplicateSectionIds || allResults.any { filename, result -> result.missingSectionIdCount }) {
            throw new GradleException(generateExceptionMessage(duplicateSectionIds, allResults))
        }
    }

    public ValidationResult validateSections(File file) {
        final doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        final xpath = XPathFactory.newInstance().newXPath()

        final sectionsWithoutIds = xpath.compile("/*/section[not(@id)]").evaluate(doc, XPathConstants.NODESET) as NodeList
        final sectionIds = xpath.compile("/*/section/@id").evaluate(doc, XPathConstants.NODESET) as NodeList

        final idList = new ArrayList(sectionIds.length)
        for (int i in 0..<sectionIds.length) {
            idList << sectionIds.item(i).nodeValue
        }

        return new ValidationResult(
                missingSectionIdCount: sectionsWithoutIds.length,
                sectionIds: idList)
    }

    private String generateExceptionMessage(
            Collection<String> duplicateSectionIds,
            Map<String, ValidationResult> allResults) {
        def msg = new StringBuilder()
        if (duplicateSectionIds) {
            msg << "The following section IDs have duplicates"
            msg << " - all IDs should be unique across the whole user guide:\n\n"
            duplicateSectionIds.each { id ->
                msg << " " * 4
                msg << "- ${id} (" + allResults.findAll { fn, r -> id in r.sectionIds }.keySet().join(", ") + ")\n"
            }
            msg << "\n"
        }

        if (allResults.any { filename, result -> result.missingSectionIdCount }) {
            msg << "The following files have sections without IDs"
            msg << " - all sections across the user guide should have IDs:\n\n"
            allResults.findAll { fn, r -> r.missingSectionIdCount }.sort { Map.Entry entry -> entry.key }.each { fn, r ->
                msg << " " * 4
                msg << "- ${fn} (${r.missingSectionIdCount})\n"
            }
            msg << "\n"
        }
        return msg.toString()
    }

    @ToString
    private static class ValidationResult {
        int missingSectionIdCount
        List<String> sectionIds
    }
}
