This specification outlines the work that is required to have Gradle use multiple parallel compile processes while building native components.

It's a part of the overall effort of improving C/C++/native build support within Gradle.

# Native Compilation

## Use Cases

- I have a single project with a single native component and want to speed up my build with parallel source compilation.
- I have multiple projects with native components and want to speed up my build with parallel source compilation across projects.

- When --parallel is used, native compilation uses number-of-processors compile processes.
- When --parallel-threads=0 is used, native compilation uses a single compile process.
- When --parallel-threads>0 is used, native compilation uses number of compile processes requested.

# DSL

No impact to DSL at the moment.

## Story: Use multiple compile processes for native compilation

### User Visible Changes

Faster compilation times.

### Implementation Details

- ~~Create `BuildOperationProcessor` to create work queues.~~
- ~~`BuildOperationProcessor` uses `StartParameter`'s getParallelThreadCount() to determine number of worker threads to use.~~
- ~~Create `OperationQueue` to submit operations to an executor and wait for completion/collect errors.~~
- ~~Change `NativeCompiler` to use `BuildOperationProcessor` to submit `CommandLineToolInvocation`s and use `CommandLineTool` as an operation worker.~~
- ~~ToolChain's withArguments is only processed once per overall "build" step (not on individual files).~~

### Test Cases

- ~~All operations submitted to queue are processed.~~
- ~~On failure, all operations are still processed (do not fail eagerly).~~
- ~~On failure, all failures are collected into a single failure.~~
- ~~For tool chains that support it, options.txt is generated once per overall "build" step.~~
- Add concurrent tests for `BuildOperationProcessor`

## Story: Add performance tests for native languages

- Project types
  - small (20 sources, 1 function per source)
  - medium (100 sources, 20 function per source)
  - large (500 sources, 50 functions per source)
  - multi (10 projects, 10 modules each, 20 sources each, 20 functions per source)

- Experiments
  - Parallel vs serial for small, medium, large, multi
  - comparison to make?

- Performance Tests
  - 2.3 vs current for small, medium, large, multi

## Story: Report build operation failures

Show build operation that fails 
  - Show 1st 10 files that fail compilation 
  - Show count of the rest over 10

### User Visible Changes

At the end of the build, instead of just saying "see the error output for details" we will also list the first 10 files that failed compilation and the number of total files that failed compilation.

TODO: Sould this be -i/--info?
With -d/--debug, will show all build operation failures.

### Implementation Details

- ~~Provide a `Operation` type with a getDescription() that can be implemented by build `Operation`s.~~
- TBD: CommandLineTool/NativeCompiler will need to include filename as part of build operation failure.
- TBD: Currently OperationQueue collects Exceptions from individual operations, need to figure out how to put them together in a generic way.

### Test Cases

- Check output of failed build includes failed filenames for 1, 10, >10 cases (should only show max of 10).
- Check total count of failures == total number of operations that failed.
- Check that -d/--debug prints all failures.

### Discussion (Daz)

I'm not sure how we should treat the exceptions for each file. Is there any point keeping each exception? Is there ever a point in printing a stack trace for every file that fails compilation?  The only benefit I see to keeping all of the exceptions is that it's a general capability of BuildOperationProcessor.  Seems like collapsing would be specific to the type of operation? 

I guess it would be a good idea to have a special exception type, and put the logic for rendering with the logic for rendering MultipleBuildFailures. We have -s and -S, so we can do something clever, I think. Maybe collapsing the common stack trace for each cause?  

The question is whether we keep all of the exceptions or throw them away.  Chucking them is probably ok for now, but long term I think we'll want to keep them and just do better rendering.

Not sure if memory is an issue for 1000 exceptions reporting 'compilation failed'.  Maybe we can only keep the 1st N exceptions: this would mean that we could later abort after that many failures.  So instead of just displaying the first 10: we actually throw the others away early.

## Story: Improve output of build operation failures

2. Show stderr from 1st 10 files that fail compilation
3. Buffer output so that parallel doesn’t interleave, and integrate with log levels

### User Visible Changes

Stderr and Stdout from command line tools are no longer interleaved.  stderr/stdout is included as part of failure message at the end of the build.

### Implementation Details

- Start capturing stdout/stderr from CommandLineTool execute()
- At the end of the operation, print stdout/stderr.
- TBD: Attach stdout/stderr to Gradle log levels (always show stderr, hide stdout, initially?)
- TBD: Do we combine stdout/stderr into one buffer (maybe they would only make sense in order?)
- TBD: Consider making logging-level a task level configuration option?
- TBD: Limit amount of stderr shown at end?

### Test Cases

- When two command line tools are used in parallel, their output is not interleaved (one follows the other)
- When an operation fails, the stderr is printed out along with the filename (as above).
- When multiple operations fail, the final failure message includes:
    - Stderr from failing operation (up to 10)
    - Filename (up to 10)
    - Total number of operation failures

## Story: Save build operation output to "report"
4. Ability to redirect compiler output to file

### User Visible Changes

Full build log from command line tools are available in a report file.

### Implementation Details

- TBD

### Test Cases

- TBD

## Story: Extend command line tool support to understand output
5. Parse the compiler output to determine number of failures per file

### User Visible Changes

### Implementation Details

- TBD

### Test Cases

- TBD

## Story: Report progress during overall build operation
6. Progress reporting on build operations

### User Visible Changes

- TBD

### Implementation Details

- TBD

### Test Cases

- TBD

## Compiler Implementation Details

### GCC

TODO: Flesh out

[Reference]()

### Clang

TODO: Flesh out

[Reference]()

### MSVC

TODO: Flesh out

[Reference]()

# Open Questions

# Out of Scope

- Multiple native components in a single project can be only compile in parallel at the component level (depends on task-level parallelization for anything else).
