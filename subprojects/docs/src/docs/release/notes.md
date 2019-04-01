The Gradle team is excited to announce Gradle @version@.

This release features a [new API for Incremental Changes](#incremental-changes-api), updates to [building native software with Gradle](#native-support), [Swift 5 Support](#swift5-support), [running Gradle on JDK12](#jdk12-support) and more.

Read the [Gradle 5.x upgrade guide](userguide/upgrading_version_5.html) to learn about breaking changes and considerations for upgrading from Gradle 5.0.
If upgrading from Gradle 4.x, please read [upgrading from Gradle 4.x to 5.0](userguide/upgrading_version_4.html) first.

We would like to thank the following community contributors to this release of Gradle:
<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)
-->

[Ian Kerins](https://github.com/isker),
[Rodolfo Forte](https://github.com/Tschis),
and [Stefan M.](https://github.com/StefMa).

## Upgrade Instructions

Switch your build to use Gradle @version@ by updating your wrapper properties:

`./gradlew wrapper --gradle-version=@version@`

<a name="incremental-changes-api"/>

## New API for Incremental Changes

The new `org.gradle.work` package provides behavior that allow access to any input files that need to be processed by an incremental work action.
The following `Checksum` example task calculates a SHA256 on each given input. Inputs may be updated at any time, for example by adding more inputs
to the `sources` property or by updating the content of any given input that was previously listed.

```
import com.google.common.hash.Hashing
import com.google.common.io.Files
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileType
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.work.ChangeType
import org.gradle.work.FileChange
import org.gradle.work.InputChanges

class Checksum extends DefaultTask {
    @SkipWhenEmpty
    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputFiles
    ConfigurableFileCollection sources = project.files()

    @OutputDirectory
    DirectoryProperty outputDirectory = project.objects.directoryProperty()

    @TaskAction
    void checksum(InputChanges changes) {
        File outputDir = outputDirectory.get().asFile
        if (!changes.incremental) {
            println("Non-incremental changes - cleaning output directory")
            outputDir.deleteDir()
            outputDir.mkdirs()
        }
        for(FileChange change : changes.getFileChanges(sources)) {
            File changedFile = change.file
            if (change.fileType == FileType.FILE) {
                switch(change.changeType) {
                    case ChangeType.ADDED: // fall through
                    case ChangeType.MODIFIED:
                        outputFileForInput(change).text = hashFileContents(changedFile)
                        break
                    case ChangeType.REMOVED:
                        deleteStaleOutput(outputFileForInput(change))
                        break
                }
            }
        }
    }

    private void deleteStaleOutput( File file) {
        println("Removing old output ${file.name}")
        file.delete()
    }

    private File outputFileForInput(FileChange inputFile) {
        project.file("${outputDirectory.get().asFile}/${inputFile.normalizedPath}.sha256")
    }

    private String hashFileContents(File file) {
        println("Hashing ${file.name}")
        return Files.asByteSource(file).hash(Hashing.sha256()).toString()
    }
}
```

This `CheckSum` task can be configured to incrementally checksum the files on the runtime classpath and everything under the inputs directory:

```
plugins {
    id "java"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.junit.platform:junit-platform-engine:1.4.1")
    implementation("org.junit.jupiter:junit-jupiter:5.4.1")
}

tasks.register("checksum", Checksum) {
    sources.from(configurations.runtimeClasspath)
    sources.from({
        project.fileTree("inputs").files
    })
    outputDirectory.set(layout.buildDirectory.dir("checksummed"))
}
```

On a first run, everything needs to be checksummed.

```console
$> ./gradlew checksum

> Task :checksum
Non-incremental changes - cleaning output directory
Hashing junit-jupiter-5.4.1.jar
Hashing junit-jupiter-engine-5.4.1.jar
Hashing junit-platform-engine-1.4.1.jar
Hashing junit-jupiter-params-5.4.1.jar
Hashing junit-jupiter-api-5.4.1.jar
Hashing junit-platform-commons-1.4.1.jar
Hashing apiguardian-api-1.0.0.jar
Hashing opentest4j-1.1.1.jar
Hashing something-to-checksum.txt

BUILD SUCCESSFUL in 0s
1 actionable task: 1 executed
```

If we change `inputs/something-to-checksum.txt` and add a new file - `inputs/new-file.txt`, then only the two new files are processed.

```console
$> ./gradlew checksum

> Task :checksum
Hashing something-to-checksum.txt
Hashing new-file.txt

BUILD SUCCESSFUL in 0s
1 actionable task: 1 executed
```

If we now remove the new file again, then the file is removed.

```console
$> ./gradlew checksum

> Task :checksum
Removing old output new-file.txt.sha256

BUILD SUCCESSFUL in 0s
1 actionable task: 1 execute
```

<a name="native-support"/>

## Building native software with Gradle

Updates include relocating generated object files to separate directories per variant; usage of the new Incremental Changes API. See more information about the [Gradle native project](https://github.com/gradle/gradle-native/blob/master/docs/RELEASE-NOTES.md#changes-included-in-gradle-54).

<a name="swift5-support"/>

### Swift 5 Support

Gradle now supports [Swift 5](https://swift.org/blog/swift-5-released/) officially [release with the Xcode 10.2](https://developer.apple.com/documentation/xcode_release_notes/xcode_10_2_release_notes).
Specifying the source compatibility to Swift 5 instruct the compiler to expect Swift 5 compatible source files.
Have a look at the [Swift samples](https://github.com/gradle/native-samples) to learn more about common use cases.

<a name="jdk12-support"/>

## Support for JDK12

Gradle now supports running on [JDK12](https://jdk.java.net/12/). 

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 6.0). See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

<!--
### Example deprecation
-->

### Using custom local build cache implementations

Using a custom build cache implementation for the local build cache is now deprecated.
The only allowed type will be `DirectoryBuildCache` going forward.
There is no change in the support for using custom build cache implementations as the remote build cache. 

### Breaking changes

<!-- summary and links -->

See the [Gradle 5.x upgrade guide](userguide/upgrading_version_5.html#changes_@baseVersion@) to learn about breaking changes and considerations when upgrading to Gradle @version@.

<!-- Do not add breaking changes here! Add them to the upgrade guide instead. --> 

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting Problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines. 
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
