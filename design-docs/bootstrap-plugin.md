A plugin that allows to 'initiate' a fully working Gradle project
from an external model, for example from a maven project.

# Use cases

1. Given a multi-module maven project I want to generate a Gradle build scripts for it
so that I migrate to Gradle!
2. There are other models we can bootstrap from, but currently we are focusing on converting from maven.
3. I want to bootstrap a simple Gradle project that contains wrapper, java pluging appied, mavenCentral(), junit.

# User visible changes

* A new plugin, 'bootstrap'
* The plugin adds task 'bootstrapGradleProject'
* The tasks generates build.gradle / settings.gradle files.

## Sad day cases

* maven project does not build
* bad pom.xml
* missing pom.xml

# Integration test coverage

* convert a multi-module maven project and run gradle build with generated Gradle scripts
* convert a single-module maven project ...
* include a sad day case(s)

# Implementation approach

* Add some basic unit and integration test coverage.
* Use the maven libraries to determine the effective pom in process, rather than forking 'mvn'.
* Reuse the import and maven->gradle mapping that the importer uses.
 We cannot have the converter using one mapping and the importer using a different mapping.
 Plus this means the converter can make use of any type of import (see below).

# Other potential stories

* Better handle the case where there's already some Gradle build scripts (i.e. don't overwrite an existing Gradle build).
* Add support for auto-applying a plugin, so that I can run 'gradle convertPom' in a directory that contains a pom.xml and no Gradle stuff.

# Open issues

-
