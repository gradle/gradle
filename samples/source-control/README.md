source-control
==============

A project that depend on external sources:

 1. [external/](./external) implements the main algorithm to compute the answer to the ultimate question of Life, the Universe and Everything
 2. [sample/](./sample) implements the command line interface

This sample uses a local Git repository, a realistic example would use a remote one.

**Run `cd external && ./gradlew generateGitRepo` first** to generate the local repository.

Then run with:

    cd sample
    ./gradlew run

And check compilation dependencies with:

    cd sample
    ./gradlew dependencies --configuration compileClasspath

See [sample/settings.gradle.kts](./sample/settings.gradle.kts).

