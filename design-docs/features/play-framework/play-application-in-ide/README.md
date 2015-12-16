# Build author imports and develops Play application in IDE

- [ ] Gradle project with a single Software Model component can be imported into the IDE
- [ ] Single-component project imported into IDE has component sources configured
- [ ] Play application project imported into IDE has appropriate sources configured for module
- [ ] Play application project imported into IDE has correct external dependencies configured
- [ ] Play application project imported into IDE has correct local component dependencies configured for multiproject build
- [ ] Play application project imported into IDE has correct Java source and target JVM configured
- [ ] Play application project imported into IDE has correct Scala language settings configured

## Stories

### Story: Gradle project with a single Software Model component can be imported into the IDE

When a Gradle project that defines a single Software Model component (and no other components), a build user must be able to import this project into Eclipse or Idea via the built-in Gradle import support in the IDE.

In IntelliJ IDEA, a Software Model component will be represented by an `IdeaModule`, meaning that the single-component project can be opened as a new Idea project, or can be imported into an existing project as a new Idea module.

In Eclipse, a Software Model component will be represented by an `EclipseProject`, meaning that the single-component project can be imported into a new Eclipse workspace or an existing Eclipse workspace.

##### Tooling API behaviour

This story makes use of a simple tooling client that demonstrates the output of the Tooling API, which will be consumed by the different IDEs.

    $ gradle-dev -u

    IDEA project: play-app
    IDEA module: play-app

    Eclipse workspace:
    Eclipse project: play-app

##### Out of scope

- No sources or dependencies will be configured for the imported project. This will be handled in later stories.

### Story: Single-component project imported into IDE has component sources configured

When a Gradle project with a single Software Model component is imported into the IDE, the appropriate source directories should be configured to permit the IDE to index, build and cross-reference the sources.

For a generic component, this means that all _component_ sources will be configured as source sets. These directories are configured:
- For Idea: on the primary `IdeaContentRoot` for the `IdeaModule` created for the imported Play application
- For Eclipse: on the `EclipseProject` created for the imported Play application

##### Tooling API behaviour

This story makes use of a simple tooling client that demonstrates the output of the Tooling API, which will be consumed by the different IDEs.

    $ gradle-dev -u

    IDEA project: play-app
    IDEA module: play-app
        Content root: ./play-app
            source: app
            source: conf
            test: test

    Eclipse workspace:
    Eclipse project: play-app
        source: app
        source: conf
        source: test

##### Out of scope

- Special treatment of `JvmResourceSet` as a _resource_ directory in Idea 15.
- Sources configured for one or more `BinarySpec` members of `component.binaries`

### Story: Play application project imported into IDE has appropriate sources configured for module

When a Play application is imported into the IDE, the appropriate source directories should be configured to permit the IDE to index, build and cross-reference the sources.

For a play application:
- The `app` and `conf` directories should be added as production source directories
- The generated scala sources for each `TwirlSourceSet` and `RoutesSourceSet` should be added as _generated_ production source sets
- The generated javascript sources for each `CoffeeScriptSourceSet` should be added as production source directories
- The `test` directory should be added as a test source directory

These directories are configured:
- For Idea: on the primary `IdeaContentRoot` for the `IdeaModule` created for the imported Play application
- For Eclipse: on the `EclipseProject` created for the imported Play application

##### Tooling API behaviour

This story makes use of a simple tooling client that demonstrates the output of the Tooling API, which will be consumed by the different IDEs.

    $ gradle-dev -u

    IDEA project: play-app
    IDEA module: play-app
        Content root: ./play-app
            source: app
            source: conf
            source: build/src/play/binary/routesScalaSources (generated)
            source: build/src/play/binary/twirlTemplatesScalaSources (generated)
            source: build/playBinary/src/compilePlayBinaryCoffeeScript (generated)
            test: test

    Eclipse workspace:
    Eclipse project: play-app
        source: app
        source: conf
        source: build/src/play/binary/routesScalaSources
        source: build/src/play/binary/twirlTemplatesScalaSources
        source: build/playBinary/src/compilePlayBinaryCoffeeScript
        source: test

##### Open issues

- Should all asset directories be added as production source directories?
- Should we add generated sources to the eclipse project?

##### Out of scope

- Special treatment of `conf` as a _resource_ directory in Idea 15.

### Story: Play application project imported into IDE has correct external dependencies configured

When a Play application is imported into the IDE, the appropriate external dependencies should be configured for the test and productions sources.

- The external dependencies in the 'playPlatform' and 'play' configurations end up in `EclipseProject.classpath` and `IdeaModule.dependencies` (with the appropriate scope)
- The external dependencies in the 'playTest' configuration end up in `EclipseProject.classpath` (with the appropriate scope)

### Story: Play application project imported into IDE has correct local component dependencies configured for multiproject build

When a multiproject Gradle build containing Play application projects is imported into the IDE, the appropriate local dependencies should be configured for the test and productions sources.

- The project dependencies in the 'playPlatform' and 'play' configurations should end up in `EclipseProject.classpath` and `IdeaModule.dependencies` (with the appropriate scope)
- The project dependencies in the 'playTest' configuration end up in `EclipseProject.classpath` (with the appropriate scope)

### Story: Play application project imported into IDE has correct Java source and target JVM configured

### Story: Play application project imported into IDE has correct Scala language settings configured

