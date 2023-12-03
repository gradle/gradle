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
import java.nio.file.Paths

import static java.nio.file.LinkOption.NOFOLLOW_LINKS
import static org.hamcrest.CoreMatchers.anyOf
import static org.hamcrest.CoreMatchers.startsWith

@Requires(UnitTestPreconditions.Symlinks)
abstract class AbstractCopySymlinksIntegrationSpec extends AbstractIntegrationSpec implements SymlinksFixture {

    final String mainTask = "doWork"

    abstract TestFile getResultDir()

    abstract String constructBuildScript(String inputConfig, String mainPath = "")

    LinksStrategy getDefaultLinksStrategy() {
        LinksStrategy.FOLLOW
    };

    TestFile inputDirectory

    def setup() {
        inputDirectory = createDir("input")
        executer.withStacktraceEnabled()
    }

    def "proper default strategy should be used"() {
        given:
        def originalFile = inputDirectory.createFile("original.txt") << "some text"
        def link = inputDirectory.file("link").createLink(originalFile)

        when:
        buildKotlinFile << constructBuildScript("")

        then:
        if (defaultLinksStrategy == LinksStrategy.FOLLOW) {
            succeeds(mainTask)
            def outputDirectory = getResultDir()

            def originalCopy = outputDirectory.file(originalFile.name)
            isCopy(originalCopy, originalFile)

            def linkCopy = outputDirectory.file(link.name)
            isCopy(linkCopy, originalCopy)
        } else {
            fails(mainTask).assertHasCause("Links strategy is set to PRESERVE_RELATIVE, but a symlink pointing outside was visited")
        }
    }

