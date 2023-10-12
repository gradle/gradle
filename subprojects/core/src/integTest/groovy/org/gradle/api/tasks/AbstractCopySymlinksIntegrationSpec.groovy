/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.api.file.LinksStrategy
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

import java.nio.file.Files

import static java.nio.file.LinkOption.NOFOLLOW_LINKS

@Requires(UnitTestPreconditions.Symlinks)
abstract class AbstractCopySymlinksIntegrationSpec extends AbstractIntegrationSpec {

    final String mainTask = "doWork"

    abstract TestFile getResultDir()

    abstract String constructBuildScript(String inputConfig)

    TestFile inputDirectory

    def setup() {
        inputDirectory = createDir("input")
        executer.withStacktraceEnabled()
    }

    def "symlinked files should be copied as #hint if linksStrategy=#linksStrategy"() {
        given:
        def originalFile = inputDirectory.createFile("original.txt") << "some text"
        def link = inputDirectory.file("link").createLink(originalFile.getRelativePathFromBase())

        buildKotlinFile << constructBuildScript(
            """
            ${configureStrategy(linksStrategy)}
            from("${inputDirectory.name}")
            """
        )

        when:
        succeeds(mainTask)
        def outputDirectory = getResultDir()

        then:
        def originalCopy = outputDirectory.file(originalFile.name)
        isCopy(originalCopy, originalFile)

        def linkCopy = outputDirectory.file(link.name)
        "$expectedOutcome"(linkCopy, originalCopy)

        where:
        linksStrategy                   | expectedOutcome  | hint
        LinksStrategy.PRESERVE_ALL      | "isValidSymlink" | "valid symlink"
        LinksStrategy.PRESERVE_RELATIVE | "isValidSymlink" | "valid symlink"
        LinksStrategy.FOLLOW            | "isCopy"         | "full copy"
        null                            | "isCopy"         | "full copy"
    }

    def "symlinked files should be reported as error if linksStrategy is ERROR"() {
        given:
        def originalFile = inputDirectory.createFile("original.txt") << "some text"
        def link = inputDirectory.file("link").createLink(originalFile.getRelativePathFromBase())

        buildKotlinFile << constructBuildScript(
            """
            linksStrategy = LinksStrategy.ERROR
            from("${inputDirectory.name}")
            """
        )

        when:
        def failure = fails(mainTask)

        then:
        failure.assertHasCause("Links strategy is set to ERROR, but a symlink was visited: /${link.name} pointing to ${originalFile.getRelativePathFromBase()}.")
    }

    def "symlinked files should be reported as error if linksStrategy is RELATIVE and points to a path outside the copy root"() {
        given:
        def externalFile = inputDirectory.createFile("original.txt") << "some text"
        def root = inputDirectory.createDir("root")
        def originalFile = root.createFile("original.txt") << "other text"
        def link = root.file("link").createLink("../${externalFile.name}")

        buildKotlinFile << constructBuildScript(
            """
            linksStrategy = LinksStrategy.PRESERVE_RELATIVE
            from("${inputDirectory.name}")
            """
        )

        when:
        def failure = fails(mainTask)

        then:
        failure.assertHasCause("Links strategy is set to PRESERVE_RELATIVE, but a symlink pointing outside was visited: ${root.name}/${link.name} pointing to ../${externalFile.name}.")
    }

    def "symlinked directories should be copied as #hint if linksStrategy=#linksStrategy"() {
        given:
        def original = inputDirectory.createDir("original")
        def originalFile = original.createFile("original.txt") << "some text"
        def link = inputDirectory.file("link").createLink(original.relativePathFromBase)

        buildKotlinFile << constructBuildScript(
            """
            ${configureStrategy(linksStrategy)}
            from("${inputDirectory.name}")
            """
        )

        when:
        succeeds(mainTask)
        def outputDirectory = getResultDir()

        then:
        def originalCopy = outputDirectory.file(original.name)
        isCopy(originalCopy, original)
        def originalFileCopy = originalCopy.file(originalFile.name)
        isCopy(originalFileCopy, originalFile)

        def linkCopy = outputDirectory.file(link.name)
        "$expectedOutcome"(linkCopy, originalCopy)
        def linkCopyFile = linkCopy.file(originalFile.name)
        haveSameContents(linkCopyFile, originalFileCopy)
        isNotASymlink(linkCopyFile)

        where:
        linksStrategy                   | expectedOutcome  | hint
        LinksStrategy.PRESERVE_ALL      | "isValidSymlink" | "valid symlink"
        LinksStrategy.PRESERVE_RELATIVE | "isValidSymlink" | "valid symlink"
        LinksStrategy.FOLLOW            | "isCopy"         | "full copy"
        null                            | "isCopy"         | "full copy"
    }

