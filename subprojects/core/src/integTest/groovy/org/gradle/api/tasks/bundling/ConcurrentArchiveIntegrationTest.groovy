/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.tasks.bundling

import org.apache.commons.io.FileUtils
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Rule
import spock.lang.Issue

class ConcurrentArchiveIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    @Issue("https://github.com/gradle/gradle/issues/22685")
    def "can visit and edit zip archive differently from two different projects in a multiproject build"() {
        given: "an archive in the root of a multiproject build"
        createZip('test.zip') {
            subdir1 {
                file ('file1.txt').text = 'original text 1'
            }
            subdir2 {
                file('file2.txt').text = 'original text 2'
                file ('file3.txt').text =  'original text 3'
            }
        }
        settingsFile << """include 'project1', 'project2'"""

        and: "where each project edits that same archive differently via a visitor"
        file('project1/build.gradle') << """
            ${defineUpdateTask('zip')}
            ${defineVerifyTask('zip')}

            def theArchive = rootProject.file('test.zip')

            tasks.register('update', UpdateTask) {
                archive = theArchive
                replacementText = 'modified by project1'
            }

            tasks.register('verify', VerifyTask) {
                dependsOn tasks.named('update')
                archive = theArchive
                beginsWith = 'modified by project1'
            }
        """

        file('project2/build.gradle') << """
            ${defineUpdateTask('zip')}
            ${defineVerifyTask('zip')}

            def theArchive = rootProject.file('test.zip')

            tasks.register('update', UpdateTask) {
                archive = theArchive
                replacementText = 'edited by project2'
            }

            tasks.register('verify', VerifyTask) {
                dependsOn tasks.named('update')
                archive = theArchive
                beginsWith = 'edited by project2'
            }
        """

        when:
        run 'verify'

        then:
        result.assertTasksExecutedAndNotSkipped(':project1:update', ':project2:update', ':project1:verify', ':project2:verify')
    }

    @Issue("https://github.com/gradle/gradle/issues/22685")
    def "can visit and edit tar archive differently from two different projects in a multiproject build"() {
        given: "an archive in the root of a multiproject build"
        createTar('test.tar') {
            subdir1 {
                file ('file1.txt').text = 'original text 1'
            }
            subdir2 {
                file('file2.txt').text = 'original text 2'
                file ('file3.txt').text =  'original text 3'
            }
        }
        settingsFile << """include 'project1', 'project2'"""

        and: "where each project edits that same archive differently via a visitor"
        file('project1/build.gradle') << """
            ${defineUpdateTask('tar')}
            ${defineVerifyTask('tar')}

            def theArchive = rootProject.file('test.tar')

            tasks.register('update', UpdateTask) {
                archive = theArchive
                replacementText = 'modified by project1'
            }

            tasks.register('verify', VerifyTask) {
                dependsOn tasks.named('update')
                archive = theArchive
                beginsWith = 'modified by project1'
            }
        """

        file('project2/build.gradle') << """
            ${defineUpdateTask('tar')}
            ${defineVerifyTask('tar')}

            def theArchive = rootProject.file('test.tar')

            tasks.register('update', UpdateTask) {
                archive = theArchive
                replacementText = 'edited by project2'
            }

            tasks.register('verify', VerifyTask) {
                dependsOn tasks.named('update')
                archive = theArchive
                beginsWith = 'edited by project2'
            }
        """

        when:
        run 'verify'

        then:
        result.assertTasksExecutedAndNotSkipped(':project1:update', ':project2:update', ':project1:verify', ':project2:verify')
    }

    @Requires(IntegTestPreconditions.NotEmbeddedExecutor)
    @Issue("https://github.com/gradle/gradle/issues/22685")
    def "can visit and edit zip archive differently from settings script when gradle is run in two simultaneous processes"() {
        given: "a started server which can be used to cause the edits to begin at approximately the same time"
        server.start()

        and: "a zip archive in the root with many files with the same content, so that the editing won't finish too quickly"
        createZip('test.zip') {
            for (int i = 0; i < 1000; i++) {
                "subdir1$i" {
                    file('file1.txt').text = 'original'
                }
                "subdir2$i" {
                    file('file2.txt').text = 'original'
                    file('file3.txt').text = 'original'
                }
            }
        }

        and: "a settings script which edits the zip archive and then verifies that all files contain the same content"
        settingsFile << """
            ${server.callFromBuild('wait')}

            void update() {
                FileTree tree = zipTree(new File(settings.rootDir, 'test.zip'))
                tree.visit(new EditingFileVisitor())
            }

            final class EditingFileVisitor implements FileVisitor {
                private final int num = new Random().nextInt(100000)

                @Override
                void visitDir(FileVisitDetails dirDetails) {}

                @Override
                void visitFile(FileVisitDetails fileDetails) {
                    fileDetails.file.text = fileDetails.file.text.replace('original', Integer.toString(num))
                }
            }

            void verify() {
                FileTree tree = zipTree(new File(settings.rootDir, 'test.zip'))
                tree.visit(new VerifyingFileVisitor())
            }

            final class VerifyingFileVisitor implements FileVisitor {
                private String contents

                @Override
                void visitDir(FileVisitDetails dirDetails) {}

                @Override
                void visitFile(FileVisitDetails fileDetails) {
                    if (contents == null) {
                        contents = fileDetails.file.text
                    } else {
                        assert fileDetails.file.text == contents
                    }
                }
            }

            update()
            verify()
        """

        server.expectConcurrent('wait', 'wait')

        when: "two processes are started, which will edit the settings file at the same time"
        def handle1 = executer.withTasks('tasks').start()
        def handle2 = executer.withTasks('tasks').start()

        and: "they both complete"
        def result1 = handle1.waitForFinish()
        def result2 = handle2.waitForFinish()

        then: "both builds ran, with the settings script editing the archive atomically"
        result1.assertTasksExecuted(':tasks')
        result2.assertTasksExecuted(':tasks')

        cleanup:
        handle1?.abort()
        handle2?.abort()
        server.stop()
    }

    @Requires(IntegTestPreconditions.NotEmbeddedExecutor)
    @Issue("https://github.com/gradle/gradle/issues/22685")
    def "can visit and edit tar archive differently from settings script when gradle is run in two simultaneous processes"() {
        given: "a started server which can be used to cause the edits to begin at approximately the same time"
        server.start()

        and: "a tar archive in the root with many files with the same content, so that the editing won't finish too quickly"
        createTar('test.tar') {
            for (int i = 0; i < 1000; i++) {
                "subdir1$i" {
                    file('file1.txt').text = 'original'
                }
                "subdir2$i" {
                    file('file2.txt').text = 'original'
                    file('file3.txt').text = 'original'
                }
            }
        }

        and: "a settings script which edits the tar archive and then verifies that all files contain the same content"
        settingsFile << """
            ${server.callFromBuild('wait')}

            void update() {
                FileTree tree = tarTree(new File(settings.rootDir, 'test.tar'))
                tree.visit(new EditingFileVisitor())
            }

            final class EditingFileVisitor implements FileVisitor {
                private final int num = new Random().nextInt(100000)

                @Override
                void visitDir(FileVisitDetails dirDetails) {}

                @Override
                void visitFile(FileVisitDetails fileDetails) {
                    fileDetails.file.text = fileDetails.file.text.replace('original', Integer.toString(num))
                }
            }

            void verify() {
                FileTree tree = tarTree(new File(settings.rootDir, 'test.tar'))
                tree.visit(new VerifyingFileVisitor())
            }

            final class VerifyingFileVisitor implements FileVisitor {
                private String contents

                @Override
                void visitDir(FileVisitDetails dirDetails) {}

                @Override
                void visitFile(FileVisitDetails fileDetails) {
                    if (contents == null) {
                        contents = fileDetails.file.text
                    } else {
                        assert fileDetails.file.text == contents
                    }
                }
            }

            update()
            verify()
        """

        server.expectConcurrent('wait', 'wait')

        when: "two processes are started, which will edit the settings file at the same time"
        def handle1 = executer.withTasks('tasks').start()
        def handle2 = executer.withTasks('tasks').start()

        and: "they both complete"
        def result1 = handle1.waitForFinish()
        def result2 = handle2.waitForFinish()

        then: "both builds ran, with the settings script editing the archive atomically"
        result1.assertTasksExecuted(':tasks')
        result2.assertTasksExecuted(':tasks')

        cleanup:
        handle1?.abort()
        handle2?.abort()
        server.stop()
    }

    def "can operate on 2 different tar files in the same project"() {
        given: "2 archive files"
        createTar('test1.tar') {
            subdir1 {
                file ('file.txt').text = 'original text 1'
            }
            subdir2 {
                file('file2.txt').text = 'original text 2'
                file ('file3.txt').text =  'original text 3'
            }
        }
        createTar('test2.tar') {
            subdir1 {
                file ('file.txt').text = 'original text 1' // Same name in same dir
            }
            subdir3 {
                file('file3.txt').text = 'original text 3' // Same name in same different nested dir
                file ('file4.txt').text =  'original text 4'
            }
        }

        and: "where a build edits each archive differently via a visitor"
        file('build.gradle') << """
            ${defineUpdateTask('tar')}
            ${defineVerifyTask('tar')}

            def theArchive1 = rootProject.file('test1.tar')
            def theArchive2 = rootProject.file('test2.tar')

            tasks.register('update1', UpdateTask) {
                archive = theArchive1
                replacementText = 'modification 1'
            }

            tasks.register('verify1', VerifyTask) {
                dependsOn tasks.named('update1')
                archive = theArchive1
                beginsWith = 'modification 1'
            }

            tasks.register('update2', UpdateTask) {
                archive = theArchive2
                replacementText = 'modification 2'
            }

            tasks.register('verify2', VerifyTask) {
                dependsOn tasks.named('update2')
                archive = theArchive2
                beginsWith = 'modification 2'
            }
        """

        when:
        run 'verify1', 'verify2'

        then:
        result.assertTasksExecutedAndNotSkipped(':update1', ':update2', ':verify1', ':verify2')
    }

    def "can operate on 2 different zip files in the same project"() {
        given: "2 archive files"
        createZip('test1.zip') {
            subdir1 {
                file ('file.txt').text = 'original text 1'
            }
            subdir2 {
                file('file2.txt').text = 'original text 2'
                file ('file3.txt').text =  'original text 3'
            }
        }
        createZip('test2.zip') {
            subdir1 {
                file ('file.txt').text = 'original text 1' // Same name in same dir
            }
            subdir3 {
                file('file3.txt').text = 'original text 3' // Same name in same different nested dir
                file ('file4.txt').text =  'original text 4'
            }
        }

        and: "where a build edits each archive differently via a visitor"
        file('build.gradle') << """
            ${defineUpdateTask('zip')}
            ${defineVerifyTask('zip')}

            def theArchive1 = rootProject.file('test1.zip')
            def theArchive2 = rootProject.file('test2.zip')

            tasks.register('update1', UpdateTask) {
                archive = theArchive1
                replacementText = 'modification 1'
            }

            tasks.register('verify1', VerifyTask) {
                dependsOn tasks.named('update1')
                archive = theArchive1
                beginsWith = 'modification 1'
            }

            tasks.register('update2', UpdateTask) {
                archive = theArchive2
                replacementText = 'modification 2'
            }

            tasks.register('verify2', VerifyTask) {
                dependsOn tasks.named('update2')
                archive = theArchive2
                beginsWith = 'modification 2'
            }
        """

        when:
        run 'verify1', 'verify2'

        then:
        result.assertTasksExecutedAndNotSkipped(':update1', ':update2', ':verify1', ':verify2')
    }

    def "when two identical archives have the same hashes and same decompression cache entry is reused"() {
        given: "2 archive files"
        createTar('test1.tar') {
            subdir1 {
                file('file.txt').text = 'original text 1'
            }
            subdir2 {
                file('file2.txt').text = 'original text 2'
                file('file3.txt').text = 'original text 3'
            }
        }
        FileUtils.copyFile(file('test1.tar'), file('test2.tar'));

        and: "where a build edits each archive differently via a visitor"
        file('build.gradle') << """
            ${defineUpdateTask('tar')}
            ${defineVerifyTask('tar')}

            def theArchive1 = rootProject.file('test1.tar')
            def theArchive2 = rootProject.file('test2.tar')

            tasks.register('update1', UpdateTask) {
                archive = theArchive1
                replacementText = 'modification 1'
            }

            tasks.register('update2', UpdateTask) {
                archive = theArchive2
                replacementText = 'modification 2'
            }

            tasks.register('verify') {
                dependsOn tasks.named('update1'), tasks.named('update2')
                doLast {
                    def cacheDir = file("build/tmp/.cache/expanded")
                    assert cacheDir.list().size() == 1
                    cacheDir.eachFile(groovy.io.FileType.DIRECTORIES) { File f ->
                        assert f.name.startsWith('tar_')
                    }

                    def lockDir = file("${executer.gradleUserHomeDir.file("caches/expanded")}")
                    assert lockDir.list().size() == 1
                    assert lockDir.list().contains('expanded.lock')
                }
            }
        """

        when:
        succeeds 'verify'

        then:
        result.assertTasksExecutedAndNotSkipped(':update1', ':update2', ':verify')
    }

    @Issue("https://github.com/gradle/gradle/issues/23253")
    def "decompression cache for zip archives respects relocated build dir"() {
        given:
        createZip('test.zip') {
            subdir1 {
                file ('file.txt').text = 'original text 1'
            }
            subdir2 {
                file('file2.txt').text = 'original text 2'
                file ('file3.txt').text =  'original text 3'
            }
        }

        and: "a build using a relocated build dir"
        buildFile << """
            plugins {
                id 'java-library'
            }

            project.buildDir = project.file('new-location')

            ${defineVerifyTask('zip')}

            def theArchive = rootProject.file('test.zip')

            tasks.register('verify', VerifyTask) {
                archive = theArchive
                beginsWith = 'original'
            }
        """

        and: "a source file exists to be compiled into the new build dir"
        file('src/main/java/MyClass.java') << """
            public class MyClass {
                public static void main(String[] args) {
                    System.out.println("Hello world");
                }
            }
        """

        when:
        succeeds 'verify', 'build'

        then: "the build dir is relocated, and the decompression cache is also relocated under it"
        file('new-location/tmp/.cache/expanded').exists()
        !file('build').exists()
    }

    @Issue("https://github.com/gradle/gradle/issues/23253")
    def "decompression cache for tar archives respects relocated build dir"() {
        given:
        createTar('test.tar') {
            subdir1 {
                file ('file.txt').text = 'original text 1'
            }
            subdir2 {
                file('file2.txt').text = 'original text 2'
                file ('file3.txt').text =  'original text 3'
            }
        }

        and: "a build using a relocated build dir"
        buildFile << """
            plugins {
                id 'java-library'
            }

            project.buildDir = project.file('new-location')

            ${defineVerifyTask('tar')}

            def theArchive = rootProject.file('test.tar')

            tasks.register('verify', VerifyTask) {
                archive = theArchive
                beginsWith = 'original'
            }
        """

        and: "a source file exists to be compiled into the new build dir"
        file('src/main/java/MyClass.java') << """
            public class MyClass {
                public static void main(String[] args) {
                    System.out.println("Hello world");
                }
            }
        """

        when:
        succeeds 'verify', 'build'

        then: "the build dir is relocated, and the decompression cache is also relocated under it"
        file('new-location/tmp/.cache/expanded').exists()
        !file('build').exists()
    }

    private def createTar(String name, Closure cl) {
        TestFile tarRoot = file("${name}.root")
        tarRoot.deleteDir()
        TestFile tar = file(name)
        tar.delete()
        tarRoot.create(cl)
        tarRoot.tarTo(tar)
    }

    private String defineUpdateTask(String archiveType) {
        return """
            abstract class UpdateTask extends DefaultTask {
                @InputFile
                abstract RegularFileProperty getArchive()

                @Input
                abstract Property<String> getReplacementText()

                @Inject abstract ArchiveOperations getArchiveOperations()

                @TaskAction
                void update() {
                    FileTree tree = archiveOperations.${archiveType}Tree(archive.asFile.get())
                    tree.visit(new EditingFileVisitor())
                }

                private final class EditingFileVisitor implements FileVisitor {
                    @Override
                    void visitDir(FileVisitDetails dirDetails) {}

                    @Override
                    void visitFile(FileVisitDetails fileDetails) {
                        fileDetails.file.text = fileDetails.file.text.replace('original', replacementText.get())
                    }
                }
            }
        """
    }

    private String defineVerifyTask(String archiveType) {
        return """
            abstract class VerifyTask extends DefaultTask {
                @InputFile
                abstract RegularFileProperty getArchive()

                @Input
                abstract Property<String> getBeginsWith()

                @Inject abstract ArchiveOperations getArchiveOperations()

                @TaskAction
                void verify() {
                    FileTree tree = archiveOperations.${archiveType}Tree(archive.asFile.get())
                    tree.visit(new VerifyingFileVisitor())
                }

                private final class VerifyingFileVisitor implements FileVisitor {
                    @Override
                    void visitDir(FileVisitDetails dirDetails) {}

                    @Override
                    void visitFile(FileVisitDetails fileDetails) {
                        assert fileDetails.file.text.startsWith(beginsWith.get())
                    }
                }
            }
        """
    }
}
