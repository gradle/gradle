multi-project-with-buildSrc
===========================

A [multi-project build][1] with common logic defined in [`buildSrc`](./buildSrc) including a [custom task](./buildSrc/src/main/kotlin/HelloTask.kt#L5).

List the available tasks with:

    ./gradlew tasks --task
 
Run with:

    ./gradlew -q hello

[1]: https://docs.gradle.org/current/userguide/multi_project_builds.html#sec:multi_project_and_buildsrc