    def "symlinked files with absolute path should be copied as #hint if linksStrategy=#linksStrategy"() {
        given:
        def originalFile = inputDirectory.createFile("original.txt") << "some text"
        def link = inputDirectory.file("link").createLink(originalFile)

        def originalDir = inputDirectory.createDir("originalDir")
        originalDir.createFile("originalDir.txt") << "some other text"
        def linkDir = inputDirectory.file("linkDir").createLink(originalDir)

        buildKotlinFile << constructBuildScript(
            """
            ${configureStrategy(linksStrategy)}
            from("${inputDirectory.name}")
            """
        )

        when:
        succeeds(mainTask)
        def outputDirectory = getResultDir()

        then:
        def originalCopy = outputDirectory.file(originalFile.name)
        isCopy(originalCopy, originalFile)

        def linkCopy = outputDirectory.file(link.name)
        "$expectedOutcome"(linkCopy, originalFile)

        def linkDirCopy = outputDirectory.file(linkDir.name)
        "$expectedOutcome"(linkDirCopy, originalDir)

        where:
        linksStrategy                   | expectedOutcome  | hint
        LinksStrategy.PRESERVE_ALL      | "isValidSymlink" | "absolute link"
        LinksStrategy.PRESERVE_RELATIVE | "isCopy"         | "full copy"
        LinksStrategy.FOLLOW            | "isCopy"         | "full copy"
        null                            | "isCopy"         | "full copy"
    }

    def "broken links should fail build if linksStrategy=#linksStrategy"() {
        given:
        def link = inputDirectory.file("link").createLink("non-existent-file")

        buildKotlinFile << constructBuildScript(
            """
            ${configureStrategy(linksStrategy)}
            from("${inputDirectory.name}")
            """
        )

        when:
        def failure = fails(mainTask)

        then:
        failure.assertHasDescription("Execution failed for task ':$mainTask'.")
        failure.assertHasCause("Couldn't follow symbolic link '${link}'.")

        where:
        linksStrategy << [
            LinksStrategy.PRESERVE_RELATIVE,
            LinksStrategy.FOLLOW,
            null
        ]
    }


    def "broken links should be preserved as is if linksStrategy=#linksStrategy"() {
        given:
        def originalFile = inputDirectory.createFile("original.txt") << "some text"
        def link = inputDirectory.file("link").createLink("non-existent-file")

        buildKotlinFile << constructBuildScript(
            """
            ${configureStrategy(linksStrategy)}
            from("${inputDirectory.name}")
            """
        )

        when:
        succeeds(mainTask)
        def outputDirectory = getResultDir()

        then:
        def originalCopy = outputDirectory.file(originalFile.name)
        isCopy(originalCopy, originalFile)

        def linkCopy = outputDirectory.file(link.name)
        isBrokenSymlink(linkCopy, originalCopy)

        where:
        linksStrategy << [LinksStrategy.PRESERVE_ALL]
    }

    def "broken links should not cause errors if they are excluded"() {
        given:
        def originalFile = inputDirectory.createFile("original.txt") << "some text"
        def link = inputDirectory.file("link").createLink("non-existent-file")

        buildKotlinFile << constructBuildScript(
            """
            ${configureStrategy(linksStrategy)}
            from("${inputDirectory.name}"){
                exclude("**/${link.name}")
            }
            """
        )

        when:
        succeeds(mainTask)
        def outputDirectory = getResultDir()

        then:
        def originalCopy = outputDirectory.file(originalFile.name)
        isCopy(originalCopy, originalFile)

        def linkCopy = outputDirectory.file(link.name)
        !linkCopy.exists()

        where:
        linksStrategy << [LinksStrategy.FOLLOW, null]
    }

    def "nested spec should be processed properly with parent linksStrategy=#linksStrategyParent and child linksStrategy=#linksStrategyChild"() {
        given:
        def inputWithChildSpec = inputDirectory.createDir("input-child")
        def inputWithParentSpec = inputDirectory.createDir("input-parent")
        for (dir in [inputWithChildSpec, inputWithParentSpec]) {
            def originalFile = dir.createFile("original-${dir.name}.txt") << "some text"
            dir.file("link-${dir.name}").createLink(originalFile.relativePathFromBase)
        }

        buildKotlinFile << constructBuildScript("""
            ${configureStrategy(linksStrategyParent)}
            from("$inputDirectory.name/${inputWithParentSpec.name}")
            from("$inputDirectory.name/${inputWithChildSpec.name}"){
               ${configureStrategy(linksStrategyChild)}
            }
            """)

        when:
        succeeds(mainTask)
        def outputDirectory = getResultDir()

        then:
        def linkChildSpec = outputDirectory.file("link-${inputWithChildSpec.name}")
        "$expectedOutcomeChild"(linkChildSpec, outputDirectory.file("original-${inputWithChildSpec.name}.txt"))

        def linkParentSpec = outputDirectory.file("link-${inputWithParentSpec.name}")
        "$expectedOutcomeParent"(linkParentSpec, outputDirectory.file("original-${inputWithParentSpec.name}.txt"))

        where:
        linksStrategyParent        | linksStrategyChild         | expectedOutcomeParent | expectedOutcomeChild
        LinksStrategy.FOLLOW       | LinksStrategy.FOLLOW       | "isCopy"              | "isCopy"
        LinksStrategy.FOLLOW       | LinksStrategy.PRESERVE_ALL | "isCopy"              | "isValidSymlink"
        LinksStrategy.FOLLOW       | null                       | "isCopy"              | "isCopy"
        LinksStrategy.PRESERVE_ALL | LinksStrategy.FOLLOW       | "isValidSymlink"      | "isCopy"
        LinksStrategy.PRESERVE_ALL | LinksStrategy.PRESERVE_ALL | "isValidSymlink"      | "isValidSymlink"
        LinksStrategy.PRESERVE_ALL | null                       | "isValidSymlink"      | "isValidSymlink"
        null                       | LinksStrategy.FOLLOW       | "isCopy"              | "isCopy"
        null                       | LinksStrategy.PRESERVE_ALL | "isCopy"              | "isValidSymlink"
        null                       | null                       | "isCopy"              | "isCopy"
    }

