# Use cases:

Generally, we want the developer cycles to get faster, overall build performance better.

- faster java compilation where few input java source files have changed - the compiler is instructed to compile a fixed set of input source files instead of all.
- faster multi-project build: upstream project contains changed java classes. Those classes are not used by downstream projects.
    Hence, some(all) downstream projects may completely skip compilation step (or at least, compile incrementally/selectively)
- fewer output classes are changed. This way, tools like JRebel are faster as they listen to the changed output classes, the less classes changed, the faster JRebel reload is.

# Story: basic incremental compilation within a single project

Faster compilation when few input source files have changed. The selection of classes for compilation needs to be reliable.
Only relevant output classes are changed, remaining output classes are untouched.

## Implementation notes for the initial story

### User visible changes

- new java.options.incremental flag, incubating, with fat warning.
- faster builds :)

### We need basic class dependencies analyzer via byte code analysis (asm). For now it should:

- understand class dependencies
- deal with constants that are inlined by the compiler (changes to class that contains non-private constants will assume full rebuild)
- deal with annotations, especially source-level annotations that are wiped by the compiler (change to an annotation class assumes full rebuild)

### The class analysis data

- serialize to disk after compile task run, deserialize before compilation
- use simplest possible serialization mechanism

### Test Coverage

- detects deletion of a source file
    - class that is not used by any other class, the output class file is removed. No other output files are changed.
    - class is used by another class, compilation fails.
    - removes output files for inner and anonymous classes.
- detects adding of a new source file
    - output files are added. No other output files are changed.
- detects change in a source file
    - class that is not used by any other class, output class file has changed. No other output files are changed.
    - removes output files for inner and anonymous classes that no longer exist.
    - class that is used by another class, the output class files of both are changed. No other output files are changed.
- understands class dependencies, require dependents to be rebuild
    - transitive dependencies.
    - cycles in class dependencies.
    - class that is a source-only annotation is changed, all source files are recompiled.
    - class that contains a constant is changed, all source files are recompiled.
- anything on the compile classpath that does not originate from the java source will require full rebuild upon change.
    - classpath jar or directory added.
    - classpath jar or directory removed.
    - classpath jar or directory added.

# Story: basic incremental compilation across multi-project build

### Coverage

- downstream project compiles incrementally based on the changed classes of upstream project.
- downstream project completely skips compilation if upstream project changes are unrelated to the classes in current project

### Implementation ideas

- every compile task also prepares 'changed classes' info. This info is associated with a jar/project dependency.
When downstream project detects changed upstream jar it uses this info for incremental compilation.
Unreliable (jar can be configured via custom spec to include some extra content which changes are undetected). Fast.
Seems simpler and might be good enough as a starter.

- after each compilation, we store the hash of every individual file from every jar/project dependency.
This way, the incremental compilation knows what classes have changed in given project dependency.
This approach should be reliable but it may be slower. We need to unzip the jar and hash the contents.

# Story: Incremental compilation in the presence of joint compilation

Need to consider the classes implicitly available in the output directory, which are also included on the compile classpath.

# Story: Incremental compilation in the presence of compile failures

Don't switch to full compilation when previous execution failed due to compilation failures.

# Story: Performance tests for incremental compilation

Need to measure the performance of incremental vs full compilation

# Story: Don't compile a source file when the API of its compile dependencies has not changed

Currently, changing the body of a method invalidates all class files that have been compiled against the method's class. Instead, only the method's class should be recompiled.
Similarly, changing a resource file invalidates all class files that included that resource file in the compile classpath. Instead, resource files should be ignored
when compiling.

We don't necessarily need a full incremental Java compilation to improve this. For example, the Java compilation task may consider the API of the compile classpath - if it has
changed, then compile all source files, and if it has not, skip the task (assuming everything else is up to date). This means that a change to a method body does not propagate
through the dependency graph.
