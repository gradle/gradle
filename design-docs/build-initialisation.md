
This feature covers the initial setup of a Gradle build, either from an existing source tree or from scratch. The result of using
this feature is a Gradle build that is usable in some form. In some cases, the build will be fully functional, and in other
cases, the build will require some additional manual work to be completed before it is ready to be used.

When used with an existing build, this feature is intended to integrate closely with the build comparison feature, so that the
initial build is created using the build initialization feature, and then the build comparison feature can be used to drive
manual changes to the Gradle build, and to inform when the Gradle build is ready to replace the existing build.

# Use cases

1. I have a multi-module Maven build that I want to migrate to Gradle.
2. I have an Ant based build that I want to migrate to Gradle.
3. I have an Ant + Ivy based build that I want to migrate to Gradle.
4. I have a Make based build that I want to migrate to Gradle.
5. I have an Eclipse based build that I want to migrate to Gradle.
6. I have an IDEA based build that I want to migrate to Gradle.
7. I want to create a Java library project from scratch.
8. I want to create a {Groovy, Scala, C++, Javascript, Android} library from scratch.
9. I want to create a {Java, native, Android, web} application from scratch.

# User visible changes

* A new plugin, 'bootstrap'
* The plugin adds task 'bootstrapGradleProject'
* The tasks generates build.gradle / settings.gradle files.

# Migrating from Maven to Gradle

When the pom.xml packaging is 'pom':
* Generate a multi-project build, with a separate Gradle project for each Maven module referenced in the pom.xml, and a root project for the parent module.
* Generate a build.gradle for each Maven module based on the contents of the pom.xml.

For all other packagings:
* Generate a single-project build.

For all builds:
* Generate a settings.gradle
* Generate the wrapper files.
* Preconfigure the build comparison plugin, as appropriate.
* Inform the user about the build comparison plugin.

## Sad day cases

* maven project does not build
* bad pom.xml
* missing pom.xml

## Integration test coverage

* convert a multi-module maven project and run gradle build with generated Gradle scripts
* convert a single-module maven project ...
* include a sad day case(s)

## Implementation approach

* Add some basic unit and integration test coverage.
* Use the maven libraries to determine the effective pom in process, rather than forking 'mvn'.
* Reuse the import and maven->gradle mapping that the importer uses.
 We cannot have the converter using one mapping and the importer using a different mapping.
 Plus this means the converter can make use of any type of import (see below).

## Other potential stories

* Better handle the case where there's already some Gradle build scripts (i.e. don't overwrite an existing Gradle build).
* Add support for auto-applying a plugin, so that I can run 'gradle convertPom' in a directory that contains a pom.xml and no Gradle stuff.

# Migrating from Ant to Gradle

* Infer the project model from the contents of the source tree (see below).
* Generate a build.gradle that applies the appropriate plugin for the project type. It does not import the build.xml.
* Generate a settings.gradle
* Generate the wrapper files.
* Preconfigure the build comparison plugin, as appropriate.
* Inform the user about the build comparison plugin.

# Migrating from Ant+Ivy to Gradle

* Infer the project model from the contents of the source tree.
* Generate a build.gradle that applies the appropriate plugin for the project type.
* Convert the ivysettings.xml and ivy.xml to build.gradle DSL.
* Generate a settings.gradle
* Generate the wrapper files.
* Preconfigure the build comparison plugin, as appropriate.
* Inform the user about the build comparison plugin.

# Migrating from Make to Gradle

As for the Ant to Gradle case.

# Migrating from Eclipse to Gradle

* Infer the project layout, type and dependencies from the Eclipse project files.
* Generate a multi-project build, with a Gradle project per Eclipse project.
* Generate a settings.gradle
* Generate the wrapper files.
* Preconfigure the build comparison plugin, as appropriate.
* Inform the user about the build comparison plugin.

# Migrating from IDEA to Gradle

As for the Eclipse to Gradle case.

# Create a Java library project from scratch

* Generate a build.gradle that applies the java plugin, adds mavenCentral() and the dependencies to allow testing with JUnit.
* Generate a settings.gradle
* Generate the wrapper files.
* Create the appropriate source directories.
* Possibly add a class and a unit test.

# Create a library project from scratch

TBD

# Create an application project from scratch

TBD

# Inferring the project model

This can start off pretty basic: if there is a source file with extension `.java`, then the Java plugin is required, if there is a
source file with extension `.groovy`, then the Groovy plugin is required, and so on.

The inference can evolve over time:
* if the source file path contains an element called 'test', then assume it is part of the test source set.
* parse the source to extract package declarations, and infer the source directory roots from this.
* parse the source import statements and infer the test frameworks involved.
* parse the source import statements and infer the project dependencies.
* infer that the project is a web app from the presence of a web.xml.

The result of the inference can potentially be presented to the user to confirm (or they can just edit the generated build file), and
when nothing can be inferred, the user can select from a list or assemble the model interactively.

# Open issues
