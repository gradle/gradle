project-with-buildSrc
=====================

A build with logic defined in [`buildSrc`](./buildSrc) including a [custom task](./buildSrc/src/main/kotlin/HelloTask.kt#L5), a [custom precompiled script plugin](./buildSrc/src/main/kotlin/my-plugin.gradle.kts) as well as [an extension property and an extension function](./buildSrc/src/main/kotlin/extensions.kt).

List the available tasks with:

    ./gradlew tasks --task

Run with:

    ./gradlew -q hello
    ./gradlew -q printProfile
    ./gradlew -q printProfile -Pprofile=prod

