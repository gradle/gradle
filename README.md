<img src="gradle.png" width="350px" alt="Gradle Logo" />

Gradle is a build tool with a focus on build automation and support for multi-language development. If you are building, testing, publishing, and deploying software on any platform, Gradle offers a flexible model that can support the entire development lifecycle from compiling and packaging code to publishing web sites. Gradle has been designed to support build automation across multiple languages and platforms including Java, Scala, Android, C/C++, and Groovy, and is closely integrated with development tools and continuous integration servers including Eclipse, IntelliJ, and Jenkins.

For more information about Gradle, please visit: https://gradle.org

## Downloading

You can download released versions and nightly build artifacts from: https://gradle.org/downloads

### Installing from source

To create an install from the source tree you can run either of the following:

    ./gradlew install -Pgradle_installPath=/usr/local/gradle-source-build

This will create a minimal installation; just what's needed to run Gradle (i.e. no docs).

You can then build a Gradle based project with this installation:

    /usr/local/gradle-source-build/bin/gradle «some task»

To create a full installation (includes docs):

    ./gradlew installAll -Pgradle_installPath=/usr/local/gradle-source-build

## Contributing

If you're looking to contribute to Gradle or provide a patch/pull request, you can find more info [here](https://github.com/gradle/gradle/blob/master/.github/CONTRIBUTING.md).

## Code of Conduct

In the interest of fostering an open and welcoming environment, we as contributors and maintainers pledge to making participation in our project and our community a harassment-free experience for everyone, regardless of age, body size, disability, ethnicity, gender identity and expression, level of experience, nationality, personal appearance, race, religion, or sexual identity and orientation.

You can read the Gradle Code of Conduct at https://gradle.org/conduct/ and contribute to it at https://github.com/gradle/code-of-conduct/.

This code of conduct applies to all of the projects under the Gradle organization on GitHub and the Gradle community at large (Slack, mailing lists, Twitter, etc.).
