# Improved Console Output

## Primary Goals
* Improve perceived performance by
   * Making user aware of all work in progress
   * Providing more granular feedback

### User Goals for Console
The user wants to answer the following questions:

* When will my build finish?
    * Soon (should I keep watching it) or later ("I'll check back")
* Is there anything I should know about my build right now? (e.g. tests have failed, warnings)
* What is Gradle doing right now?
* Are there other interesting outcomes?
    * How much work was done/skipped (up-to-date, from-cache etc.)?
    * Where is my build scan?

## Design Concerns
The main criteria we need to consider when choosing the best solution to the problem.

#### DC1 - Engaging interactive output**
Output from the CLI on an interactive terminal must clearly show the work in-progress while showing activity while waiting as well (ascii spinner, output messages, etc). It should be easy to tell which thing Gradle is waiting on. Consider how parallel work should be represented. Consider how failures are presented, including the case of --continue. Consider the use of System.in and Console to read input.

#### DC2 - Logging consumed through Tooling API/GUI/CI
Consider the case where rich text is not available, but there are other means to provide rich output. How would IntelliJ, a web page, or some other GUI behave/display given output.
 
#### DC3 - Non-interactive output
Consider how console output should behave when an interactive terminal is not available. For example, when output is piped to some other process or a log file. 

#### DC4 - Extensible for future features of Gradle
Consider how build scans, task output cache, and other yet undreamt features are represented. 

#### DC5 - Performance
All of the above should be achieved with nominal performance impact.

#### DC6 - Backwards Compatibility
Consider tools that parse the current output for their own highlighting. Provide a fallback to do no harm in order to avoid blocking tool providers upgrading. Consider different consoles and OSes. `System.in`, `--continuous`

Consider: 
 - Different terminals
 - Windows, Linux, macOS
 - `System.in`
 - `TERM=dumb`
 - Fonts with limited provided characters outside ASCII

# Milestone 1 - Show parallel WIP and summary

## Story: Display parallel work in-progress independently by operation
Show incomplete `BuildOperations` on separate lines, up to a specified maximum number. 

### User Visible Changes
The mainArea is displayed at the top for rendering important messages like warnings
and errors.

In the case where a build process is not attached to a terminal, `OutputEvent`s are
handled the same as previous versions of Gradle in that they are streamed to
stdout and stderr with no filtering or throttling.
 