    def "symlinks are not preserved with filter"() {
        given:
        def originalFile = inputDirectory.createFile("original.txt") << "some text"
        def link = inputDirectory.file("link").createLink(originalFile.getRelativePathFromBase())

        buildKotlinFile << constructBuildScript("""
            linksStrategy = LinksStrategy.PRESERVE_ALL
            from("${inputDirectory.name}"){
                filter { line: String ->
                    if (line.startsWith('#')) "" else line
                }
            }
            """)

        when:
        succeeds(mainTask)
        def outputDirectory = getResultDir()

        then:
        def originalCopy = outputDirectory.file(originalFile.name)
        isCopy(originalCopy, originalFile)

        def linkCopy = outputDirectory.file(link.name)
        isCopy(linkCopy, originalCopy)
    }

    //TODO: decide on actual behavior
    def "symlinks are not preserved with expand"() {
        given:
        def originalFile = inputDirectory.createFile("original.txt") << "some text"
        def link = inputDirectory.file("link").createLink(originalFile.getRelativePathFromBase())

        buildKotlinFile << constructBuildScript(
            """
            linksStrategy = LinksStrategy.PRESERVE_ALL
            from("${inputDirectory.name}"){
                expand(mapOf("key" to "value"))
            }
            """
        )

        when:
        succeeds(mainTask)
        def outputDirectory = getResultDir()

        then:
        def originalCopy = outputDirectory.file(originalFile.name)
        isCopy(originalCopy, originalFile)

        def linkCopy = outputDirectory.file(link.name)
        isCopy(linkCopy, originalCopy)
    }

    //TODO: test a case when root is a symlink

    def "symlinks should respect inclusions and similar transformations for #linksStrategy"() {
        given:
        def originalDir = inputDirectory.createDir("original")
        def originalFile = originalDir.createFile("original.txt") << "some text"
        def link = originalDir.file("link").createLink(originalFile.getRelativePathFromBase())
        def linkTxt = originalDir.file("link.txt").createLink(originalFile.getRelativePathFromBase())
        def linkDir = inputDirectory.file("linkDir").createLink(originalDir.getRelativePathFromBase())

        buildKotlinFile << constructBuildScript("""
            ${configureStrategy(linksStrategy)}
            from("${inputDirectory.name}"){
                include("**/*.txt")
            }
        """)

        when:
        succeeds(mainTask)
        def outputDirectory = getResultDir()

        then:
        def linkCopy = outputDirectory.file(originalDir.name, link.name)
        !linkCopy.exists()

        def linkTxtCopy = outputDirectory.file(originalDir.name, linkTxt.name)
        linkTxtCopy.exists()

        def originalFileLinkDirCopy = outputDirectory.file(linkDir.name, originalFile.name)
        originalFileLinkDirCopy.exists() == copied

        where:
        linksStrategy              | copied
        LinksStrategy.PRESERVE_ALL | false
        LinksStrategy.FOLLOW       | true
        null                       | true
    }

    //TODO: add permissions checks

    @SuppressWarnings('unused')
    protected boolean isValidSymlink(File link, File target) {
        link.exists() &&
            Files.isSymbolicLink(link.toPath()) &&
            link.canonicalPath == target.canonicalPath &&
            (target.isDirectory() || haveSameContents(link, target))
    }

    @SuppressWarnings('unused')
    protected boolean isBrokenSymlink(File link, File target) {
        Files.exists(link.toPath(), NOFOLLOW_LINKS) &&
            Files.isSymbolicLink(link.toPath()) &&
            !Files.readSymbolicLink(link.toPath()).toFile().exists()
    }

    protected boolean isNotASymlink(File link) {
        link.exists() &&
            !Files.isSymbolicLink(link.toPath())
    }

    protected boolean isCopy(File copy, File original) {
        copy.exists() &&
            !Files.isSymbolicLink(copy.toPath()) &&
            copy.canonicalPath != original.canonicalPath &&
            (copy.isDirectory() || haveSameContents(copy, original))
    }

    protected def haveSameContents(File one, File other) {
        one.text == other.text
    }

    protected String configureStrategy(LinksStrategy linksStrategy) {
        if (linksStrategy == null) {
            return ""
        }
        return "linksStrategy = LinksStrategy.$linksStrategy"
    }
}