    def "symlinked files should be copied as #hint if linksStrategy=#linksStrategy"() {
        given:
        def originalFile = inputDirectory.createFile("original.txt") << "some text"
        def link = inputDirectory.file("link").createLink(originalFile.name)

        buildKotlinFile << constructBuildScript(
            configureStrategy(linksStrategy)
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
    }

    def "symlinked files should be reported as error if linksStrategy is ERROR"() {
        given:
        def originalFile = inputDirectory.createFile("original.txt") << "some text"
        def link = inputDirectory.file("link").createLink(originalFile.name)

        buildKotlinFile << constructBuildScript(
            configureStrategy(LinksStrategy.ERROR)
        )

        when:
        def failure = fails(mainTask)

        then:
        failure.assertHasCause("Links strategy is set to ERROR, but a symlink was visited: '${link.name}' pointing to '${originalFile.name}'.")
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
            configureStrategy(LinksStrategy.PRESERVE_RELATIVE),
            root
        )

        then:
        if (error) {
            def relativePathToLink = relativePath(inputDirectory.file(root), link)
            fails(mainTask).assertHasCause("Links strategy is set to PRESERVE_RELATIVE, but a symlink pointing outside was visited: '$relativePathToLink' pointing to '$symlinkTarget'.")
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
            configureStrategy(LinksStrategy.PRESERVE_ALL),
            rootDir.name
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

    def "link cycles should be #expectedOutcome with linksStrategy=#linksStrategy"() {
        given:
        def originalFile = inputDirectory.createFile("original.txt") << "some text"
        def directCycle1 = inputDirectory.file("directCycle1").createLink("directCycle2")
        def directCycle2 = inputDirectory.file("directCycle2").createLink("directCycle1")
        def selfLink = inputDirectory.file("selfLink").createLink("selfLink")
        def indirectCycle1 = inputDirectory.file("indirectCycle1").createLink("indirectCycle2")
        def indirectCycle2 = inputDirectory.file("indirectCycle2").createLink("indirectCycle3")
        def indirectCycle3 = inputDirectory.file("indirectCycle3").createLink("indirectCycle1")

        when:
        buildKotlinFile << constructBuildScript(
            configureStrategy(linksStrategy)
        )

        then:
        if (expectedOutcome.contains("error")) {
            fails(mainTask)
        } else {
            succeeds(mainTask)
        }
        switch (expectedOutcome) {
            case "copied as is":
                def outputDirectory = getResultDir()
                for (link in [directCycle1, directCycle2, selfLink, indirectCycle1, indirectCycle2, indirectCycle3]) {
                    def linkCopy = outputDirectory.file(link.name)
                    haveSameTarget(linkCopy, link)
                }
                break
            case "treated as error":
                assert failure.assertHasCause("Links strategy is set to ${linksStrategy}, but a symlink was visited")
                break
            case "treated as broken link error":
                assert failure.assertHasCause("Couldn't follow symbolic link")
                break
            case "treated as relativeness error":
                assert failure.assertHasCause("Links strategy is set to ${linksStrategy}, but a symlink pointing outside was visited")
                break
        }

        where:
        linksStrategy                   | expectedOutcome
        LinksStrategy.PRESERVE_ALL      | "copied as is"
        LinksStrategy.PRESERVE_RELATIVE | "treated as relativeness error"
        LinksStrategy.FOLLOW            | "treated as broken link error"
        LinksStrategy.ERROR             | "treated as error"
    }

    def "symlinked directories should be copied as #hint if linksStrategy=#linksStrategy"() {
        given:
        def original = inputDirectory.createDir("original")

        def originalFile = original.createFile("original.txt") << "some text"
        def randomFile = original.createFile("random.txt") << "some random text"
        def originalLink = original.file("link").createLink(originalFile.name)
        def subDir = original.createDir("sub")
        def subDirLink = original.file("subdir-link").createLink(subDir.name)
        def subFile = subDir.createFile("original-sub.txt") << "some sub text"

        def dirLink = inputDirectory.file("dir-link").createLink(original.name)
        def subDirLinkFromRoot = inputDirectory.file("subdir-link").createLink("${original.name}/${subDir.name}")

        buildKotlinFile << constructBuildScript(
            configureStrategy(linksStrategy)
        )

        when:
        succeeds(mainTask)
        def outputDirectory = getResultDir()

        then:
        def originalCopy = outputDirectory.file(original.name)
        isCopy(originalCopy, original)
        def originalFileCopy = originalCopy.file(originalFile.name)
        def randomFileCopy = originalCopy.file(randomFile.name)
        isCopy(originalFileCopy, originalFile)
        def subDirCopy = originalCopy.file(subDir.name)
        def subDirFile = subDirCopy.file(subFile.name)

        def dirLinkCopy = outputDirectory.file(dirLink.name)
        "$expectedOutcome"(dirLinkCopy, originalCopy)
        if (expectedOutcome == "isCopy") {
            isCopy(dirLinkCopy.file(originalFile.name), originalFileCopy)
            isCopy(dirLinkCopy.file(randomFile.name), randomFileCopy)
            isCopy(dirLinkCopy.file(originalLink.name), originalFileCopy)
            isCopy(dirLinkCopy.file(subDir.name), subDirCopy)
            isCopy(dirLinkCopy.file(subDir.name, subFile.name), subDirFile)
            isCopy(dirLinkCopy.file(subDirLink.name), subDirCopy)
            isCopy(dirLinkCopy.file(subDirLink.name, subFile.name), subDirFile)
        }

        for (subDirLinkCopyPath in [subDirLinkFromRoot.name, "${original.name}/${subDirLink.name}"]) {
            def subDirLinkCopy = outputDirectory.file(subDirLinkCopyPath)
            "$expectedOutcome"(subDirLinkCopy, subDirCopy)
        }

        where:
        linksStrategy                   | expectedOutcome  | hint
        LinksStrategy.PRESERVE_ALL      | "isValidSymlink" | "valid symlink"
        LinksStrategy.PRESERVE_RELATIVE | "isValidSymlink" | "valid symlink"
        LinksStrategy.FOLLOW            | "isCopy"         | "full copy"
    }

    def "symlinked files with absolute path should be copied as #hint if linksStrategy=#linksStrategy"() {
        given:
        def originalFile = inputDirectory.createFile("original.txt") << "some text"
        def link = inputDirectory.file("link").createLink(originalFile)

        def originalDir = inputDirectory.createDir("originalDir")
        originalDir.createFile("originalDir.txt") << "some other text"
        def linkDir = inputDirectory.file("linkDir").createLink(originalDir)

        buildKotlinFile << constructBuildScript(
            configureStrategy(linksStrategy)
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
    }

    def "symlinked files with absolute path should be treated as error if linksStrategy=PRESERVE_RELATIVE"() {
        given:
        def linksStrategy = LinksStrategy.PRESERVE_RELATIVE
        def originalFile = inputDirectory.createFile("original.txt") << "some text"

        def originalDir = inputDirectory.createDir("originalDir")
        originalDir.createFile("originalDir.txt") << "some other text"
        def linkTargetFile = linkTarget == "file" ? originalFile : originalDir
        def link = inputDirectory.file("linkDir").createLink(linkTargetFile)

        when:
        buildKotlinFile << constructBuildScript(
            configureStrategy(linksStrategy)
        )

        then:
        fails(mainTask)
        assertHasCauseIgnoringUnicodeNormaization("Links strategy is set to PRESERVE_RELATIVE, but a symlink pointing outside was visited: '${link.name}' pointing to '${linkTargetFile}'.")

        where:
        linkTarget << ["file", "dir"]
    }

    def "broken links should fail build if linksStrategy=#linksStrategy"() {
        given:
        def link = inputDirectory.file("link").createLink("non-existent-file")

        buildKotlinFile << constructBuildScript(
            configureStrategy(linksStrategy)
        )

        when:
        def failure = fails(mainTask)

        then:
        failure.assertHasDescription("Execution failed for task ':$mainTask'.")
        failure.assertHasCause("Couldn't follow symbolic link '${link.name}' pointing to 'non-existent-file'.")

        where:
        linksStrategy << [LinksStrategy.FOLLOW]
    }

    def "broken links should be preserved as is if linksStrategy=LinksStrategy.PRESERVE_ALL"() {
        given:
        def originalFile = inputDirectory.createFile("original.txt") << "some text"
        def link = inputDirectory.file("link").createLink("non-existent-file")

        buildKotlinFile << constructBuildScript(
            configureStrategy(LinksStrategy.PRESERVE_ALL)
        )

        when:
        succeeds(mainTask)
        def outputDirectory = getResultDir()

        then:
        def originalCopy = outputDirectory.file(originalFile.name)
        isCopy(originalCopy, originalFile)

        def linkCopy = outputDirectory.file(link.name)
        isBrokenSymlink(linkCopy, originalCopy)
    }

    def "broken links should not cause errors if they are excluded"() {
        given:
        def originalFile = inputDirectory.createFile("original.txt") << "some text"
        def link = inputDirectory.file("link").createLink("non-existent-file")

        buildKotlinFile << constructBuildScript(
            """
            ${configureStrategy(linksStrategy)}
            exclude("**/${link.name}")
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
        linksStrategy << [
            LinksStrategy.FOLLOW,
            LinksStrategy.ERROR,
            LinksStrategy.PRESERVE_RELATIVE
        ]
    }

    def "symlinks are not processed by filter with #linksStrategy"() {
        given:
        def originalFile = inputDirectory.createFile("original.txt") << "some text"
        def link = inputDirectory.file("link").createLink(originalFile.name)

        buildKotlinFile << constructBuildScript("""
            ${configureStrategy(linksStrategy)}
            filter { line: String ->
                if (line.startsWith('#')) "" else line
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
    }

    def "symlinks are not processed by expand with #linksStrategy"() {
        given:
        def originalFile = inputDirectory.createFile("original.txt") << "some text"
        def link = inputDirectory.file("link").createLink(originalFile.name)

        buildKotlinFile << constructBuildScript(
            """
            ${configureStrategy(linksStrategy)}
            expand(mapOf("key" to "value"))
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
    }

    def "symlinks should respect inclusions and similar transformations for #linksStrategy"() {
        given:
        def originalDir = inputDirectory.createDir("original")
        def originalFile = originalDir.createFile("original.txt") << "some text"
        def link = originalDir.file("link").createLink(originalFile.name)
        def linkTxt = originalDir.file("link.txt").createLink(originalFile.name)
        def linkDir = inputDirectory.file("linkDir").createLink(originalDir.name)

        buildKotlinFile << constructBuildScript("""
            ${configureStrategy(linksStrategy)}
            include("**/*.txt")
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
    }

    def "links permissions are preserved"() {
        given:
        def originalFile = inputDirectory.createFile("original.txt") << "some text"
        originalFile.mode = fileMode
        def link = inputDirectory.file("link").createLink(originalFile.name)

        buildKotlinFile << constructBuildScript(
            configureStrategy(linksStrategy)
        )

        when:
        succeeds(mainTask)
        def outputDirectory = getResultDir()

        then:
        def linkCopy = outputDirectory.file(link.name)
        def fileCopy = outputDirectory.file(originalFile.name)
        isValidSymlink(linkCopy, fileCopy)

        linkCopy.permissions == link.permissions
        originalFile.permissions == fileCopy.permissions

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
        def link = inputDirectory.file("link").createLink(originalFile.name)

        buildKotlinFile << constructBuildScript(
            """
            ${configureStrategy(linksStrategy)}
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

        linkCopy.permissions == link.permissions
        fileCopy.permissions == specMode

        where:
        fileMode | specMode    | linksStrategy
        0746     | "rwxrwxrwx" | LinksStrategy.PRESERVE_ALL
        0746     | "rwxr--rw-" | LinksStrategy.PRESERVE_ALL
        0777     | "rwx------" | LinksStrategy.PRESERVE_RELATIVE
    }
}

abstract class AbstractFileSystemCopySymlinksIntegrationSpec extends AbstractCopySymlinksIntegrationSpec {
    def "symlinked files with absolute path should be copied as #hint if linksStrategy=#linksStrategy"() {
        given:
        def originalFile = inputDirectory.createFile("original.txt") << "some text"
        def link = inputDirectory.file("link").createLink(originalFile)

        def originalDir = inputDirectory.createDir("originalDir")
        originalDir.createFile("originalDir.txt") << "some other text"
        def linkDir = inputDirectory.file("linkDir").createLink(originalDir)

        buildKotlinFile << constructBuildScript(
            configureStrategy(linksStrategy)
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
    }

    def "intermediate symlinks are followed properly"() {
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

        def inputRoot = inputDirectory.file(root)

        when:
        buildKotlinFile << constructBuildScript(
            configureStrategy(LinksStrategy.FOLLOW),
            root
        )

        then:
        succeeds(mainTask)
        def outputDirectory = getResultDir()

        def linkCopy = outputDirectory.file(relativePath(inputRoot, link))
        def linkTargetCopy = outputDirectory.file(relativePathToTarget(inputRoot, link))
        isCopy(linkCopy, linkTargetCopy)

        def trickyLinkCopy = outputDirectory.file(relativePath(inputRoot, trickyLink))
        def trickyLinkTargetCopy = outputDirectory.file(relativePathToTarget(inputRoot, trickyLink))
        isCopy(trickyLinkCopy, trickyLinkTargetCopy)

        where:
        root   | symlinkTarget                                            | trickyLinkTarget
        "root" | "trickyLink"                                             | "original.txt"
        "root" | "../subroot/trickyLink"                                  | "../subroot/original.txt"
        "root" | "../subroot/trickyLink/../../root.txt"                   | "sub"
        "root" | "../subroot/trickyLink/../../subroot/trickyLink/sub.txt" | "sub"
        "root" | "trickyLink/../original.txt"                             | "sub"
        "root" | "trickyLink/../../root.txt"                              | "sub"
    }

    def "symlink relativeness checks forbid other symlinks in path"() {
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

        def inputRoot = inputDirectory.file(root)

        when:
        buildKotlinFile << constructBuildScript(
            configureStrategy(LinksStrategy.PRESERVE_RELATIVE),
            root
        )

        then:
        def relativePathToLink = relativePath(inputRoot, link)
        def relativePathToTrickyLink = relativePath(inputRoot, trickyLink)

        fails(mainTask).assertThatCause(
            anyOf(
                startsWith("Links strategy is set to PRESERVE_RELATIVE, but a symlink pointing outside was visited: '$relativePathToLink' pointing to '$symlinkTarget'."),
                startsWith("Links strategy is set to PRESERVE_RELATIVE, but a symlink pointing outside was visited: '$relativePathToTrickyLink' pointing to '$trickyLinkTarget'.")
            )
        )

        where:
        root   | symlinkTarget                                            | trickyLinkTarget
        "root" | "trickyLink"                                             | "original.txt"
        "root" | "../subroot/trickyLink"                                  | "../subroot/original.txt"
        "root" | "../subroot/trickyLink/../../root.txt"                   | "sub"
        "root" | "../subroot/trickyLink/../../subroot/trickyLink/sub.txt" | "sub"
        "root" | "trickyLink/../original.txt"                             | "sub"
        "root" | "trickyLink/../external.txt"                             | ".."
        "root" | "trickyLink/../root.txt"                                 | "../../root/subroot"
        "root" | "../subroot/trickyLink/../root.txt"                      | "../../root/subroot"
        "root" | "trickyLink/../../root.txt"                              | "sub"
        "root" | "trickyLink/../root.txt"                                 | "../../root/nonexisting"    // nonexistent file can be replaced by symlink
        "root" | "nonExistent/original.txt"                               | "stub"
    }

    def "sibling spec should be processed properly with main linksStrategy=#linksStrategyMain and sibling linksStrategy=#linksStrategySibling"() {
        given:
        def inputWithSiblingSpec = inputDirectory.createDir("input-subling")
        def inputWithMainSpec = inputDirectory.createDir("input-main")
        for (dir in [inputWithSiblingSpec, inputWithMainSpec]) {
            def originalFile = dir.createFile("original-${dir.name}.txt") << "some text"
            dir.file("link-${dir.name}").createLink(originalFile.name)
        }

        buildKotlinFile << constructBuildScript(
            """
                ${configureStrategy(linksStrategyMain)}
                from("${inputDirectory.name}/${inputWithSiblingSpec.name}") {
                    ${configureStrategy(linksStrategySibling)}
                }
                """,
            inputWithMainSpec.name
        )

        when:
        succeeds(mainTask)
        def outputDirectory = getResultDir()

        then:
        def linkSibling = outputDirectory.file("link-${inputWithSiblingSpec.name}")
        "$expectedOutcomeSibling"(linkSibling, outputDirectory.file("original-${inputWithSiblingSpec.name}.txt"))

        def linkMain = outputDirectory.file("link-${inputWithMainSpec.name}")
        "$expectedOutcomeMain"(linkMain, outputDirectory.file("original-${inputWithMainSpec.name}.txt"))

        where:
        linksStrategyMain          | linksStrategySibling       | expectedOutcomeMain | expectedOutcomeSibling
        LinksStrategy.FOLLOW       | LinksStrategy.FOLLOW       | "isCopy"            | "isCopy"
        LinksStrategy.FOLLOW       | LinksStrategy.PRESERVE_ALL | "isCopy"            | "isValidSymlink"
        LinksStrategy.PRESERVE_ALL | LinksStrategy.FOLLOW       | "isValidSymlink"    | "isCopy"
        LinksStrategy.PRESERVE_ALL | LinksStrategy.PRESERVE_ALL | "isValidSymlink"    | "isValidSymlink"
    }

    def "when root is a link to #rootLinkTarget, it should be #expectedOutcome if linksStrategy=#linksStrategy"() {
        given:
        def externalFile = inputDirectory.createFile("file") << "external text"
        def rootDir = inputDirectory.createDir("dir")
        def rootFile = rootDir.createFile("root.txt") << "root text"
        def link = inputDirectory.file("link").createLink(rootLinkTarget)

        when:
        buildKotlinFile << constructBuildScript(
            configureStrategy(linksStrategy),
            link.name
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
                isBrokenSymlink(output.file(link.name), output.file(rootLinkTarget))
                break
            case "copied as file":
                def outputFile = output.file(link.name)
                assert Files.isRegularFile(outputFile.toPath(), NOFOLLOW_LINKS)
                isCopy(outputFile, externalFile)
                break
            case "copied as dir":
                assert Files.isDirectory(output.toPath(), NOFOLLOW_LINKS)
                isCopy(output.file("root.txt"), rootFile)
                break
            case "treated as error":
                assert failure.assertHasCause("Links strategy is set to ${linksStrategy}, but a symlink was visited: '${link.name}' pointing to '$rootLinkTarget'")
                break
            case "treated as broken link error":
                assert failure.assertHasCause("Couldn't follow symbolic link '.' pointing to '${rootLinkTarget}'")
                break
            case "treated as relativeness error":
                assert failure.assertHasCause("Links strategy is set to ${linksStrategy}, but a symlink pointing outside was visited: '${link.name}' pointing to '$rootLinkTarget'")
                break
            case "ignored":
                assert !output.exists()
                break
        }

        where:
        rootLinkTarget      | linksStrategy                   | expectedOutcome
        "dir"               | LinksStrategy.FOLLOW            | "copied as dir"
        "dir"               | LinksStrategy.PRESERVE_ALL      | "copied as link" // it would be inside output dir, but that's consistent with file behavior
        "dir"               | LinksStrategy.PRESERVE_RELATIVE | "treated as relativeness error"
        "dir"               | LinksStrategy.ERROR             | "treated as error"

        "file"              | LinksStrategy.FOLLOW            | "copied as file"
        "file"              | LinksStrategy.PRESERVE_ALL      | "copied as link"
        "file"              | LinksStrategy.PRESERVE_RELATIVE | "treated as relativeness error"
        "file"              | LinksStrategy.ERROR             | "treated as error"

        "non-existent-file" | LinksStrategy.FOLLOW            | "treated as broken link error"
        "non-existent-file" | LinksStrategy.PRESERVE_ALL      | "copied as link"
        "non-existent-file" | LinksStrategy.PRESERVE_RELATIVE | "treated as relativeness error"
        "non-existent-file" | LinksStrategy.ERROR             | "treated as error"
    }
}

abstract class AbstractArchiveCopySymlinksIntegrationSpec extends AbstractCopySymlinksIntegrationSpec {

    @Override
    LinksStrategy getDefaultLinksStrategy() {
        LinksStrategy.PRESERVE_RELATIVE
    };

    def "symlinked files with absolute path should be treated as error if linksStrategy=LinksStrategy.FOLLOW"() {
        given:
        def originalFile = inputDirectory.createFile("original.txt") << "some text"
        def link = inputDirectory.file("link").createLink(originalFile)

        when:
        buildKotlinFile << constructBuildScript(
            configureStrategy(LinksStrategy.FOLLOW)
        )

        then:
        fails(mainTask)
        assertHasCauseIgnoringUnicodeNormaization("Couldn't follow symbolic link '${link.name}' pointing to '${originalFile}'.")
    }

    def "intermediate symlinks are followed properly if relative"() {
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

        def inputRoot = inputDirectory.file(root)

        when:
        buildKotlinFile << constructBuildScript(
            configureStrategy(LinksStrategy.FOLLOW),
            root
        )

        then:
        succeeds(mainTask)
        def outputDirectory = getResultDir()

        def linkCopy = outputDirectory.file(relativePath(inputRoot, link))
        def linkTargetCopy = outputDirectory.file(relativePathToTarget(inputRoot, link))
        isCopy(linkCopy, linkTargetCopy)

        def trickyLinkCopy = outputDirectory.file(relativePath(inputRoot, trickyLink))
        def trickyLinkTargetCopy = outputDirectory.file(relativePathToTarget(inputRoot, trickyLink))
        isCopy(trickyLinkCopy, trickyLinkTargetCopy)

        where:
        root   | symlinkTarget                                            | trickyLinkTarget
        "root" | "trickyLink"                                             | "original.txt"
        "root" | "../subroot/trickyLink"                                  | "../subroot/original.txt"
        "root" | "../subroot/trickyLink/../../subroot/trickyLink/sub.txt" | "sub"
        "root" | "trickyLink/../original.txt"                             | "sub"
        "root" | "trickyLink/../../root.txt"                              | "sub"
        "root" | "../subroot/trickyLink/../../root.txt"                   | "sub"
    }

    def "symlink relativeness checks allow other symlinks in path if they are in the same zip"() {
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

        def inputRoot = inputDirectory.file(root)

        when:
        buildKotlinFile << constructBuildScript(
            configureStrategy(LinksStrategy.PRESERVE_RELATIVE),
            root
        )

        then:
        def relativePathToLink = relativePath(inputRoot, link)
        def relativePathToTrickyLink = relativePath(inputRoot, trickyLink)

        if (error) {
            fails(mainTask).assertThatCause(
                anyOf(
                    startsWith("Links strategy is set to PRESERVE_RELATIVE, but a symlink pointing outside was visited: '$relativePathToLink' pointing to '$symlinkTarget'."),
                    startsWith("Links strategy is set to PRESERVE_RELATIVE, but a symlink pointing outside was visited: '$relativePathToTrickyLink' pointing to '$trickyLinkTarget'.")
                )
            )
        } else {
            succeeds(mainTask)
            def outputDirectory = getResultDir()

            def linkCopy = outputDirectory.file(relativePathToLink)
            def linkTargetCopy = outputDirectory.file(relativePathToTarget(inputRoot, link))
            isValidSymlink(linkCopy, linkTargetCopy)

            def trickyLinkCopy = outputDirectory.file(relativePathToTrickyLink)
            def trickyLinkTargetCopy = outputDirectory.file(relativePathToTarget(inputRoot, trickyLink))
            isValidSymlink(trickyLinkCopy, trickyLinkTargetCopy)
        }

        where:
        root   | symlinkTarget                                            | trickyLinkTarget          | error
        "root" | "trickyLink"                                             | "original.txt"            | false
        "root" | "../subroot/trickyLink"                                  | "../subroot/original.txt" | false
        "root" | "../subroot/trickyLink/../../root.txt"                   | "sub"                     | false
        "root" | "../subroot/trickyLink/../../subroot/trickyLink/sub.txt" | "sub"                     | false
        "root" | "trickyLink/../original.txt"                             | "sub"                     | false
        "root" | "trickyLink/../external.txt"                             | ".."                      | true
        "root" | "trickyLink/../root.txt"                                 | "../../root/subroot"      | true
        "root" | "../subroot/trickyLink/../root.txt"                      | "../../root/subroot"      | true
        "root" | "trickyLink/../../root.txt"                              | "sub"                     | false
        "root" | "trickyLink/../root.txt"                                 | "../../root/nonexisting"  | true
        "root" | "nonExistent/original.txt"                               | "stub"                    | true
    }
}
