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

package org.gradle.api.file

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import spock.lang.Issue

class RelativePathFilesIntegrationTest extends AbstractIntegrationSpec {

    @Issue("https://github.com/gradle/gradle/issues/30052")
    def "provider-backed relative files are resolved relative to their owner"() {
        given:
        settingsFile """
            include("sub")
            include("other")
        """
        createDirs("other")

        buildFile "sub/build.gradle", """
            abstract class CustomTask extends DefaultTask {
                @InputFiles abstract ConfigurableFileCollection getIncoming()
                @TaskAction void run() { println("Effective files: \${incoming.files.toSorted()}") }
            }

            tasks.register("foo", CustomTask) {
                incoming.from(project.files(provider { "subFile.txt" }))
                incoming.from(project(":other").isolated.projectDirectory.files(provider { "otherFile.txt" }))
                incoming.from(layout.settingsDirectory.files(provider { "settingsFile.txt" }))
            }
        """

        when:
        run ":sub:foo"

        then:
        def files = ["sub/subFile.txt", "other/otherFile.txt", "settingsFile.txt"]
        outputContains("Effective files: ${files.collect { testDirectory.file(it) }.toSorted()}")
    }

    // Test implementation is not compatible with IP, but the use case will still exist, though might be more involved to set up
    @Requires(TestExecutionPreconditions.NotIsolatedProjects)
    @Issue("https://github.com/gradle/gradle/issues/30052")
    def "#container supports using relative paths at execution time"() {
        given:
        settingsFile """
            include("abc") // a sibling non-root project that is configured earlier
            include("sub")
        """

        file("abc/subDir1/file1.txt").touch()
        file("abc/subDir2/file2.txt").touch()

        buildFile "abc/build.gradle", """
            def fileCollection = project.objects.${creator}
            fileCollection.from("subDir1")
            project.ext.myFiles = fileCollection
        """

        buildFile "sub/build.gradle", """
            def otherProjectFiles = project(":abc").ext.myFiles
            tasks.register("foo") {
                doLast {
                    otherProjectFiles.from("subDir2")
                    println("files: \${otherProjectFiles.files.toSorted()}")
                }
            }
        """

        when:
        run ":sub:foo"

        then:
        outputContains("files: ${expectedFiles.collect { testDirectory.file(it) }.toSorted()}")

        where:
        container                    | creator            | expectedFiles
        "ConfigurableFileCollection" | "fileCollection()" | ["abc/subDir1", "abc/subDir2"]
        "ConfigurableFileTree"       | "fileTree()"       | ["abc/subDir2/file2.txt"]
    }

    def "ConfigurableFileCollection files derived from directory property via #method respect execution time directory change"() {
        settingsFile """
            include("sub")
        """

        buildFile "sub/build.gradle", """
            def dir = project.objects.directoryProperty()
            dir.set(file("subDir1"))

            def files = project.objects.fileCollection()

            tasks.register("foo") {
                def otherDir = file("subDir2")
                doLast {
                    dir.set(otherDir) // change the directory to point elsewhere
                    println("files: \${files.files.toSorted()}")
                }
            }
        """
        buildFile "sub/build.gradle", """
            files.from(${expression})
        """

        expect:
        if (GradleContextualExecuter.configCache) {
            fails(":sub:foo")
            failure.assertHasCause("The value for this property cannot be changed any further.")
        } else {
            succeeds(":sub:foo")
            outputContains("files: ${expectedFiles.collect { testDirectory.file(it) }.toSorted()}")
        }

        where:
        method                      | expression                        | expectedFiles
        "dir(String)"               | "dir.dir('subSubDir')"            | ["sub/subDir2/subSubDir"]
        "dir(Provider<String>)"     | "dir.dir(provider{'subSubDir'})"  | ["sub/subDir2/subSubDir"]
        "file(String)"              | "dir.file('file.txt')"            | ["sub/subDir2/file.txt"]
        "file(Provider<String>)"    | "dir.file(provider{'file.txt'})"  | ["sub/subDir2/file.txt"]
        "files(<string>)"           | "dir.files('file.txt')"           | ["sub/subDir2/file.txt"]
        "files(provider{<string>})" | "dir.files(provider{'file.txt'})" | ["sub/subDir2/file.txt"]
    }

