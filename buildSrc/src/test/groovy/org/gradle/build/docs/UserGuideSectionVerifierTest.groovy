package org.gradle.build.docs

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

/**
 * Unit test for {@link UserGuideSectionVerifier}.
 */
class UserGuideSectionVerifierTest extends Specification {
    @Rule final TemporaryFolder projectDirProvider = new TemporaryFolder()

    def "Should fail if there are missing section IDs"() {
        given: "Bunch of docbook files in the temporary project directory"
        def projectDir = projectDirProvider.newFolder("missing-ids")
        initMissingIdsDocBookFiles(projectDir)

        and: "A verifier task instance"
        def project = new ProjectBuilder().withProjectDir(projectDir).build()
        def verifierTask = project.tasks.create("verifySectionIds", UserGuideSectionVerifier) { task ->
            task.docbookFiles = project.fileTree(".") {
                include "**/*.xml"
            }
        }

        when: "I execute the task"
        verifierTask.verify()

        then: "An exception is thrown detailing how many missing IDs there are"
        GradleException ex = thrown()
        ex.message.contains("The following files have sections without IDs")
        !ex.message.contains("The following section IDs have duplicates")
        ex.message.contains("- ch1.xml (1)")
        ex.message.contains("- ch2.xml (1)")
        ex.message.contains("- ch3.xml (2)")
    }

    def "Should fail if there are duplicate section IDs"() {
        given: "A set of input files that are all valid"
        def projectDir = projectDirProvider.newFolder("duplicate-ids")
        initDuplicateIdsDocBookFiles(projectDir)

        and: "A task instance configured with those input files"
        def project = new ProjectBuilder().withProjectDir(projectDir).build()
        def verifierTask = project.tasks.create("verifySectionIds", UserGuideSectionVerifier) { task ->
            task.docbookFiles = project.fileTree(".") {
                include "**/*.xml"
            }
        }

        when: "I execute the task"
        verifierTask.verify()

        then: "An exception is thrown detailing "
        GradleException ex = thrown()
        ex.message.contains("The following section IDs have duplicates")
        !ex.message.contains("The following files have sections without IDs")
        ex.message.contains("- first (ch4.xml, ch5.xml, ch6.xml)")
        ex.message.contains("- second (ch5.xml, ch6.xml)")
        !ex.message.contains("- Something")
        !ex.message.contains("- apples")
    }

    def "Should fail if there are both duplicate and missing IDs"() {
        given: "A set of input files that are all valid"
        def projectDir = projectDirProvider.newFolder("duplicate-ids")
        initMissingIdsDocBookFiles(projectDir)
        initDuplicateIdsDocBookFiles(projectDir)

        and: "A task instance configured with those input files"
        def project = new ProjectBuilder().withProjectDir(projectDir).build()
        def verifierTask = project.tasks.create("verifySectionIds", UserGuideSectionVerifier) { task ->
            task.docbookFiles = project.fileTree(".") {
                include "**/*.xml"
            }
        }

        when: "I execute the task"
        verifierTask.verify()

        then: "An exception is thrown detailing "
        GradleException ex = thrown()
        ex.message.contains("The following section IDs have duplicates")
        ex.message.contains("The following files have sections without IDs")
        ex.message.contains("- ch1.xml (1)")
        ex.message.contains("- first (ch1.xml, ch4.xml, ch5.xml, ch6.xml)")
    }

    def "Should pass if all input files are valid"() {
        given: "A set of input files that are all valid"
        def projectDir = projectDirProvider.newFolder("valid-inputs")
        initValidDocBookFiles(projectDir)

        and: "A task instance configured with those input files"
        def project = new ProjectBuilder().withProjectDir(projectDir).build()
        def verifierTask = project.tasks.create("verifySectionIds", UserGuideSectionVerifier) { task ->
            task.docbookFiles = project.fileTree(".") {
                include "**/*.xml"
            }
        }

        expect: "The verification doesn't throw an exception"
        verifierTask.verify()
    }

    def "Should pass if there are no input files"() {
        given: "A task instance configured with an empty collection of input files"
        def project = new ProjectBuilder().withProjectDir(projectDirProvider.newFolder("no-inputs")).build()
        def verifierTask = project.tasks.create("verifySectionIds", UserGuideSectionVerifier) { task ->
            task.docbookFiles = project.files()
        }

        expect: "The verification doesn't throw an exception"
        verifierTask.verify()
    }

    private initMissingIdsDocBookFiles(File projectDir) {
        new File(projectDir, "ch1.xml").setText("""
<chapter id="ch1">
    <title>Chapter One</title>
    <section id="first"></section>
    <section></section>
</chapter>
""", "UTF-8")

        new File(projectDir, "ch2.xml").setText("""
<chapter id="ch2">
    <title>Chapter Two</title>
    <section id="oranges">
        <title>Something</title>
        <section id="">
             <title>Sub section</title>
             <!-- This is fine because it's not a level 1 or 2 section, so doesn't require an ID -->
             <section></section>
        </section>
    </section>
</chapter>
""", "UTF-8")

        def deepFile = new File(projectDir, "src/docs/ch3.xml")
        deepFile.parentFile.mkdirs()
        deepFile.setText("""\
<chapter id="ch3">
    <title>Chapter Three</title>
    <section id="apples">
        <title>Something</title>
        <section>
             <title>Sub section</title>
        </section>
    </section>
    <section></section>
</chapter>
""", "UTF-8")
    }

    private initDuplicateIdsDocBookFiles(File projectDir) {
        new File(projectDir, "ch4.xml").setText("""\
<chapter id="ch4">
    <title>Chapter Four</title>
    <section id="first"></section>
</chapter>
""", "UTF-8")

        new File(projectDir, "ch5.xml").setText("""\
<chapter id="ch5">
    <title>Chapter Five</title>
    <section id="first">
        <title>Something</title>
        <section id="second">
             <title>Sub section</title>
        </section>
    </section>
</chapter>
""", "UTF-8")

        def deepFile = new File(projectDir, "src/docs/ch6.xml")
        deepFile.parentFile.mkdirs()
        deepFile.setText("""\
<chapter id="ch6">
    <title>Chapter Six</title>
    <section id="apples">
        <title>Something</title>
        <section id="second">
             <title>Sub section</title>
             <section id="first"></section>
        </section>
    </section>
    <section id="second"></section>
</chapter>
""", "UTF-8")
    }

    private initValidDocBookFiles(File projectDir) {
        new File(projectDir, "ch7.xml").setText("""
<chapter id="ch7">
    <title>Chapter Seven</title>
    <section id="first"></section>
    <section id="other"></section>
</chapter>
""", "UTF-8")

        new File(projectDir, "ch8.xml").setText("""
<chapter id="ch8">
    <title>Chapter Eight</title>
    <section id="oranges">
        <title>Something</title>
        <section id="subsection">
             <title>Sub section</title>
             <!-- This is fine because it's not a level 1 or 2 section, so doesn't require an ID -->
             <section></section>
        </section>
    </section>
</chapter>
""", "UTF-8")

        def deepFile = new File(projectDir, "src/docs/ch9.xml")
        deepFile.parentFile.mkdirs()
        deepFile.setText("""\
<chapter id="ch9">
    <title>Chapter Nine</title>
    <section id="apples">
        <title>Something</title>
        <section id="figs">
             <title>Sub section</title>
        </section>
    </section>
    <section id="other2"></section>
</chapter>
""", "UTF-8")
    }
}