The `ProgressLabel` is intended to give the user a glance-able indicator that tells
them whether the build will be finished soon or not. 
See ["Display build progress as a progress bar through colorized output"](#story-display-build-progress-as-a-progress-bar-through-colorized-output) below.

The multiple progressArea lines are rendered results of a fixed list of `AbstractWorker`s. 
It displays work-in-progress for each `AbstractWorker`. `BuildOperationDetails` is built
with an optional worker ID to provide information about "where" an operation is running
so that a mapping of worker to index of operation status line can be maintained. The 
default maximum number of workers to display is `org.gradle.workers.max` or 8, whichever
is lower.

A `OperationStatusBarFormatter` can be used to customize the intra-line display of each 
`ProgressOperation` chain (each `ProgressOperation` has a reference to its parent)

stdout and stderr are routed to QUIET logger same as today, `GradleLogger.quiet` too.
`GradleLogger.lifecycle` will also be logged the same as today, in the main area.

### Implementation
A `ParallelWorkReportingConsole` extends `Console` with the following operations: 
 - `TextArea getMainArea()` (inherited from `Console`)
 - `ProgressLabel getStatusBar()` (inherited from `Console`)
 - `MultiStatusTextArea getProgressArea()`
 
An `StatusBarFormatter` has operations:
 - `String format(ProgressOperation operation)`

`MultiStatusTextArea` extends `TextArea` with the following operations:
 - `void update(ProgressOperations operations)`
 - `void setStatusBarFormatter(StatusBarFormatter formatter)` 

**NOTE:** Plugins can provide progress indication by wrapping underlying work in `BuildOperations`

Implementation is similar to `ConsoleBackedProgressRenderer` with throttling and a data
structure for storing recent updates.

Warnings and errors are displayed in the main area with the same format and styling as today.

`FixedSizeMultiStatusTextArea` would be an implementation of `MultiStatusTextArea` that
initializes a `List<LinkedList<ProgressOperation>>` of fixed size (defaults to `org.gradle.workers.max`
 under `--parallel` or 1 otherwise). It would keep track of `ProgressOperation` chains 
in-progress to prevent having more in-progress operations than max workers displayed.

**NOTE:** We must avoid use of save/restore cursor position unless we switch to a 
[terminfo](https://en.wikipedia.org/wiki/Terminfo)-based Console handling instead of using ANSI.

User guide is updated with new output examples.
    
### Test Coverage
* A terminal size with fewer than max parallel operations displays (rows - 2) operations. 
 That is, the progressArea never grows taller than the console height - 2 rows.
* Operation status lines are trimmed at (cols - 1)
* Renders well on default macOS Terminal, default Windows Console, Cygwin, PowerShell and
 common Linux Terminals
* Console shows idle workers under `--continuous` build

## Story: Display overall build progress on a dedicated line
Render ProgressEvents with build completeness information as a progress bar — intended to 
give the user a very fast way of telling whether the build will be finished soon.

### User-visible Changes
We can visually represent overall build progress:

**ASCII-based options (some options use extended ASCII):**
```
[####------] 114/320 Tasks executed
<===-------> 32% Building
«======    » 6 / 10 Projects configured
####>------> 63/201
[#####     ] 50%
```

### Implementation
TaskGraphExecutor generates an event with how many tasks will be executed
and events with TaskExecutionOutcome for each build operation wrapping a task.

NotifyingSettingsLoader generates an event at the start and completion of
loading settings. 

A `BuildProgressBarLogger` logs using a `ProgressBar` implementation of `ProgressFormatter`. 
A `ProgressBar` has a width and filler character.

`ConsoleBackedProgressBarRenderer` renders the `ProgressBar` using a `ProgressLabel`, which extends `Label` with 
the following additions:
 - `void setProgressBar(ProgressBar progressBar)`
 - `void setText(String text)` (inherited from `Label`)
 - `void render()` (we can probably add this to `Label`)
 
Under `--continuous` build, the progress bar and multi status text area are reused.

User guide is updated with new output examples.

### Test Coverage
* By default, logs should be streamed with plain output and not throttled when not attached to a Console
* Color output is output when requested through via the tooling API via `LongRunningOperation.setColorOutput(true)`
* OutputEvents tooling to API is unaffected except for additional BuildOperations now published
* Logs should be streamed with plain output when attached Terminal lacks of color or cursor support
* Renders well on default macOS Terminal, default Windows Console, Cygwin, PowerShell and common Linux Terminals

# Milestone 2 - More granular work in-progress display

## ~Story: Show ratio of work avoided on progress line~
Give a very brief summary of how much work was skipped and why on the status line.

### User-facing Changes
```
[#######   ] 80% Building [10% UP-TO-DATE, 75% FROM-CACHE]
 > :foo:test
 > :bar:compileScala
 > :baz:monadifyHaskell
```

Initial display is empty (no "[...]") because we don't want to list all of
the possible task outcomes as that is lengthy and unnecessary.

"EXECUTED" tasks are not included, it is assumed that whatever is left was executed.

### Implementation
Break apart `TaskExecutionStatisticsEventAdapter` to only handle task
execution events. Leverage that in `BuildProgressLogger`, notifying
`ConsoleBackedProgressBarRenderer` of updates that are then rendered to the
attached console (if any).

### Test Coverage

### Open Issues
* Display format

## Story: Show ratio of work avoided in build result
Instead of showing work avoided _during_ the build, show it afterward, in brief.

### User-facing Changes
**Before**
```
BUILD SUCCESSFUL

Total time: 3 mins 5.652 secs

Publishing build information...
https://scans.gradle.com/s/3rafycnw2n2pg
```

**After**
```
BUILD SUCCESSFUL in 3m 5s
Build Scan: https://scans.gradle.com/s/3rafycnw2n2pg
Tasks: 109/419 (26%) AVOIDED, 310/419 (74%) EXECUTED
```

### Implementation
Repurpose build cache build results report as a first-class thing and format it prettily.

### Test Cases
* Does not display or error when there are 0 tasks
* Counts tasks from composite builds
* Lifecycle/NO-SOURCE tasks should not be counted

### Open Issues
* How task outcomes are grouped. Avoided and executed are currently used by build scans.

## Story: Indicate Gradle's continued progress in absence of intra-operation updates
Provide motion in output to indicate Gradle's working if build operations are long-running.

### Expected Behavior
Each worker status line has an additional suffix with the duration it has been executing.

This duration is formatted according to the smallest unit that can represent the duration in one number. 
For example:
```
 > [:foo:compileJava] 0.1s
 > [:bar:compileJava] 2s
 > [:baz:test] 3m
```

This has the visual effect of updating frequently for small tasks and infrequently for larger tasks. 
We an also choose to add color as Buck does for long-running tasks to give focus to slow tasks when 
there are a large number of workers producing output.

### Current Behavior
The Gradle console looks like it's hung when workers are working in the background and not providing updates.

### Implementation
`Operations` data store knows the time since each operation was started. The 
`StatusBarFormatter` can choose to render an operation slightly differently depending on its lifespan.

### Test Coverage
* Time formatter handles durations up to hours
* Performance impact is nominal

### Open issues
* How are parent operations represented?
* Is overall build status affected?
* Should we change Console throttling to avoid skipping tenths of seconds?
* How about parallel test execution?

## Story: (Optional) Add a status line for work in-progress that doesn't fit on the terminal
Suppose there is more work in-progress in parallel than lines available on the terminal.
In that case, instead of cutting off output, we can summarize un-rendered work. For example,
a spinner for each additional worker.
 
The default maximum number of workers to display is `org.gradle.workers.max` or 8, whichever
is lower.

### User-facing changes
Progress line displays # of busy workers of maximum instead of % complete.
```
`###############>                       > [14 / 16] workers busy`
 > :worker1:something
 > :worker2:something
 > :worker3:something
 > :worker4:something
 > :worker5:something
 > :worker6:something
 > :worker7:something
 > :worker8:something
```

### TODO Implementation

### Test Coverage
* Number of status lines stays consistent unless terminal is resized, and never goes up
* Console resize is handled gracefully and output is not garbled if possible.

## Story: Granular progress of dependency resolution 
Display progress of dependencies resolved/unresolved in [Complete / Total] format. 

For example:
```
 > [56 / 245] Resolving dependencies for configuration 'compileJava'
```

### Implementation
`BuildOperationContext` gains a method to report a `DiscreteProgress`.
While executing a `BuildOperation`, progress can be optionally reported
by a ProgressLogger that is routed to the a Console renderer.

`SimpleProgressFormatter#getProgress()` format is changed to wrap `current` and
`total` in square brackets.

`BuildProgressBarListener` implements `DependencyResolutionListener` and
forwards dependency resolution events to `BuildProgressBarLogger`.

## Story: Granular progress of tests 
We know prior to test execution time the number of test classes. Let's give a
granular number of tests complete and remaining in [Complete / Total] format.

For example:
```
 > [1154 / 9999] :foo:test > BarLongNamesInJavaForevermoreSpec
```

### Implementation
TODO replace TestListenerInternal with build operation events.

### Test Cases
* Dynamic tests like `@Unroll` in Spock and correctly counted
* Display is throttled so that many tests completing does not impact performance
* Given no discovered tests, [X / Y] progress is omitted

## Milestone 3 - Logging Improvements

## Story: Console output is grouped by BuildOperation
Similar to how the TaskExecutionLogger works today, except that we apply the concept of logging headers
to associate all RenderableOutputEvents to a BuildOperation. Furthermore, log events are buffered and batched
per operation and only rendered upon completion of the operation.

### User Visible Changes
Logs from any given build operation are buffered and output all at once upon completion of the operation.

**Before**:
```
:util:compileJava
Note: Some input files use unchecked or unsafe operations.
Log message from build logic
:util:compileJava
Note: Recompile with -Xlint:unchecked for details.
```

**After**:
```
[:util:compileJava]
Note: Some input files use unchecked or unsafe operations.
Note: Recompile with -Xlint:unchecked for details.

[task execution listener]
Log message from build logic
```

### Implementation
Generalize what the `TaskExecutionLogger` does and apply it to all BuildOperations that generate output. This feature needs more design to be implemented.

### Test Coverage
* Log messages are flushed upon failure of a build operation
* Log messages are flushed if build is stopped prematurely (via user command or process kill)
* Log messages are only output after operation completes

# Milestone 4 - Make it gorgeous for supported environments

## Story: Display work in-progress through ProgressBar
This adds a visual indicator of what proportion of the build would be complete if all in-progress work is completed.
This requires the use of color to distinguish between different regions.

### User-facing Changes
This turns this:
`###############>                          > 40% Building`

into this:
`#####green#####>##yellow##>#####black#####> 40% Building`

### Implementation
A concept of `DiscreteProgress` wraps current, in-progress, and total fields, each Longs.

`ProgressBar` would have another operation:
 - `void setRegions(List<ProgressBarRegion> regions)`

A `ProgressBarRegion` has operations:
 - `void setPrefix(String prefix)`
 - `void setSuffix(String suffix)`
 - `void render(Integer width)`
 
`BuildProgressBarListener` would keep track of work starting through `projectsEvaluated(Gradle)`
and `beforeExecute(Task)` and forward that information to `ProgressLabel` 

## Story: Standardize a way to accept user input during a build
Gradle does not handle input very gracefully, especially as work is run in parallel, because we continue to update
the console output which interferes with prompting and accepting user input.
 
### TODO Implementation

### Test Cases
 * `System.in` and SystemConsole I/O happens on the mainArea, which is unaffected by other areas

### Open Issues
 * Should we consider:
   * a new API
   * a dedicated section of the Console output, or
   * some property that causes the Console to behave differently

## Story: Allow user to opt-in to beautiful, Gradle-themed console output
Provide a modernized feel for the Gradle console using color and UTF-8 characters.

### User-facing Changes
If Gradle property `org.gradle.console.modernized=true` is present, Console renderers add ANSI colors 
and use UTF-8 characters to achieve a modern console design.

On ANSI terminals, we can use empty spaces with different background colors for the "bars".
The progress bar shall be red given a failing build.

Note that the suffix at the end of the progress bar should be the progress for the current
Gradle execution phase.

### Implementation


### Test Coverage
* Renders well using default iTerm, Terminal.app, Cygwin, Gnome Terminal, and Powershell config
* Colors account for common forms of color blindness

## Story: (Optional) Customizable ProgressBarFormat
Allow users to configure how `ProgressBar` is formatted. This allows for great creativity
without needing to provide a default implementation

Details that can be customized:
 * `ProgressBarRegion` prefix
 * `ProgressBarRegion` suffix
 * `ProgressBar` complete char (default is "#")
 * `ProgressBar` width (default is 50)

### Implementation
`ProgressBarFormatter` implementation would read the following properties and provide defaults.
```bash
org.gradle.console.progressbar.succeeded.prefix="[32m"   #FG_GREEN
org.gradle.console.progressbar.failed.prefix="[31m"      #FG_RED
org.gradle.console.progressbar.inprogress.prefix="[33m"  #FG_YELLOW
org.gradle.console.progressbar.unstarted.prefix="[30m"   #FG_BLACK
org.gradle.console.progressbar.region.suffix=""
org.gradle.console.progressbar.completechar="#"
org.gradle.console.progressbar.width=50
```

### Test Cases
* Helpful error message is displayed if progressbar.width isn't a natural number
* `progressbar.completechar` accepts multi-byte characters and displays correctly
* Width of progress bar is consistent given multi-character "suffix"
* Helpful error message if `progressbar.completechar` isn't one character in length
* Progress bar is truncated at width of Console
* Fuzz testing to protect against any security issues arising from using user input

### Open issues
* Could this concept allow customization of ProgressLine format, providing even greater flexibility?
