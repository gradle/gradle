# Java Annotation Processing

This spec aims to improve Java annotation processing in Gradle. Some stories might not live in Gradle core but describe functionality that might can live better in 3party plugins.

## Use cases

 * Simplify configuration for annotation processor dependencies used as `-processorpath` of the Java compiler  
   Compile-time-only dependencies (e.g. containing annotations that are only needed at compile-time to be processed by annotation processors (e.g. Immutables.org or Google's "auto" projects), or possibly for documentation purpose only (e.g. `@Nullable`, `@NotNull` et al.)) can already be configured through the `compileOnly` and `testCompileOnly` configurations in the Java Plugin
 * Configure annotation processing in IDEs through the IDE-specific Gradle plugins (`./gradlew eclipse` or `./gradlew idea`)
 * Expose the configuration through the Tooling API for IDEs to configure themselves (Buildship, etc.)

#### The API

```
public enum AnnotationProcessingOption {
    PROC,
    NONE;
}

public class CompileOptions extends AbstractOptions {
    ...

    @Input
    @Optional
    public AnnotationProcessingOption getProc() { ... }

    public void setProc(AnnotationProcessingOption proc) { ... }

    @InputFiles
    @Optional
    public FileCollection getProcessorpath() { ... }

    public void setProcessorpath(FileCollection processorpath) { ... }

    @Input
    @Optional
    public List<String> getProcessors() { ... }

    public void setProcessors(List<String> processors) { ... }

    @Input
    @Optional
    public Map<String, ?> getProcessorArgs() { ... }

    public void setProcessorArgs(Map<String, ?> processorArgs) { ... }
}
```

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

### Story - Add processor path to `SourceSet` (TBD if this should live in gradle core)

- Add a `processorpath` property to `SourceSet`, like the existing `compileClasspath`.
- Update java base plugin to wire sourcesets processorpath with the according compiler classpath.

#### Test cases

- in a java project configure `sourceSets.main.processorpath` and check that it's reflected in the compiled main classes.
- use a custom sourceSet with custom `processorpath`.

### Story - Create default/conventional dependency configurations (TBD if this should live in gradle core)

For each source set (`main` and `test`), create an `apt` (resp. `testApt`) configuration and wire it as the source set's `processorpath`.

![Java Plugin Configurations](img/annotation_processing_javaPluginConfigurations.png)

### Story - Automatically configure IDEs through their Gradle plugins (TBD if this should live in gradle core)

For Eclipse, if any of `compileJava.options.proc` or `compileTestJava.options.proc` is `null`, create a `.factorypath` file:

 * if `compileJava.options.proc` is `null`:
   * if `compileJava.options.processorpath` is not empty, include its entries,
   * otherwise include the entries of the `compileOnly` configuration.
 * if `compileTestJava.options.proc` is `null`:
   * if `compileTestJava.options.processorpath` is not empty, include its entries,
   * otherwise include the entries of the `testCompileOnly` configuration.

This means that, for Eclipse, annotation processors are applied to all sources, whether they've been configure for main or test sources only.
This is a limitation of Eclipse's project model.

TODO: IntelliJ IDEA (things need to be configure both at the project and module level, can be pretty hairy to get "right")

### Story - Expose the configuration through the Tooling API (TBD if this should live in gradle core)

Expose the `apt` and `testApt` configurations through the Tooling API such that Buildship and IntelliJ IDEA (and others) integrations can make use of them to automatically configure the projects.

## Open for discussion

 * Should the `testApt` configuration extends from the `apt` one?
 * Should there be a new task to call annotation processors on already-compiled classes (passing the class names to the Java compiler in lieu of source file names), possibly coming from dependencies.
 * Should there be new properties to the `eclipse` and `idea` plugins to enable/disable annotation processing in the IDE irrespective of the `JavaCompile` tasks? Sometimes you want it in Gradle but not in IDEs (Eclipse is known to be buggy, and not flexible enough)
