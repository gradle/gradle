/*
 * Copyright 2025 the original author or authors.
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
package gradlebuild.docs.dsl.docbook

import gradlebuild.docs.dsl.source.model.ClassMetaData
import gradlebuild.docs.dsl.source.model.MethodMetaData
import gradlebuild.docs.dsl.source.model.PropertyMetaData
import gradlebuild.docs.model.SimpleClassMetaDataRepository
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import javax.xml.parsers.DocumentBuilderFactory

/**
 * Analyzes the gap between existing per-class DSL XML files and what auto-generation
 * from metadata would produce. Reports members that need {@code @gradle.dsl.hidden}
 * and members that need Javadoc added.
 */
abstract class DslMigrationAnalysisTask extends DefaultTask {

    @PathSensitive(PathSensitivity.NONE)
    @InputFile
    abstract RegularFileProperty getClassMetaDataFile()

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputDirectory
    abstract DirectoryProperty getClassDocbookDirectory()

    @OutputFile
    abstract RegularFileProperty getReportFile()

    @TaskAction
    def analyze() {
        SimpleClassMetaDataRepository<ClassMetaData> repository = new SimpleClassMetaDataRepository<>()
        repository.load(classMetaDataFile.get().asFile)

        def xmlDir = classDocbookDirectory.get().asFile
        def xmlFiles = xmlDir.listFiles({ File f -> f.name.startsWith("org.") && f.name.endsWith(".xml") } as FileFilter)
            ?.toList()?.sort { it.name } ?: []

        def report = new StringBuilder()
        def summary = new MigrationSummary()

        for (File xmlFile : xmlFiles) {
            def className = xmlFile.name.replace('.xml', '')
            def classMetaData = repository.find(className)
            if (classMetaData == null) {
                report.append("SKIP $className: no metadata found\n")
                summary.skipped++
                continue
            }

            def xmlMembers = parseXmlMembers(xmlFile)
            def autoMembers = computeAutoMembers(classMetaData)

            def extraInMeta = autoMembers.properties - xmlMembers.properties
            def missingJavadoc = xmlMembers.properties - autoMembers.properties
            def extraMethodsInMeta = autoMembers.methods - xmlMembers.methods
            def missingMethodJavadoc = xmlMembers.methods - autoMembers.methods

            boolean hasDefaultValues = xmlMembers.hasDefaultValues
            boolean isClean = extraInMeta.isEmpty() && missingJavadoc.isEmpty() &&
                extraMethodsInMeta.isEmpty() && missingMethodJavadoc.isEmpty()

            if (isClean && !hasDefaultValues) {
                summary.readyToMigrate++
                summary.readyClasses << className
            } else if (isClean && hasDefaultValues) {
                summary.readyWithDefaults++
            } else {
                summary.needsWork++
            }

            if (!isClean || hasDefaultValues) {
                report.append("\n=== $className ===\n")
                if (hasDefaultValues) {
                    report.append("  HAS DEFAULT VALUE COLUMNS (will be dropped)\n")
                }
                if (!extraInMeta.isEmpty()) {
                    report.append("  Need @gradle.dsl.hidden (in metadata but NOT in XML):\n")
                    extraInMeta.each { report.append("    - property: $it\n") }
                }
                if (!missingJavadoc.isEmpty()) {
                    report.append("  Need Javadoc (in XML but NOT in auto-generated):\n")
                    missingJavadoc.each { report.append("    - property: $it\n") }
                }
                if (!extraMethodsInMeta.isEmpty()) {
                    report.append("  Need @gradle.dsl.hidden (methods in metadata but NOT in XML):\n")
                    extraMethodsInMeta.each { report.append("    - method: $it\n") }
                }
                if (!missingMethodJavadoc.isEmpty()) {
                    report.append("  Need Javadoc (methods in XML but NOT in auto-generated):\n")
                    missingMethodJavadoc.each { report.append("    - method: $it\n") }
                }
            }
        }

        def header = new StringBuilder()
        header.append("DSL Migration Analysis Report\n")
        header.append("=============================\n")
        header.append("Total XML files: ${xmlFiles.size()}\n")
        header.append("Ready to migrate (exact match): ${summary.readyToMigrate}\n")
        header.append("Ready (with default values to drop): ${summary.readyWithDefaults}\n")
        header.append("Needs work: ${summary.needsWork}\n")
        header.append("Skipped (no metadata): ${summary.skipped}\n")
        header.append("\n--- Ready to migrate classes ---\n")
        summary.readyClasses.each { header.append("  $it\n") }
        header.append("\n--- Details for classes needing work ---\n")

        def outputFile = reportFile.get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.text = header.toString() + report.toString()

        logger.lifecycle("Migration analysis written to: ${outputFile}")
        logger.lifecycle("Ready to migrate: ${summary.readyToMigrate}, Ready with defaults: ${summary.readyWithDefaults}, Needs work: ${summary.needsWork}, Skipped: ${summary.skipped}")
    }