    def "ConfigurableFileCollection files derived from regular file property respect execution time file change"() {
        settingsFile """
            include("sub")
        """

        buildFile "sub/build.gradle", """
            def regularFile = project.objects.fileProperty()
            regularFile.set(file("file1.txt"))

            def files = project.objects.fileCollection()
            files.from(regularFile)

            tasks.register("foo") {
                def otherFile = file("file2.txt")
                doLast {
                    regularFile.set(otherFile) // change the file to point elsewhere
                    println("files: \${files.files.toSorted()}")
                }
            }
        """

        expect:
        if (GradleContextualExecuter.configCache) {
            fails(":sub:foo")
            failure.assertHasCause("The value for this property cannot be changed any further.")
        } else {
            succeeds(":sub:foo")
            outputContains("files: ${["sub/file2.txt"].collect { testDirectory.file(it) }.toSorted()}")
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/32591")
    def "RegularFileProperty survives cyclic CC round-trip when its provider chain transitively references the property"() {
        given:
        file("buildSrc/src/main/java/my/PropHoldingTransformer.java") << """
            package my;
            import org.gradle.api.Transformer;
            import org.gradle.api.file.FileSystemLocation;
            import org.gradle.api.file.RegularFileProperty;
            import java.io.File;
            import java.util.Set;

            public class PropHoldingTransformer implements Transformer<File, Set<FileSystemLocation>> {
                private final RegularFileProperty prop;
                public PropHoldingTransformer(RegularFileProperty prop) { this.prop = prop; }
                @Override
                public File transform(Set<FileSystemLocation> locations) {
                    return locations.iterator().next().getAsFile();
                }
            }
        """
        file("buildSrc/src/main/java/my/CyclicBean.java") << """
            package my;
            import org.gradle.api.Project;
            import org.gradle.api.file.RegularFileProperty;
            import org.gradle.api.tasks.InputFile;
            import org.gradle.api.tasks.TaskProvider;

            public abstract class CyclicBean {
                @InputFile public abstract RegularFileProperty getProp();

                public void wire(Project project, TaskProvider<?> producer) {
                    getProp().fileProvider(
                        project.files(producer).getElements().map(new PropHoldingTransformer(getProp()))
                    );
                    getProp().disallowChanges();
                }
            }
        """
        file("buildSrc/src/main/java/my/CyclicTask.java") << """
            package my;
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.Nested;
            import org.gradle.api.tasks.TaskAction;

            public abstract class CyclicTask extends DefaultTask {
                @Nested public abstract CyclicBean getCyclic();
                @TaskAction public void run() {
                    System.out.println("value: " + getCyclic().getProp().get());
                }
            }
        """
        file("buildSrc/src/main/java/my/ProducerTask.java") << """
            package my;
            import org.gradle.api.DefaultTask;
            import org.gradle.api.file.RegularFileProperty;
            import org.gradle.api.tasks.OutputFile;
            import org.gradle.api.tasks.TaskAction;

            public abstract class ProducerTask extends DefaultTask {
                @OutputFile public abstract RegularFileProperty getOut();
                @TaskAction public void run() throws java.io.IOException {
                    java.nio.file.Files.writeString(getOut().get().getAsFile().toPath(), "hello");
                }
            }
        """
        buildFile """
            def producer = tasks.register("producer", my.ProducerTask) {
                out.set(layout.buildDirectory.file("produced.txt"))
            }
            tasks.register("cyclic", my.CyclicTask) { task ->
                task.getCyclic().wire(project, producer)
            }
        """

        expect:
        succeeds(":cyclic")
        and:
        succeeds(":cyclic")
        outputContains("value: " + file("build/produced.txt"))
    }

    @Issue("https://github.com/gradle/gradle/issues/32591")
    def "DirectoryProperty survives cyclic CC round-trip when its provider chain transitively references the property"() {
        given:
        file("buildSrc/src/main/java/my/DirPropHoldingTransformer.java") << """
            package my;
            import org.gradle.api.Transformer;
            import org.gradle.api.file.Directory;
            import org.gradle.api.file.DirectoryProperty;

            public class DirPropHoldingTransformer implements Transformer<Directory, Directory> {
                private final DirectoryProperty prop;
                public DirPropHoldingTransformer(DirectoryProperty prop) { this.prop = prop; }
                @Override
                public Directory transform(Directory input) {
                    return input;
                }
            }
        """
        file("buildSrc/src/main/java/my/CyclicDirBean.java") << """
            package my;
            import org.gradle.api.Project;
            import org.gradle.api.file.Directory;
            import org.gradle.api.file.DirectoryProperty;
            import org.gradle.api.provider.Provider;
            import org.gradle.api.tasks.InputDirectory;
            import org.gradle.api.tasks.TaskProvider;

            public abstract class CyclicDirBean {
                @InputDirectory public abstract DirectoryProperty getProp();

                public void wire(Project project, Provider<Directory> producerOut) {
                    Provider<Directory> asDir = producerOut.map(new DirPropHoldingTransformer(getProp()));
                    getProp().value(asDir);
                    getProp().disallowChanges();
                }
            }
        """
        file("buildSrc/src/main/java/my/CyclicDirTask.java") << """
            package my;
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.Nested;
            import org.gradle.api.tasks.TaskAction;

            public abstract class CyclicDirTask extends DefaultTask {
                @Nested public abstract CyclicDirBean getCyclic();
                @TaskAction public void run() {
                    System.out.println("value: " + getCyclic().getProp().get());
                }
            }
        """
        file("buildSrc/src/main/java/my/DirProducerTask.java") << """
            package my;
            import org.gradle.api.DefaultTask;
            import org.gradle.api.file.DirectoryProperty;
            import org.gradle.api.tasks.OutputDirectory;
            import org.gradle.api.tasks.TaskAction;

            public abstract class DirProducerTask extends DefaultTask {
                @OutputDirectory public abstract DirectoryProperty getOut();
                @TaskAction public void run() {
                    getOut().get().getAsFile().mkdirs();
                }
            }
        """
        buildFile """
            def producer = tasks.register("dirProducer", my.DirProducerTask) {
                out.set(layout.buildDirectory.dir("produced"))
            }
            tasks.register("cyclicDir", my.CyclicDirTask) { task ->
                task.getCyclic().wire(project, producer.flatMap { it.out })
            }
        """

        expect:
        succeeds(":cyclicDir")
        and:
        succeeds(":cyclicDir")
        outputContains("value: " + file("build/produced"))
    }

    @Issue("https://github.com/gradle/gradle/issues/32992")
    def "provider-backed relative files in directory properties via #method are resolved relative to their owner"() {
        given:
        settingsFile """
            include("sub")
        """

        buildFile "sub/build.gradle", """
            abstract class CustomTask extends DefaultTask {
                @InputFiles abstract DirectoryProperty getIncoming()
                @TaskAction void run() {
                    println("Effective file: \${incoming.get()}")
                    println("Effective fileTree: \${incoming.asFileTree.files.toSorted()}")
                }
            }

            def fooBarTxt = project.objects.fileProperty()
            fooBarTxt.set(file("fooBar.txt"))
            tasks.register("foo", CustomTask) {
                incoming.fileProvider(${expression})
            }
        """

        when:
        def directoryContents = ['foo.txt', 'bar.txt'].collect { testDirectory.createDir(expectedDir).file(it).touch() }
        file('sub/fooBar.txt') << "subDir/fileContent"
        executer.withEnvironmentVars([FOO: 'subDir/env'])
        executer.withArgument("-Dfoo.bar=subDir/sysprop")
        executer.withArgument("-Pbar=subDir/gradleProp")
        run ":sub:foo"

        then:
        outputContains("Effective file: ${ testDirectory.file(expectedDir) }")
        outputContains("Effective fileTree: ${ directoryContents.toSorted() }")

        where:
        method                      | expression                                                        | expectedDir
        "systemProperty"            | "providers.systemProperty('foo.bar').map { new File(it) }"        | "sub/subDir/sysprop"
        "environmentVariable"       | "providers.environmentVariable('FOO').map { new File(it) }"       | "sub/subDir/env"
        "gradleProperty"            | "providers.gradleProperty('bar').map { new File(it) }"            | "sub/subDir/gradleProp"
        "fileContents"              | "providers.fileContents(fooBarTxt).asText.map { new File(it) }"   | "sub/subDir/fileContent"
    }

    @Issue("https://github.com/gradle/gradle/issues/32992")
    def "provider-backed relative files in regular file properties via #method are resolved relative to their owner"() {
        given:
        settingsFile """
            include("sub")
        """

        buildFile "sub/build.gradle", """
            abstract class CustomTask extends DefaultTask {
                @InputFiles abstract RegularFileProperty getIncoming()
                @TaskAction void run() { println("Effective file: \${incoming.get()}") }
            }

            def fooTxt = project.objects.fileProperty()
            fooTxt.set(file("foo.txt"))
            tasks.register("foo", CustomTask) {
                incoming.fileProvider(${expression})
            }
        """

        when:
        file('sub/foo.txt') << "subDir/fileContent"
        executer.withEnvironmentVars([FOO: 'subDir/env'])
        executer.withArgument("-Dfoo.bar=subDir/sysprop")
        executer.withArgument("-Pbar=subDir/gradleProp")
        run ":sub:foo"

        then:
        outputContains("Effective file: ${ testDirectory.file(expectedFile) }")

        where:
        method                      | expression                                                        | expectedFile
        "systemProperty"            | "providers.systemProperty('foo.bar').map { new File(it) }"        | "sub/subDir/sysprop"
        "environmentVariable"       | "providers.environmentVariable('FOO').map { new File(it) }"       | "sub/subDir/env"
        "gradleProperty"            | "providers.gradleProperty('bar').map { new File(it) }"            | "sub/subDir/gradleProp"
        "fileContents"              | "providers.fileContents(fooTxt).asText.map { new File(it) }"      | "sub/subDir/fileContent"
    }
}
