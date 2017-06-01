project-with-buildSrc
=====================

A build with logic defined in [`buildSrc`](./buildSrc) including a [custom task](./buildSrc/src/main/kotlin/HelloTask.kt#L5).

List the available tasks with:

    ./gradlew tasks --task

Run with:

    ./gradlew -q hello

