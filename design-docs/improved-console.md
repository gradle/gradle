# Improved Console Output

## Primary Goals
* Improve perceived performance by making user aware of background work
* Modern feel
* User extensibility

### User Goals for Console
In priority order, the user wants to answer the following questions:

* When will my build finish?
    * Soon (should I keep watching it) or later ("I'll check back")
* Is there anything I should know about my build right now? (e.g. tests have failed, warnings)
* What is Gradle doing right now?
* Is my build successful?
* What command did I execute?

## Story: Display parallel work in-progress independently by operation
Show incomplete ProgressOperations on separate lines, up to a specified maximum number. 

### Implementation
A `RichConsole` has 
 - an appendable `TextArea` (mainArea)
 - a `ProgressLabel` (consisting of a `ProgressBar` and `StatusText`)
 - a fixed-height, random-access `TextArea` (progressArea)
 
The mainArea is displayed at the top for rendering important messages like warnings
and errors.

`--quiet`, `--info`, and `--debug` log level flags affect the output visible in the main TextArea,
However, the default will change so that `LIFECYCLE` logs are no longer displayed.
 
The `ProgressLabel` is intended to give the user a glance-able indicator that tells
them whether the build will be finished soon or not. 
See ["Display build progress as a progress bar through colorized output"](TODO) below.

The multiple progressArea lines are rendered results of a fixed list of `AbstractWorker`s. 
It displays work-in-progress for each `AbstractWorker`. 
Forked processes submit BuildOperations with information about "what" is running "where".

Plugins can provide progress indication by publishing `BuildOperations`

A `OperationStatusBarFormatter` can be used to customize the intra-line display of each 
`ProgressOperation` chain (each `ProgressOperation` has a reference to its parent)

Implementation is similar to `ConsoleBackedProgressRenderer` with throttling and a data
structure for storing recent updates.

> NOTE: We must avoid use of save/restore cursor position unless we switch to a 
[terminfo](https://en.wikipedia.org/wiki/Terminfo)-based Console handling instead of using ANSI.
    
### Test Coverage
* A terminal size with fewer than max parallel operations shows (rows - 2) operations in parallel
* Operation status lines are trimmed at (cols - 1)
* `System.in` and SystemConsole I/O happens on the mainArea, which is unaffected by other areas
* Renders well on default macOS Terminal, default Windows Console, Cygwin, and common Linux Terminals
* Console display degrades gracefully given BuildOperations that are out-of-order (e.g. complete event received before start)

### Open issues
* How is `--continuous` build displayed? During execution? While waiting? (as we allow processes to stay running)
* When things fail, will the new CLI look like a regular failure or something else?
* How does `--parallel` (or not) affect display?
* How does having more operations than workers affect display?

## Story: Display build progress as a progress bar through colorized output
Render ProgressEvents with build completeness information as a progress bar.
 
Intended to give the user a very fast way of telling whether the build will be finished soon or not.
The work-in-progress (yellow) section shows how much of the build would be complete if all
Operations displayed in the progressArea completed.

### User-visible Changes
We can visually represent complete, in-progress, and un-started tasks of build using colorized output:

`#####green#####>##yellow##>#####black#####> 40% Building`

On ANSI terminals, we can use empty spaces with different background colors for the "bars" and
A red background shall be used for failed tasks. 

### Implementation
`DefaultGradleLauncherFactory` registers a `BuildProgressListener` listening
to events from `BuildProgressLogger` and forwards them to a `ConsoleBackedBuildProgressRenderer`.
 
`ProgressLine` consists of a `ProgressBar`, status text, and a width. It doesn't know anything about builds. 

`ProgressBar` has text and width. Weakly modeling this allows for alternate `ProgressLine`s that can
potentially be configured by users.

The `DefaultProgressBar`, though, has a width and a list of `ProgressBarRegion`s,
each of which have a width and a `RegionType` of `SUCCEEDED`, `FAILED`, `IN_PROGRESS`, or `UNSTARTED`. 
Each`RegionType` is associated with a color. This allows us to show failures in arbitrary locations
as needed with `--continue`

### Test Coverage
* Logs should be streamed with plain output and not throttled when not attached to a Console
* The TAPI is unaffected except for additional BuildOperations now published
* Logs should be streamed with plain output when attached Terminal lacks of color or cursor support
* Renders well on default macOS Terminal, default Windows Console, Cygwin, PowerShell and common Linux Terminals

### Open Issues
* Characters outside ASCII will give a more polished look, but with risk of poor display if console font doesn't
support them
* ASCII-based, unstyled alternatives:
```
[##                        ] 6% Building
[‡‡‡‡                      ] 12% Building
‹===================       › 60% Building
«==========================» 100% BUILD SUCCESS
```

## Story: Display intra-operation progress
When we know in advance the number of things to be processed, we display progress in [Complete / All] format. 
Provide APIs to optionally add # complete and # of all items.  

For example:
```
[23 / 65] Configuring tasks
[999 / 1234] Running tests
[384 / 218932] Compiling classes
[56 / 245] Resolving dependencies
```

### Implementation
 TODO

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
* Helpful error message if `progressbar.completechar` isn't one character in length
* Progress bar is truncated at given width
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
 TODO

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
