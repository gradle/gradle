multi-project-with-buildSrc
===========================

_Note: This is a copy of [the original sample from the gradle-script-kotlin repository][2]_

A [multi-project build][1] with common logic defined in [`buildSrc`](./buildSrc) including a [custom task](./buildSrc/src/main/kotlin/HelloTask.kt#L5).

Gradle Script Kotlin support is enabled recursively for all projects in [settings.gradle](./settings.gradle#L3).

List the available tasks with:

    ./gradlew tasks --task
 
Run with:

    ./gradlew -q hello

[1]: https://docs.gradle.org/current/userguide/multi_project_builds.html#sec:multi_project_and_buildsrc
[2]: https://github.com/gradle/gradle-script-kotlin/tree/7c74044cd84c4c426f1bca9af9f48bf332620c73/samples/multi-project-with-buildSrc
