# Build author imports and develops Play application in IDE

- [ ] Gradle project with a single Software Model component can be imported into the IDE
- [ ] Single-component project imported into IDE has component sources configured
- [ ] Play application project imported into IDE has appropriate sources configured for module
- [ ] Play application project imported into IDE has correct external dependencies configured
- [ ] Play application project imported into IDE has correct local component dependencies configured for multiproject build
- [ ] Play application project imported into IDE has correct Java source and target JVM configured
- [ ] Play application project imported into IDE has correct Scala language settings configured
- [ ] Gradle project with a multiple Software Model components can be imported into the IDE

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

##### Implementation notes

- IDEA content root and Eclipse project directory should be the project directory of the Gradle project containing the component
- Module/project should have `null` Java source settings.
- Eclipse natures and builders should be empty.
- Classpaths/dependencies should be empty.
- Manually test import into Eclipse and IDEA at this point

##### Out of scope

- No sources or dependencies will be configured for the imported project. This will be handled in later stories.
- The behaviour of importing a project with multiple Software Model components remains undefined.

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

##### Implementation notes

- Source directories should include only those source directories that contain JVM source files or resources, as this is
what 'source directory' means to both Eclipse and IDEA:
    - Import Jvm library: should get Java and resources dirs
    - Import native library: should get content root but no source dirs
- Module/project should have non-null Java source settings when there are source directories.
- Manually test import into Eclipse and IDEA

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

##### Implementation notes

- Introduce an (internal) abstraction that says:
    - here are my production source sets
    - here are my generated production source sets
    - here are my test source sets
    - here are my generated test source sets
- Play application component implements this, or play plugin provides an implementation of this in some form
- IDE import uses this abstraction instead of anything Play specific
- IDE import must know _nothing_ of Play. It should understand JVM components only.
- Manually test import into Eclipse and IDEA

##### Open issues

- Should all asset directories be added as production source directories? yes, if they end up as classpath resources without transformation, otherwise no
- Should we add generated sources to the eclipse project? yes, otherwise it can't compile stuff

##### Out of scope

- Special treatment of `conf` as a _resource_ directory in Idea 15.

### Story: Play application project imported into IDE has correct external dependencies configured

When a Play application is imported into the IDE, the appropriate external dependencies should be configured for the test and productions sources.

- The external dependencies in the 'playPlatform' and 'play' configurations end up in `EclipseProject.classpath` and `IdeaModule.dependencies` (with the appropriate scope)
- The external dependencies in the 'playTest' configuration end up in `EclipseProject.classpath` (with the appropriate scope)

##### Implementation notes

- Introduce an (internal) abstraction that says:
    - Here are my compile time dependencies
    - Here are my runtime dependencies
    - Here are my test compile dependencies
    - Here are my test runtime dependencies
    - Here are my dependencies merged into a single classpath
- Here 'dependency' means something like:
    - Local (this is for the following story)
        - The identity of the target _component_
    - External
        - A human consumable display name for the library
        - The GAV of the library, if known.
        - The location of the jar, if any.
        - The location of the source zip, if any.
        - The location of the Javadoc zip, if any.
- Play application component implements this, or play plugin provides an implementation in some form
- IDE import uses this abstraction instead of anything Play specific
- Manually test import into Eclipse and IDEA

##### Open questions

- Resolution failures?

### Story: Play application project imported into IDE has correct local component dependencies configured for multiproject build

When a multiproject Gradle build containing Play application projects is imported into the IDE, the appropriate local dependencies should be configured for the test and productions sources.

- The project dependencies in the 'playPlatform' and 'play' configurations should end up in `EclipseProject.classpath` and `IdeaModule.dependencies` (with the appropriate scope)
- The project dependencies in the 'playTest' configuration end up in `EclipseProject.classpath` (with the appropriate scope)

##### Open questions

- Local dependencies can be both play libraries and general JVM libraries. Which are supported for this story?

### Story: Play application project imported into IDE has correct Java source and target JVM configured

##### Implementation plan

- Add an abstraction to expose these. Language level should live on source set.

### Story: Play application project imported into IDE has correct Scala language settings configured

##### Implementation plan

- Add an abstraction to expose these. Language level should live on source set.