    private static XmlMembers parseXmlMembers(File xmlFile) {
        def factory = DocumentBuilderFactory.newInstance()
        def builder = factory.newDocumentBuilder()
        def doc = builder.parse(xmlFile)
        def root = doc.documentElement

        def members = new XmlMembers()
        def sections = root.getElementsByTagName("section")
        for (int s = 0; s < sections.length; s++) {
            def section = sections.item(s)
            def titles = section.getChildNodes()
            String sectionTitle = null
            for (int t = 0; t < titles.length; t++) {
                def node = titles.item(t)
                if (node.nodeName == "title") {
                    sectionTitle = node.textContent.trim()
                    break
                }
            }
            if (sectionTitle == null) continue

            // Check for Default value column in thead
            def tables = section.getElementsByTagName("table")
            if (tables.length == 0) continue
            def table = tables.item(0)
            def theads = table.getElementsByTagName("thead")
            if (theads.length > 0) {
                def headerTds = theads.item(0).getElementsByTagName("td")
                for (int h = 0; h < headerTds.length; h++) {
                    if (headerTds.item(h).textContent.trim().contains("Default value")) {
                        members.hasDefaultValues = true
                    }
                }
            }

            // Collect member names from data rows
            def trs = table.getElementsByTagName("tr")
            for (int r = 0; r < trs.length; r++) {
                def tr = trs.item(r)
                if (tr.parentNode.nodeName == "thead") continue
                def tds = tr.getElementsByTagName("td")
                if (tds.length > 0) {
                    def name = tds.item(0).textContent.trim()
                    if (sectionTitle == "Properties") {
                        members.properties << name
                    } else if (sectionTitle == "Methods") {
                        members.methods << name
                    }
                }
            }
        }
        return members
    }

    private static AutoMembers computeAutoMembers(ClassMetaData classMetaData) {
        def members = new AutoMembers()

        // Collect property method names to exclude from methods
        def propertyMethodNames = new TreeSet<String>()
        for (PropertyMetaData property : classMetaData.declaredProperties) {
            if (property.getter != null) propertyMethodNames.add(property.getter.name)
            if (property.setter != null) propertyMethodNames.add(property.setter.name)
        }

        for (PropertyMetaData property : classMetaData.declaredProperties) {
            if (property.dslHidden) continue
            if (property.rawCommentText == null || property.rawCommentText.trim().isEmpty()) continue
            members.properties << property.name
        }

        for (MethodMetaData method : classMetaData.declaredMethods) {
            if (method.dslHidden) continue
            if (method.rawCommentText == null || method.rawCommentText.trim().isEmpty()) continue
            if (propertyMethodNames.contains(method.name)) continue
            members.methods << method.name
        }

        return members
    }

    private static class XmlMembers {
        Set<String> properties = new TreeSet<>()
        Set<String> methods = new TreeSet<>()
        boolean hasDefaultValues = false
    }

    private static class AutoMembers {
        Set<String> properties = new TreeSet<>()
        Set<String> methods = new TreeSet<>()
    }

    private static class MigrationSummary {
        int readyToMigrate = 0
        int readyWithDefaults = 0
        int needsWork = 0
        int skipped = 0
        List<String> readyClasses = []
    }
}
