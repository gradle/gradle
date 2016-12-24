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

`--quiet`, `--info`, and `--debug` log level flags affect the output visible in the main TextArea,
However, the default will change so that `LIFECYCLE` logs are no longer displayed.
 
The `ProgressLabel` is intended to give the user a glance-able indicator that tells
them whether the build will be finished soon or not. 
See ["Display build progress as a progress bar through colorized output"](#story-display-build-progress-as-a-progress-bar-through-colorized-output) below.

The multiple progressArea lines are rendered results of a fixed list of `AbstractWorker`s. 
It displays work-in-progress for each `AbstractWorker`. 
Forked processes submit BuildOperations with information about "what" is running "where".

A `OperationStatusBarFormatter` can be used to customize the intra-line display of each 
`ProgressOperation` chain (each `ProgressOperation` has a reference to its parent)

### Implementation
A `RichConsole` extends `Console` with the following operations: 
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
* Renders well on default macOS Terminal, default Windows Console, Cygwin, PowerShell and common Linux Terminals
* Console display gracefully handles ProgressOperations that are out-of-order (e.g. complete event received before start)
* Console shows idle workers under `--continuous` build

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
`DefaultGradleLauncherFactory` registers a `RichBuildProgressListener` (similar to `BuildProgressFilter`)
and forwards them to a `ConsoleBackedBuildProgressRenderer`. It would maintain a `RichBuildProgressLogger`
that is responsible for updating the `ProgressLabel`.

**TODO**: This model is missing some classes and implementation between `RichBuildProgressLogger` and `ProgressLabel`
 
A `ProgressLabel` extends `Label` with the following additions:
 - `void setProgressBar(ProgressBar progressBar)`
 - `void setText(String text)` (inherited from `Label`)
 - `void render()` (we can probably add this to `Label`)
 
A `ProgressBar` has:
 - `void setMaxWidth(Integer maxWidth)`
 - `void setFillerChar(Char fillerChar)`
 - `void render()`
 
Under `--continuous` build, the progress bar and multi status text area are reused.

### Test Coverage
* Logs should be streamed with plain output and not throttled when not attached to a Console
* The TAPI is unaffected except for additional BuildOperations now published
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
`ProgressBar` would have another operation:
 - `void setRegions(List<ProgressBarRegion> regions)`

A `ProgressBarRegion` has operations:
 - `void setPrefix(String prefix)`
 - `void setSuffix(String suffix)`
 - `void render(Integer width)`
 
`RichBuildProgressListener` would keep track of work starting through `projectsEvaluated(Gradle)`
and `beforeExecute(Task)` and forward that information to `ProgressLabel` 

## Story: Display intra-operation progress
When we know in advance the number of things to be processed, we display progress in [Complete / Total] format. 
Provide APIs to optionally add # complete and # of all items.  

For example:
```
[23 / 65] Configuring tasks
[999 / 1234] Running tests
[384 / 218932] Compiling classes
[56 / 245] Resolving dependencies
```

### Implementation
`SimpleProgressFormatter` is given a settable `String prefix`.

**TODO:** Provide more implementation here. Possibly getting more information through 
`RichBuildProgressListener` by checking/casting Tasks executing.

Dependency resolution is wrapped in `BuildOperation`s, captured in a `BuildListener`, 
and rendered through the generated `ProgressOperation`s. This design is part of another spec.

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
org.gradle.console.progressbar.succeeded.suffix=""
org.gradle.console.progressbar.failed.suffix=""
org.gradle.console.progressbar.inprogress.suffix=""
org.gradle.console.progressbar.unstarted.suffix=""
org.gradle.console.progressbar.completechar=" "
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
org.gradle.console.progressbar.succeeded.suffix=""
org.gradle.console.progressbar.failed.suffix=""
org.gradle.console.progressbar.inprogress.suffix=""
org.gradle.console.progressbar.unstarted.suffix=""
org.gradle.console.progressbar.completechar=" "
```

### Implementation
A `PowerlineProgressBarRegion` would extend `ProgressBarRegion` with the defaults
given above. `RichBuildProgressLogger` would detect the use of a Gradle property
`org.gradle.console.theme=powerline` and substitute `PowerlineProgressBarRegion` 
instead of the `DefaultProgressBarRegion`.

## Story: Customizable StatusBarFormat
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
* Fuzz testing ensures no other parts of build are affected

### Open issues
* How is intra-operation progress ([55 / 1234] or spinner) affected?

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
* Color choices we would use if we could

## Story: (Optional) Illustrate build phases independently
Similar to "Display build progress as a progress bar through colorized output" above, but having clearly separated output for each build phase.
For example, it could be 3 separate progress bars with independent lines.
 
### Implementation
 TODO

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
