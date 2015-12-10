# Build author specifies Play, Scala and Java platform for Play application

 - [x] Developer builds, runs and tests a basic application for a specified version of Play
 - [x] Compilation and building is incremental with respect to changes in Play platform
 - [x] Build author specifies target Play and Scala platform for Play application
 - [x] Build author specifies target Java platform for Play application

#### Play 2.4 support
- [ ] Add basic support for Play 2.4.x
- [ ] Add support for configuring Play 2.4.x routes compiler type (injected/static)
- [ ] Add support for configuring Play 2.4.x aggregate reverse routes

## Stories

### Story: Developer builds, runs and tests a basic application for a specified version of Play

- Can build play application with Play 2.2.3 on Scala 2.10, and Play 2.3.7 on Scala 2.11

#### Test cases

- Verify building and running the 'activator new' app with Play 2.2.3 and Play 2.3.7

#### Open issues

- Compile Scala wrappers around different Play versions, and load these via reflection


### Story: Compilation and building is incremental with respect to changes in Play platform

### Story: Build author declares target Play and Scala platform for Play application

```gradle
model {
    components {
        play(PlayApplicationSpec) {
            platform play: "2.3.7", scala: "2.11"
        }
    }
}
```

- If not specified, Play major version number implies Scala version
    - Play 2.2.x -> Scala 2.10
    - Play 2.3.x -> Scala 2.11
- Java version is taken from version executing Gradle

#### Test cases

- For each supported Play version: 2.2.3, 2.3.7
    - Can assemble Play application
    - Can run Play application
    - Can test Play application
- Can build & test multiple Play application variants in single build invocation
    - `gradle assemble` builds all variants
    - `gradle test` tests all variants
- Play version should be visible in components report and dependencies reports.
- Most Play integration tests should be able to run against different supported platforms
    - Developer build should run against single version by default
    - CI should run against all supported versions (using '-PtestAllPlatforms=true')

### Story: Build author declares target Java platform for Play application

```gradle
model {
    components {
        play(PlayApplicationSpec) {
            platform play: "2.3.7", scala: "2.11", java: "1.8"
        }
    }
}
```

## Play 2.4.x support

### Story: Add basic support for Play 2.4.x

The Gradle Play support has a structure for supporting multiple Play major versions. Currently 2.2.x and 2.3.x are supported.
Support for 2.4.x can be done with the same structure.
- Add PlayMajorVersion enum for 2.4.x
- Add 2.4.x specific adaptors for
 - routes compiler
 - twirl compiler
 - play run (starting development server)

Test coverage:
- Add Play 2.4.0 to the target coverage of PlayMultiVersionIntegrationTest so that the same level of test coverage is applied to test Play 2.4.0

### Story: Add support for configuring Play 2.4.x routes compiler type (injected/static)

Play 2.4 introduces a new configuration option `routesGenerator` for the route compiler.
```
// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator
```
There are currently two different types of routesGenerators: `play.routes.compiler.InjectedRoutesGenerator` and `play.routes.compiler.StaticRoutesGenerator` .
StaticRoutesGenerator creates the legacy style access to "object" type Scala controllers. InjectedRoutesGenerator expects controllers to be "class" type in Scala.
In Play Java, the legacy style uses static methods and the newer injected style uses normal non-static methods.
Play uses Guice to do autowiring in the new style.

Test coverage:
- add Play 2.4.x integration test application that uses injected routes compiler and non-static style of controllers in Scala and Java.

### Story: Add support for configuring Play 2.4.x aggregate reverse routes

In previous versions of Play, the only way for a project to use the reverse routes of another project in a multiproject build is to create a
compile dependency on the other project.  This is inconvenient when two different projects want to both share each others routes as it creates
a circular dependency.  Play 2.4 introduces a new configuration option `aggregateReverseRoutes` which allows the reverse routes of projects to
be exposed to other projects via a common shared dependency project.  Basically, each project depends on the shared project and only generates
forward routes while all of the reverse routes come from the shared dependency.

 - Play manual: [Aggregating Reverse routers](https://github.com/playframework/playframework/blob/2.4.x/documentation/manual/detailedTopics/build/AggregatingReverseRouters.md)
 - [Routes compilation sbt task in 2.3.x](https://github.com/playframework/playframework/blob/2.3.x/framework/src/sbt-plugin/src/main/scala/PlaySourceGenerators.scala#L20)
 - [Routes compilation sbt task in 2.4.x](https://github.com/playframework/playframework/blob/2.4.x/framework/src/sbt-plugin/src/main/scala/play/sbt/routes/RoutesCompiler.scala#L48)

#### Implementation

Possible implementation:
- Add an `aggregateReverseRoutes` setting to `PlayApplicationBinarySpec` that accepts a list of projects.
- Add a `ReverseRoutes` buildable model element to `PlayApplicationBinarySpecInternal` with an `aggregated` setting and a list of generated source dirs.
- Split the generation of forward routes and reverse routes into two RoutesCompile tasks.
- When a project is added to the aggregateReverseRoutes of another project, a binary rule is created that sets `ReverseRoutes.aggregated` to true and its generated source dirs are then added to the ReverseRoutes of the aggregate project.
- When the scalaCompilePlayBinary task is configured, include ReverseRoutes generated source dirs only if aggregate is false.
