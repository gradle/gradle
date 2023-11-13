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
import org.gradle.internal.nativeintegration.filesystem.jdk7.PosixFilePermissionConverter
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import static java.nio.file.LinkOption.NOFOLLOW_LINKS
import static org.hamcrest.CoreMatchers.anyOf
import static org.hamcrest.CoreMatchers.startsWith

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
        failure.assertHasCause("Links strategy is set to ERROR, but a symlink was visited: ${link.name} pointing to ${originalFile.getRelativePathFromBase()}.")
    }

    def "symlinked files should be reported as error if linksStrategy is RELATIVE and points to a path outside the copy root"() {
        given:
        def externalFile = inputDirectory.createFile("external.txt") << "external text"
        def rootDir = inputDirectory.createDir("root")
        def rootFile = rootDir.createFile("root.txt") << "root text"
        def subRootDir = rootDir.createDir("subroot")
        def originalFile = subRootDir.createFile("original.txt") << "other text"
        def link = subRootDir.file("link").createLink(symlinkTarget)
        def subDir = subRootDir.createDir("sub")
        def subFile = subDir.createFile("sub.txt") << "subfile"

        when:
        buildKotlinFile << constructBuildScript(
            """
            linksStrategy = LinksStrategy.${LinksStrategy.PRESERVE_RELATIVE}
            from("${inputDirectory.name}/$root")
            """
        )

        then:
        if (error) {
            def relativePathToLink = inputDirectory.toPath().resolve(root).relativize(link.toPath())
            fails(mainTask).assertHasCause("Links strategy is set to PRESERVE_RELATIVE, but a symlink pointing outside was visited: $relativePathToLink pointing to $symlinkTarget.")
        } else {
            succeeds(mainTask)
        }

        where:
        root           | symlinkTarget                       | error
        "root"         | "original.txt"                      | false
        "root"         | "../root.txt"                       | false
        "root"         | "../subroot/original.txt"           | false

        "root"         | "../../external.txt"                | true
        "root/subroot" | "original.txt"                      | false
        "root/subroot" | "../root.txt"                       | true
        "root/subroot" | "../../external.txt"                | true
        "root/subroot" | "../subroot/original.txt"           | true // any traversal out of the root should be an error

        "root/subroot" | "sub/sub.txt"                       | false
        "root/subroot" | "../subroot/sub/sub.txt"            | true
        "root/subroot" | "sub/../../subroot/sub/sub.txt"     | true
        "root/subroot" | "sub/./../../subroot/sub/sub.txt"   | true
        "root/subroot" | "./sub/.././../subroot/sub/sub.txt" | true
        "root"         | "sub/sub.txt"                       | false
        "root"         | "../subroot/sub/sub.txt"            | false
        "root"         | "sub/../../subroot/sub/sub.txt"     | false
        "root"         | "./sub/.././../subroot/sub/sub.txt" | false
    }

    def "symlink relativeness checks forbid another symlinks in path"() {
        given:
        def externalFile = inputDirectory.createFile("external.txt") << "external text"
        def rootDir = inputDirectory.createDir("root")
        def rootFile = rootDir.createFile("root.txt") << "root text"
        def subRootDir = rootDir.createDir("subroot")
        def originalFile = subRootDir.createFile("original.txt") << "other text"
        def link = subRootDir.file("link").createLink(symlinkTarget)
        def trickyLink = subRootDir.file("trickyLink").createLink(trickyLinkTarget)
        def subDir = subRootDir.createDir("sub")
        def subFile = subDir.createFile("sub.txt") << "subfile"

        when:
        buildKotlinFile << constructBuildScript(
            """
            linksStrategy = LinksStrategy.${LinksStrategy.PRESERVE_RELATIVE}
            from("${inputDirectory.name}/$root")
            """
        )

        then:
        if (error) {
            def relativePathToLink = inputDirectory.toPath().resolve(root).relativize(link.toPath())
            def relativePathToTrickyLink = inputDirectory.toPath().resolve(root).relativize(trickyLink.toPath())
            fails(mainTask).assertThatCause(
                anyOf(
                    startsWith("Links strategy is set to PRESERVE_RELATIVE, but a symlink pointing outside was visited: $relativePathToLink pointing to $symlinkTarget."),
                    startsWith("Links strategy is set to PRESERVE_RELATIVE, but a symlink pointing outside was visited: $relativePathToTrickyLink pointing to $trickyLinkTarget.")
                )
            )
        } else {
            succeeds(mainTask)
        }

        where:
        root   | symlinkTarget                       | trickyLinkTarget         | error
        "root" | "trickyLink"                        | "original.txt"           | true
        "root" | "trickyLink/../original.txt"        | "sub"                    | true
        "root" | "trickyLink/../external.txt"        | ".."                     | true
        "root" | "trickyLink/../root.txt"            | "../../root/subroot"     | true
        "root" | "../subroot/trickyLink/../root.txt" | "../../root/subroot"     | true
        "root" | "trickyLink/../../root.txt"         | "sub"                    | true
        "root" | "trickyLink/../root.txt"            | "../../root/nonexisting" | true  // nonexistent file can be replaced by symlink
        "root" | "nonExistent/original.txt"          | "stub"                   | true
    }

    def "symlinked files with nested spec should be reported as error if linksStrategy is RELATIVE and points to a path outside the copy root"() {
        given:
        def externalFile = inputDirectory.createFile("external.txt") << "external text"
        def rootDir = inputDirectory.createDir("root")
        def rootFile = rootDir.createFile("root.txt") << "root text"
        def subRootDir = rootDir.createDir("subroot")
        def originalFile = subRootDir.createFile("original.txt") << "other text"
        def link = subRootDir.file("link").createLink(symlinkTarget)

        when:
        buildKotlinFile << constructBuildScript("""
            ${configureStrategy(linksStrategyParent)}
            from("$inputDirectory.name/$root"){
               ${configureStrategy(linksStrategyChild)}
            }
            """)

        then:
        if (error) {
            def relativePathToLink = inputDirectory.toPath().resolve(root).relativize(link.toPath())
            fails(mainTask).assertHasCause("Links strategy is set to PRESERVE_RELATIVE, but a symlink pointing outside was visited: $relativePathToLink pointing to $symlinkTarget.")
        } else {
            succeeds(mainTask)
        }
        where:
        root           | symlinkTarget        | linksStrategyParent             | linksStrategyChild              | error
        "root"         | "original.txt"       | LinksStrategy.FOLLOW            | LinksStrategy.PRESERVE_RELATIVE | false
        "root"         | "../../external.txt" | LinksStrategy.FOLLOW            | LinksStrategy.PRESERVE_RELATIVE | true
        "root"         | "../root.txt"        | LinksStrategy.FOLLOW            | LinksStrategy.PRESERVE_RELATIVE | false
        "root/subroot" | "../root.txt"        | LinksStrategy.FOLLOW            | LinksStrategy.PRESERVE_RELATIVE | true

        "root"         | "original.txt"       | LinksStrategy.PRESERVE_RELATIVE | LinksStrategy.FOLLOW            | false
        "root"         | "../../external.txt" | LinksStrategy.PRESERVE_RELATIVE | LinksStrategy.FOLLOW            | false
        "root"         | "../root.txt"        | LinksStrategy.PRESERVE_RELATIVE | LinksStrategy.FOLLOW            | false
        "root/subroot" | "../root.txt"        | LinksStrategy.PRESERVE_RELATIVE | LinksStrategy.FOLLOW            | false

        "root"         | "original.txt"       | LinksStrategy.PRESERVE_RELATIVE | LinksStrategy.PRESERVE_RELATIVE | false
        "root/subroot" | "../root.txt"        | LinksStrategy.PRESERVE_RELATIVE | LinksStrategy.PRESERVE_RELATIVE | true
    }

    def "PRESERVE_ALL preserves any link as is"() {
        given:
        def externalFile = inputDirectory.createFile("external.txt") << "external text"
        def rootDir = inputDirectory.createDir("root")
        def rootFile = rootDir.createFile("root.txt") << "root text"
        def subRootDir = rootDir.createDir("subroot")
        def originalFile = subRootDir.createFile("original.txt") << "other text"
        def link = subRootDir.file("link").createLink(symlinkTarget)
        def trickyLink = subRootDir.file("trickyLink").createLink(trickyLinkTarget)
        def subDir = subRootDir.createDir("sub")
        def subFile = subDir.createFile("sub.txt") << "subfile"

        when:
        buildKotlinFile << constructBuildScript(
            """
            linksStrategy = LinksStrategy.${LinksStrategy.PRESERVE_ALL}
            from("${inputDirectory.name}/${rootDir.name}")
            """
        )

        then:
        succeeds(mainTask)
        def outputDirectory = getResultDir()
        def linkCopy = outputDirectory.file("${subRootDir.name}/${link.name}")

        "$expectedOutcome"(linkCopy, outputDirectory.file(expectedTarget))
        Files.readSymbolicLink(linkCopy.toPath()) == Paths.get(symlinkTarget)

        where:
        symlinkTarget                       | trickyLinkTarget         | expectedOutcome   | expectedTarget
        "trickyLink"                        | "original.txt"           | "isValidSymlink"  | "subroot/original.txt"
        "trickyLink/../original.txt"        | "sub"                    | "isValidSymlink"  | "subroot/original.txt"
        "trickyLink/../external.txt"        | ".."                     | "isBrokenSymlink" | "../external.txt"
        "trickyLink/../root.txt"            | "../../root/subroot"     | "isBrokenSymlink" | "root.txt" //no "root" part in path
        "../subroot/trickyLink/../root.txt" | "../../root/subroot"     | "isBrokenSymlink" | "root.txt"
        "trickyLink/../../root.txt"         | "sub"                    | "isValidSymlink"  | "root.txt"
        "trickyLink/../root.txt"            | "../../root/nonexisting" | "isBrokenSymlink" | "nonexisting"
        "nonExistent/original.txt"          | "stub"                   | "isBrokenSymlink" | "nonExistent/original.txt"
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
        linksStrategy              | expectedOutcome  | hint
        LinksStrategy.PRESERVE_ALL | "isValidSymlink" | "absolute link"
        LinksStrategy.FOLLOW       | "isCopy"         | "full copy"
        null                       | "isCopy"         | "full copy"
    }

    def "symlinked files with absolute path should be treated as error if linksStrategy=PRESERVE_RELATIVE"() {
        given:
        def linksStrategy = LinksStrategy.PRESERVE_RELATIVE
        def originalFile = inputDirectory.createFile("original.txt") << "some text"

        def originalDir = inputDirectory.createDir("originalDir")
        originalDir.createFile("originalDir.txt") << "some other text"
        def linkDir = inputDirectory.file("linkDir").createLink(originalDir)

        when:
        buildKotlinFile << constructBuildScript(
            """
            ${configureStrategy(linksStrategy)}
            from("${inputDirectory.name}")
            """
        )

        then:
        fails(mainTask).assertHasCause("Links strategy is set to PRESERVE_RELATIVE, but a symlink pointing outside was visited: ${linkDir.name} pointing to $originalDir.")
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

    def "when root is a link to #rootLinkTarget, it should be #expectedOutcome if linksStrategy=#linksStrategy"() {
        given:
        def externalFile = inputDirectory.createFile("file") << "external text"
        def rootDir = inputDirectory.createDir("dir")
        def rootFile = rootDir.createFile("root.txt") << "root text"
        def link = inputDirectory.file("link").createLink(rootLinkTarget)

        when:
        buildKotlinFile << constructBuildScript(
            """
            ${configureStrategy(linksStrategy)}
            from("${inputDirectory.name}/${link.name}")
            """
        )

        then:
        if (expectedOutcome.contains("error")) {
            fails(mainTask)
        } else {
            succeeds(mainTask)
        }
        def output = getResultDir()
        switch (expectedOutcome) {
            case "copied as link":
                assert isBrokenSymlink(output.file(link.name), output.file(rootLinkTarget))
                break
            case "copied as file":
                def outputFile = output.file(link.name)
                assert Files.isRegularFile(outputFile.toPath(), NOFOLLOW_LINKS)
                assert isCopy(outputFile, externalFile)
                break
            case "copied as dir":
                assert Files.isDirectory(output.toPath(), NOFOLLOW_LINKS)
                assert isCopy(output.file("root.txt"), rootFile)
                break
            case "treated as error":
                assert failure.assertHasCause("Links strategy is set to ${linksStrategy}, but a symlink was visited: . pointing to $rootLinkTarget")
                break
            case "treated as broken link error":
                assert failure.assertHasCause("Couldn't follow symbolic link '${link}'")
                break
            case "treated as relativeness error":
                assert failure.assertHasCause("Links strategy is set to ${linksStrategy}, but a symlink pointing outside was visited: . pointing to $rootLinkTarget")
                break
            case "ignored":
                assert !output.exists()
                break
        }

        where:
        rootLinkTarget      | linksStrategy                   | expectedOutcome
        "dir"               | null                            | "copied as dir"
        "dir"               | LinksStrategy.FOLLOW            | "copied as dir"
        "dir"               | LinksStrategy.PRESERVE_ALL      | "copied as link" // it would be inside output dir, but that's consistent with file behavior
        "dir"               | LinksStrategy.PRESERVE_RELATIVE | "treated as relativeness error"
        "dir"               | LinksStrategy.ERROR             | "treated as error"

        "file"              | null                            | "copied as file"
        "file"              | LinksStrategy.FOLLOW            | "copied as file"
        "file"              | LinksStrategy.PRESERVE_ALL      | "copied as link"
        "file"              | LinksStrategy.PRESERVE_RELATIVE | "treated as relativeness error"
        "file"              | LinksStrategy.ERROR             | "treated as error"

        "non-existent-file" | null                            | "treated as broken link error"
        "non-existent-file" | LinksStrategy.FOLLOW            | "treated as broken link error"
        "non-existent-file" | LinksStrategy.PRESERVE_ALL      | "copied as link"
        "non-existent-file" | LinksStrategy.PRESERVE_RELATIVE | "treated as relativeness error"
        "non-existent-file" | LinksStrategy.ERROR             | "treated as error"
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

    def "symlinks are not processed by filter with #linksStrategy"() {
        given:
        def originalFile = inputDirectory.createFile("original.txt") << "some text"
        def link = inputDirectory.file("link").createLink(originalFile.getRelativePathFromBase())

        buildKotlinFile << constructBuildScript("""
            ${configureStrategy(linksStrategy)}
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
        "$expectedOutcome"(linkCopy, originalCopy)

        where:
        linksStrategy                   | expectedOutcome
        LinksStrategy.PRESERVE_ALL      | "isValidSymlink"
        LinksStrategy.PRESERVE_RELATIVE | "isValidSymlink"
        LinksStrategy.FOLLOW            | "isCopy"
        null                            | "isCopy"
    }

    def "symlinks are not processed by expand with #linksStrategy"() {
        given:
        def originalFile = inputDirectory.createFile("original.txt") << "some text"
        def link = inputDirectory.file("link").createLink(originalFile.getRelativePathFromBase())

        buildKotlinFile << constructBuildScript(
            """
            ${configureStrategy(linksStrategy)}
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
        "$expectedOutcome"(linkCopy, originalCopy)

        where:
        linksStrategy                   | expectedOutcome
        LinksStrategy.PRESERVE_ALL      | "isValidSymlink"
        LinksStrategy.PRESERVE_RELATIVE | "isValidSymlink"
        LinksStrategy.FOLLOW            | "isCopy"
        null                            | "isCopy"
    }

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

    def "links permissions are preserved"() {
        given:
        def originalFile = inputDirectory.createFile("original.txt") << "some text"
        originalFile.mode = fileMode
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
        def linkCopy = outputDirectory.file(link.name)
        def fileCopy = outputDirectory.file(originalFile.name)
        isValidSymlink(linkCopy, fileCopy)

        permissions(linkCopy) == permissions(link)
        permissions(originalFile) == permissions(fileCopy)

        where:
        fileMode | linksStrategy
        0746     | LinksStrategy.PRESERVE_ALL
        0746     | LinksStrategy.PRESERVE_ALL
        0746     | LinksStrategy.PRESERVE_ALL
        0400     | LinksStrategy.PRESERVE_ALL
        0777     | LinksStrategy.PRESERVE_RELATIVE
    }

    def "links permissions are not affected by filePermissions"() {
        given:
        def originalFile = inputDirectory.createFile("original.txt") << "some text"
        originalFile.mode = fileMode
        def link = inputDirectory.file("link").createLink(originalFile.getRelativePathFromBase())

        buildKotlinFile << constructBuildScript(
            """
            ${configureStrategy(linksStrategy)}
            from("${inputDirectory.name}")
            filePermissions {
                unix("$specMode")
            }
            """
        )

        when:
        succeeds(mainTask)
        def outputDirectory = getResultDir()

        then:
        def linkCopy = outputDirectory.file(link.name)
        def fileCopy = outputDirectory.file(originalFile.name)
        isValidSymlink(linkCopy, fileCopy)

        permissions(linkCopy) == permissions(link)
        permissions(fileCopy) == Integer.parseInt(specMode, 8)

        where:
        fileMode | specMode | linksStrategy
        0746     | "0777"   | LinksStrategy.PRESERVE_ALL
        0746     | "0746"   | LinksStrategy.PRESERVE_ALL
        0777     | "0700"   | LinksStrategy.PRESERVE_RELATIVE
    }

    @SuppressWarnings('unused')
    protected boolean isValidSymlink(File link, File target) {
        link.exists() &&
            Files.isSymbolicLink(link.toPath()) &&
            link.canonicalPath == target.canonicalPath &&
            (target.isDirectory() || haveSameContents(link, target))
    }

    @SuppressWarnings('unused')
    protected boolean isBrokenSymlink(File link, File target) {
        Path linkPath = link.toPath()
        Files.exists(linkPath, NOFOLLOW_LINKS) &&
            Files.isSymbolicLink(linkPath) &&
            !Files.exists(Files.readSymbolicLink(linkPath))
    }

    protected boolean isNotASymlink(File link) {
        link.exists() &&
            !Files.isSymbolicLink(link.toPath())
    }

    protected boolean isCopy(File copy, File original) {
        copy.exists() &&
            !Files.isSymbolicLink(copy.toPath()) &&
            copy.canonicalPath != original.canonicalPath &&
            (copy.isDirectory() || haveSameContents(copy, original)) &&
            permissions(copy) == permissions(original)
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

    protected def permissions(File file) {
        return PosixFilePermissionConverter.convertToInt(Files.getPosixFilePermissions(file.toPath(), NOFOLLOW_LINKS));
    }
}
