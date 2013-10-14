
## Allow the configuration of publications to be deferred (DONE)

This story allows the lazy configuration of publications, so that the configuration code is deferred until after all the
other things it uses as input have themselves been configured. It will introduce a public mechanism for plugins to use to
implement this pattern.

The `publishing { }` closure will become lazy, so that it is deferred until after the project has been configured.
Accessing the `publishing` extension directly in the script or programmatically will trigger the configuration of
the publishing extension.

The publishing tasks will continue to be created and configured as the publications are defined. Later stories will
allow deferred creation.

An example:

    apply plugin: 'maven-publish'

    publishing {
        // this closure is lazy and is not executed until after this build script has been executed
        repositories {
        }
        publications {
        }
    }

    // The following are not lazy
    publishing.repositories {
        maven { ... }
    }
    publishing.repositories.maven { ... }
    publishing.repositories.mavenLocal()
    publishing.repositories.myRepo.rootUrl = 'http://somehost/'
    def extension = project.extensions.getByType(PublishingExtension)

    // This will not work as the tasks have not been defined
    generatePomFileForMavenPublication {
    }
    tasks.generatePomFileForMavenPublication {
    }

For a multiproject build:

    // root build.gradle

    subprojects {
        apply plugin: 'java'
        apply plugin: 'maven-publish'
        publishing {
            repositories.maven { ... }
            publications {
                maven(MavenPublication) {
                    from components.java
                }
            }
        }
    }

    // project build.gradle

    dependencies { ... }

Once the publishing extension has been configured, it will be an error to make further calls to `publishing { ... }`.

### Implementation plan

1. Add a `DeferredConfigurable` annotation. This annotation marks an extension as requiring a single, deferred configuration event, rather than progressive configuration.
2. Update the ExtensionContainer so that for any added extension that is annotated with DeferredConfigurable:
    - `configure` will add an action for later execution when the target extension object is to be configured.
       It is an error to attempt to configure a DeferredConfigurable extension after it has been configured.
    - Accessing the extension triggers the execution of the registered configuration actions, if the extension has not already been configured.
3. Add `DeferredConfigurable` to `DefaultPublishingExtension`.

### Test coverage

- Update the publishing integration tests so that the publications are declared along with the other injected configuration in `allprojects`/`subprojects`
- A custom plugin can use a `DeferredConfigurable` extension to implement lazy configuration. Verify that that extension is configured before the
  project's afterEvaluate {} event is fired.
- Attempting to configure a `DeferredConfigurable` extension after access provides reasonable failure message.
- A reasonable error message is given when the configuration of an extension fails.
- A reasonable error message is given when attempting to access an extension whose configuration has previously failed.
