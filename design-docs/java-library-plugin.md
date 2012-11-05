Declare the project to be a java library.

# Use cases

For a Java library, Gradle will build and publish the JAR, API documentation and source JAR for the project.
For a command-line application, Gradle will build a distribution ZIP or TAR containing
automatically generated wrapper scripts, runtime dependencies and documentation.
From the same meta-data, it will be able to build a .exe or OS X application bundle for the application.
Or provide a task to run the application from Gradle. Or generate a native installer.

More info in the [forum ticket](http://forums.gradle.org/gradle/topics/modeling_the_java_application_development_domain).

# User visible changes

    - 'java-library' plugin
        - applies 'java' plugin
        - adds 'distZip' task
            - packages up the jar and runtime dependencies of the library as a Zip.
            - include contents of src/dist
        - adds 'distribution' extension
            - has 'name' property that is used to configure the 'baseName' of the distZip task.

## DSL:

    apply plugin: 'java-library'
    //applies 'java' plugin

    distribution {
      name = 'someName'
    }

    distZip {  //type: Zip
      from { ... }
    }

## Sad day cases

- works if src/dist does not exist
- works if only 'java-library' is specified and nothing else (e.g. sensible defaults are used for the distro name, etc.)
- does not crash if distribution.name is configured to null

# Integration test coverage

- add an integ test that runs task to build up a distro that:
    - has some stuff in src/dist
    - includes some runtime dependency that is declared
    - uses the name of the distribution.name for the distro name

# Implementation approach

## Unit testing

- add unit tests for the plugin, validate all the features (separate tests) declared in 'user visible changes' section.

## Documentation

- add new plugin chapter and hook it up to the user guide
- add the xml for the new extension object
- new extension object should have a javadoc with small code sample (using our 'autoTested').
    - say, apply plugin and configure the distribution.name
- link the new extension from plugins.xml

# Next steps

Only after above is completed & integrated with the master we want to design and implement the following:

- Generate a Tar distribution as well.
- Share configuration and tasks with the application plugin, by extracting a common 'dist' plugin out of the application plugin.
- (Very advanced) Allow the distributions to be published instead of the jar.
    This would need to mess with the generated pom.xml/ivy.xml to remove the dependency declarations
    for those dependencies that have been bundled in the distribution.