Gradle Kotlin DSL
=================

[![TeamCity Build](https://builds.gradle.org/app/rest/builds/buildType:GradleKotlinDSL_Develop/statusIcon.svg)](https://builds.gradle.org/viewType.html?buildTypeId=GradleKotlinDSL_Develop)
[![TravisCI Build](https://img.shields.io/travis/gradle/kotlin-dsl/develop.svg)](https://travis-ci.org/gradle/kotlin-dsl)
[![License](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](http://www.apache.org/licenses/LICENSE-2.0)

Welcome! The _Gradle Kotlin DSL_ provides support for writing [Gradle](http://gradle.org) build scripts using JetBrains' [Kotlin](http://kotlinlang.org) language. It aims to provide Gradle users with a rich, flexible and statically-typed approach to developing build logic in conjunction with the best IDE and tooling experience possible.


Getting Started
---------------

The fastest way to get up and running with a Kotlin-based Gradle build is to use [gradle init](https://docs.gradle.org/current/userguide/build_init_plugin.html)

```
gradle init --dsl kotlin
```

or, if you don't have Gradle installed already, you can generate Gradle builds online at https://gradle-initializr.cleverapps.io/.

The Gradle Kotlin DSL is documented in a [dedicated chapter](https://docs.gradle.org/current/userguide/kotlin_dsl.html) in the Gradle user manual.

Moreover, the Gradle [user manual](https://docs.gradle.org/current/userguide/userguide.html) and [guides](https://gradle.org/guides/) contain build script excerpts that demonstrate both the Groovy DSL and the Kotlin DSL. This is the best place where to find how to do this and that with the Gradle Kotlin DSL; and it covers all Gradle features from [using plugins](https://docs.gradle.org/current/userguide/plugins.html#plugins) to [customizing the dependency resolution behavior](https://docs.gradle.org/current/userguide/customizing_dependency_resolution_behavior.html#customizing_dependency_resolution_behavior).

There are also some Gradle Kotlin DSL [samples](samples) in this repository. You'll find complete instructions in the README there.

If you are looking into migrating an existing build to the Gradle Kotlin DSL, please also check out the [migration guide](https://guides.gradle.org/migrating-build-logic-from-groovy-to-kotlin/).

You can read more about the project in our [announcement blog post](http://gradle.org/blog/kotlin-meets-gradle) and check out the [frequently asked questions](https://github.com/gradle/kotlin-dsl/wiki/Frequently-Asked-Questions) in the project wiki.


Issue Tracking
--------------

Found a bug? Have an idea for an improvement? Feel free to [add an issue](../../issues).

If you're dealing with what you believe to be an issue with Kotlin itself or the Kotlin Plugin for IDEA, you may want to search JetBrains' [YouTrack](https://youtrack.jetbrains.com/issues/KT) first to see if it is a known issue. In any case, feel free to add an issue here for it as well. We'd like to know and track what our users are experiencing regardless whether the issue is with the Gradle Kotlin DSL or with Kotlin itself.


Staying in Touch
----------------

Come chat with us in the #kotlin-dsl channel of the public [Gradle Community Slack](https://join.slack.com/t/gradle-community/shared_invite/enQtNDE3MzAwNjkxMzY0LTYwMTk0MWUwN2FiMzIzOWM3MzBjYjMxNWYzMDE1NGIwOTJkMTQ2NDEzOGM2OWIzNmU1ZTk5MjVhYjFhMTI3MmE) instance.


License
-------
Like the rest of Gradle, the _Gradle Kotlin DSL_ is released under version 2.0 of the [Apache License](LICENSE.md).


Contributing
------------

Please see [CONTRIBUTING.md](.github/CONTRIBUTING.md) for details of how to build and contribute to _Gradle Kotlin DSL_.
