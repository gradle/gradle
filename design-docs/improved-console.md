# Improved Console Output

## Primary Goals
* Improve perceived performance by making user aware of background work
* Modern feel
* User extensibility

### User Goals for Console
The user wants to answer the following questions:

* When will my build finish?
    * Soon (should I keep watching it) or later ("I'll check back")
* Is there anything I should know about my build right now? (e.g. tests have failed, warnings)
* What is Gradle doing right now?
* Is my build successful?
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
Show incomplete ProgressOperations on separate lines, up to a specified maximum number. 

### User Visible Changes
The mainArea is displayed at the top for rendering important messages like warnings
and errors.

`TaskExecutionLogger` no longer sets logging headers. This prevents logging of task names
in main area. To compensate for the user's loss of context, [WARN and ERROR logs can be 
configured to give more context](#story-log-messages-can-be-configured-to-give-more-context),
possibly setting the log category (and allowing `LogEvent` to accept a category)
to the build operation description then configuring `StyledTextOutputBackedRenderer`
to render category by default.

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

> NOTE: Plugins can provide progress indication by publishing `BuildOperations` (separate effort)

Implementation is similar to `ConsoleBackedProgressRenderer` with throttling and a data
structure for storing recent updates.

Warnings and errors are displayed in the main area with the same format and styling as today.

`FixedSizeMultiStatusTextArea` would be an implementation of `MultiStatusTextArea` that
initializes a `List<LinkedList<ProgressOperation>>` of fixed size (defaults to `org.gradle.workers.max`
 under `--parallel` or 1 otherwise). It would keep track of `ProgressOperation` chains 
in-progress to prevent having more in-progress operations than max workers displayed.

> NOTE: We must avoid use of save/restore cursor position unless we switch to a 
[terminfo](https://en.wikipedia.org/wiki/Terminfo)-based Console handling instead of using ANSI.

User guide is updated with new output examples.
    
### Test Coverage
* A terminal size with fewer than max parallel operations displays (rows - 2) operations. 
 That is, the progressArea never grows taller than the console height - 2 rows.
* Operation status lines are trimmed at (cols - 1)
* `System.in` and SystemConsole I/O happens on the mainArea, which is unaffected by other areas
* Renders well on default macOS Terminal, default Windows Console, Cygwin, PowerShell and
 common Linux Terminals
* Console shows idle workers under `--continuous` build

## Story: Display overall build progress as a progress bar
Render ProgressEvents with build completeness information as a progress bar.
 
Intended to give the user a very fast way of telling whether the build will be finished soon or not.

### User-visible Changes
We can visually represent complete and un-started tasks of build using colorized output:

`###############>                       > 40% Building`

On ANSI terminals, we can use empty spaces with different background colors for the "bars".
The progress bar shall be red given a failing build.

Note that the suffix at the end of the progress bar should be the progress for the current
Gradle execution phase.

**ASCII-based options (some options use extended ASCII):**
```
[##                        ] 6% Building
[‡‡‡‡                      ] 12% Building
###########>               > 33% Building
‹===================       › 60% Building
«==========================» 100% BUILD SUCCESS
```

### Implementation
`DefaultGradleLauncherFactory` registers a listener `BuildProgressBarListener` (similar to `BuildProgressFilter`)
and forwards events to a `ConsoleBackedProgressBarRenderer`. It would maintain a `BuildProgressBarLogger` that
logs using a `ProgressBar` implementation of `ProgressFormatter`. A `ProgressBar` has a width and filler character.

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

## Story: Improve BuildOperation name consistency
Standardize the terminology and format of build operation names. This is necessary for a cohesive
experience as these are displayed while the operation is executing. 

### TODO Implementation

### Test Coverage
* Update tests to reflect updated output where necessary

### Open Issues
* How is buildSrc rendered?
* How are composite builds rendered?
* How are GradleBuild tasks rendered?

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

# Milestone 2 - More granular work in-progress display

## Story: Display work in-progress through ProgressBar
This adds a visual indicator of what proportion of the build would be complete if all in-progress work is completed.
This requires the use of color to distinguish between different regions.

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

### Open Issues
* Alternative implementation of text-based spinner (e.g. "┘└┌┐" or "|/-\\")

## Story: Indicate Gradle's continued progress in absence of intra-operation updates
Provide motion in output to indicate Gradle's working if build operations are long-running.

Options:
* Incrementing time
* Blinking a small area of progress bar
* Blinking indicator on progress operations
* Adjusting color of progress operations (may require 256 colors)
* Text-based spinner (e.g. "┘└┌┐" or "|/-\\")

### Implementation
`Operations` data store knows the time since each operation was started. The 
`StatusBarFormatter` can choose to render an operation slightly differently depending
on its lifespan.

## Story: Show ratio of work avoided on progress line
Give a very brief summary of how much work was skipped and why on the status line.

### User-facing Changes

**Before:**
`#####green#####>##yellow##>#####black#####> 40% Building`

**After:**
`#####green#####>##yellow##>#####black#####> 40% Building [10% UP-TO-DATE, 75% FROM-CACHE]`

### TODO Implementation

### TODO Test Coverage

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
`BuildProgressBarListener` implements a TestExecutionListener (TBD) and
forwards test complete events to `BuildProgressBarLogger`.

### Test Cases
* Dynamic tests like `@Unroll` in Spock and correctly counted
* Display is throttled so that many tests completing does not impact performance
* Given no discovered tests, [X / Y] progress is omitted

## Milestone 3 - Logging Improvements

## Story: Log messages can be configured to give more context
With the removal of task execution logging in the main area, users need to get some additional
context when warnings or errors occur. 

### User Visible Changes
Users see log level and build operation description for WARN and ERROR level 
when rendering log events that have a severity and build operation specified.

**Before**:
```
Note: Some input files use unchecked or unsafe operations.
Note: Recompile with -Xlint:unchecked for details.
Log message from build logic
```

**After**:
```
[:util:compileJava] Note: Some input files use unchecked or unsafe operations.
[:util:compileJava] Note: Recompile with -Xlint:unchecked for details.
Log message from build logic
```

### Implementation
The concept of a source operation is added to `RenderableOutputEvent`. `LogEvent`s
and `StyledTextOutputEvent` can be constructed with a String that generally 
contains the `BuildOperation` description. 

### Test Coverage
* Log messages without source operation configured renders same as usual

### Open Issues
* Should categories be leveraged instead?
* Concept of log level or severity
* Additions needed to `org.gradle.api.Logger` and `StyledTextOutput` for use in build scripts

## Story: Allow user to include additional info in log messages
Allow users to specify whether timestamp, log level, and/or category are logged with each message.

Format is constant as "%dateTime% [%logLevel%] [%category%]"

### Implementation
Change logic of `StyledTextOutputBackedRenderer` to be configured with new Gradle properties

```bash
org.gradle.logger.showCategory=true                        # default=false
org.gradle.logger.dateTimeFormat=yyyy-MM-dd HH:mm:ss:SSS Z # default=HH:mm:ss.SSS
org.gradle.logger.showDateTime=true                        # default=false
org.gradle.logger.showLogLevel=true                        # default=true
```

When rendering `RenderableOutputEvent`s, user configuration is taken into account.

### Test Coverage
* Does not display category when it is `null`.
* Gives helpful error message and fails fast given invalid datetime format string

### Open issues
* This should take source (build operation) into consideration

# Milestone 4 - Make it gorgeous for supported environments

## Story: Gradle-provided powerline theme
Configure the default `org.gradle.console.progressbar.*` prefix and suffix based on a single 
property.

For example, a [powerline](https://github.com/powerline/powerline) theme, we would change the
default progress bar properties to:

```bash
org.gradle.console.progressbar.succeeded.prefix="[42m"   #BG_GREEN
org.gradle.console.progressbar.failed.prefix="[41m"      #BG_RED
org.gradle.console.progressbar.inprogress.prefix="[43m"  #BG_YELLOW
org.gradle.console.progressbar.unstarted.prefix="[40m"   #BG_BLACK
org.gradle.console.progressbar.region.suffix=""
org.gradle.console.progressbar.completechar=" "
```

### Implementation
A `PowerlineProgressBarRegion` would extend `ProgressBarRegion` with the defaults
given above. `BuildProgressBarListener` would detect the use of a Gradle property
`org.gradle.console.theme=powerline` and substitute `PowerlineProgressBarRegion` 
instead of the `DefaultProgressBarRegion`.

### Test Coverage
* Renders well using default iTerm, Terminal.app, Cygwin, Gnome Terminal, and Powershell config
* Colors account for common forms of color blindness

## Story: Customizable ProgressBarFormat
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

## Story: (Optional) Customizable StatusBarFormat
Allow users to configure the format of `StatusLine` entries in the status TextArea.

Aspects that can be customized:
 * `StatusBarLine` Prefix
 * `StatusBarLine` Suffix

### Implementation
Introduce Gradle properties that allow this customization (with defaults):
```bash
org.gradle.console.statusline.prefix=" > "
org.gradle.console.statusline.suffix=""
```

### Test Cases
* Fuzz testing ensures no other parts of build are affected by user input
