# Java Annotation Processing

This spec aims at making Java annotation processing a first-class citizen in Gradle.

## Use cases

 * Simplify configuration for annotation processor dependencies used as `-processorpath` of the Java compiler  
   Compile-time-only dependencies (e.g. containing annotations that are only needed at compile-time to be processed by annotation processors (e.g. Immutables.org or Google's "auto" projects), or possibly for documentation purpose only (e.g. `@Nullable`, `@NotNull` et al.)) can already be configured through the `compileOnly` and `testCompileOnly` configurations in the Java Plugin
 * Configure annotation processing in IDEs through the IDE-specific Gradle plugins (`./gradlew eclipse` or `./gradlew idea`)
 * Expose the configuration through the Tooling API for IDEs to configure themselves (Buildship, etc.)

## Implementation plan

### Story - Add properties to `CompileOptions`

Add a `processorpath` (similar to the existing `sourcepath`), `processors`, `processorArgs`, and `proc`:

 * `processorpath` as a `FileCollection`: when non-empty, it is passed as a `-processorpath` argument to the Java compiler.
 * `processors` as a `List<String>` (or `Set<String>` or `Collection<String>`?); when non-empty, it is passed as a `-processor` argument to the Java compiler (values joined with a `,`).
 * `processorArgs` as a `Map<String, ?>` (or `Map<String, String>`?); each entry is passed as a `-Akey=value` argument to the Java compiler.
 * `proc` as an enum with values `none` and `only`, defaults to `null`; when non-`null`, it is passed a `-proc` argument to the Java compiler (`-proc:none` or `-proc:only`).

#### Backwards compatibility

 * The new arguments must be added before the `compilerArgs` so that user-specified arguments in `compilerArgs` override the ones added by those new properties (similar to how `compilerArgs` can override `sourcepath` added in Gradle 2.4, or any other compiler argument
 * An empty `processorpath` must not result in a `-processorpath` argument so that the default behavior still is to lookup annotation processors in the classpath.

#### Test cases

 * The default behavior doesn't change (no change in existing tests, e.g. in `JavaCompilerArgumentsBuilderTest` and `CommandLineJavaCompilerArgumentsGeneratorTest`)
 * a non-empty `processorpath` adds a `-processorpath` argument with `processorpath.asPath` as value
 * a non-empty `processors` list adds a `-processor` argument with the `processors` joined with a `,` as value
 * each entry in `processorArgs` is added as `-Akey=value` arguments
 * `proc` with a value of `none` is added as a `-proc:none` argument
 * `proc` with a value of `only` is added as a `-proc:only` argument
 * Add integration test with an annotation processor in the `compileOnly` configuration and an empty `compileJava.processorpath`, and verify that it ran (checks non-regression)
 * Add integration test with an annotation processor in the `compileJava.options.processorpath` and another in the `compileOnly` configuration, and verify that the former ran but the latter didn't
 * TODO: more integration tests

### Story - Add processor path to `SourceSet`

Add a `processorpath` property to `SourceSet`, like the existing `compileClasspath`.

### Story - Update the Java plugin to wire the above two `processorpath`s

Configure the `compileJava` task's `options.processorpath` to the `main` source set's `processorpath`, and similarly for the `compileTestJava` task and `test` source set.

#### Test cases

Set the source set's `processorpath` and check that it's reflected in the corresponding task's `options.processorpath`.

### Story - Create default/conventional dependency configurations

For each source set (`main` and `test`), create an `apt` (resp. `testApt`) configuration and wire it as the source set's `processorpath`.

![Java Plugin Configurations](img/annotation_processing_javaPluginConfigurations.png)

## Open for discussion

 * Should the `testApt` configuration extends from the `apt` one?
 * Should there be a new task to call annotation processors on already-compiled classes (passing the class names to the Java compiler in lieu of source file names), possibly coming from dependencies.

