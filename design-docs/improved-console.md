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
* What command did I execute?

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

## Story: Display parallel work in-progress independently by operation
Show incomplete ProgressOperations on separate lines, up to a specified maximum number. 

This is opt-in initially by specifying the `org.gradle.console.parallel=true` Gradle property.

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
so that a mapping of worker to index of operation status line can be maintained.

A `OperationStatusBarFormatter` can be used to customize the intra-line display of each 
`ProgressOperation` chain (each `ProgressOperation` has a reference to its parent)

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
    
### Test Coverage
* A terminal size with fewer than max parallel operations displays (rows - 2) operations. 
 That is, the progressArea never grows taller than the console height - 2 rows.
* Operation status lines are trimmed at (cols - 1)
* `System.in` and SystemConsole I/O happens on the mainArea, which is unaffected by other areas
* Renders well on default macOS Terminal, default Windows Console, Cygwin, PowerShell and
 common Linux Terminals
* Console shows idle workers under `--continuous` build

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

### Test Cases
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

### Open issues
* This should take source (build operation) into consideration

## Story: Display overall build progress as a progress bar
Render ProgressEvents with build completeness information as a progress bar.
 
Intended to give the user a very fast way of telling whether the build will be finished soon or not.
The work-in-progress (yellow) section shows how much of the build would be complete if all
Operations displayed in the progressArea completed.

### User-visible Changes
We can visually represent complete and un-started tasks of build using colorized output:

`#####green#####>###########black##########> 40% Building`

On ANSI terminals, we can use empty spaces with different background colors for the "bars" and
A red background shall be used for failed tasks. 

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

### Test Coverage
* By default, logs should be streamed with plain output and not throttled when not attached to a Console
* Color output is output when requested through via the tooling API via `LongRunningOperation.setColorOutput(true)`
* OutputEvents tooling to API is unaffected except for additional BuildOperations now published
* Logs should be streamed with plain output when attached Terminal lacks of color or cursor support
* Renders well on default macOS Terminal, default Windows Console, Cygwin, PowerShell and common Linux Terminals

## Story: Display work-in-progress through ProgressBar
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

## Story: Display intra-operation progress
When we know in advance the number of things to be processed, we display progress in [Complete / Total] format. 
Provide APIs to optionally add # complete and # of all items.

For example:
```
[23 / 65] Configuring projects
[56 / 245] Resolving dependencies for configuration 'compileJava'
```

### Implementation
`BuildOperationContext` gains a method to report a `DiscreteProgress`.
While executing a `BuildOperation`, progress can be optionally reported
by a ProgressLogger that is routed to the a Console renderer.

`SimpleProgressFormatter#getProgress()` format is changed to wrap `current` and
`total` in square brackets.

`BuildProgressBarListener` implements `DependencyResolutionListener` and
forwards dependency resolution events to `BuildProgressBarLogger`.

### Test Cases
* Implementations are thread-safe in the face of multiple threads updating status

### Open Issues
* Can we discover the number of tests to run or classes to compile ahead of time?
* We'll need a public API to give plugins access to log intra-operation progress.

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

## Story: Gradle-provided slick themes
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

### Open issues
* How is intra-operation progress ([55 / 1234] or spinner) affected?

## Story: (Optional) Indicate Gradle's continued progress in absence of intra-operation updates
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

### Open issues
* Best choice to render from given options above

## Story: (Optional) Detect terminals that support UTF-8 and provide richer UI elements by default
Support detection and use of UCS-2 (default supported by Windows) or UTF-8 characters could further polish this if we choose.

This would allow us to use characters like ᐅ or ► supported by a majority of monospace fonts.
More granular width for progress bars: ' ', '▏', '▎', '▎', '▍', '▍', '▌', '▌', '▋', '▋', '▊', '▊', '▉', '▉', '█'

### Implementation
Change default `org.gradle.console.progressbar.*` property values if we detect a console we know
supports advanced features. 

## Story: (Optional) "Fade out" operation result
Tasks completed continue to be displayed for 1 more "tick" of the clock with their resolution status, 
but are displayed in a muted color (ANSI bright black, perhaps).

For example:
```
:api:jar UP-TO-DATE
```

## Story: (Optional) Use 256 color output when supported
ANSI colors are limited and dependent on color mapping of the underlying terminal. 
We can achieve much better look and feel using a 256-color palette when supported.

We may also need to capture the default background color of the terminal so we 
can adjust colors to at least light and dark backgrounds. This can be done with 
control sequences on at least *nix terminal.
 
### Implementation
Requires use of terminfo controls. Capture output of `tput colors` when available. 
If >=256, use a Gradle-chosen color palette.

Change default `org.gradle.console.progressbar.*` property values if we detect a console we know
supports additional colors.

### Open issues
* Color choices we would use

## Story: (Optional) Illustrate build phases independently
Similar to "Display build progress as a progress bar through colorized output" above, but having clearly separated output for each build phase.
For example, it could be 3 separate progress bars with independent lines.

### User-facing changes
**Option 1**

All 3 lines displayed at the same time
```
##################green###################> Initialization Complete
##################green###################> Configuration Complete
#####green#####>##yellow##>#####black#####> 40% Building
```

**Option 2**

_Only 1_ of the following is displayed, depending on the build phase
```
#####green#####>##yellow##>#####black#####> 40% Initializing                                    <#Initialization#
#####green#####>##yellow##>#####black#####> 40% Configuring                     <#Configuration#<#Initialization#
#####green#####>##yellow##>#####black#####> 40% Building            <#Execution#<#Configuration#<#Initialization#
```

### Implementation
