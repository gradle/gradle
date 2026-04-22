# Plan: Gradle-Side TUI Support for Forked Processes

This plan describes the Gradle changes needed to allow forked processes to run
TUI applications via PTY allocation and daemon protocol relay. This covers all
process execution entry points in Gradle:

- **Tasks**: `Exec` and `JavaExec` tasks
- **Programmatic API**: `ExecOperations.exec()` and `ExecOperations.javaexec()`
  (injectable into plugins, tasks, and other build logic)
- **Internal**: `ExecFactory` / `ExecActionFactory` (used by Gradle internals)

All of these converge on `BaseExecSpec` (extended by both `ExecSpec` and
`JavaExecSpec`), so adding `fullTerminal` to `BaseExecSpec` covers every entry
point.

It assumes the `native-platform` PTY extensions described in `PLAN_NATIVE.md`
are available.

---

## Background — How Process Execution Works Today

### Task → ExecAction → ExecHandle → Process

```
AbstractExecTask.exec()                           [subprojects/core]
  → ExecActionFactory.newExecAction()              [process-services]
  → DefaultExecSpec.copyTo(execAction)
  → DefaultExecAction.execute()
      → DefaultClientExecHandleBuilder.build()     [process-services]
          → creates DefaultExecHandle with:
              outputHandler = OutputStreamsForwarder(stdout, stderr)
              inputHandler  = EmptyStdInStreamsHandler (or ForwardStdinStreamsHandler)
      → DefaultExecHandle.start()
          → ExecHandleRunner.run()                 [on executor thread]
              → ProcessBuilderFactory.createProcessBuilder(execHandle)
              → processLauncher.start(processBuilder)   [native-platform]
              → streamsHandler.connectStreams(process, ...)
              → streamsHandler.start()
              → process.waitFor()
              → streamsHandler.stop()
```

### Key classes and their locations

| Class | Path | Role |
|-------|------|------|
| `BaseExecSpec` | `process-services-api/.../BaseExecSpec.java` | Public API: `standardInput`, `standardOutput`, `errorOutput`. Extended by both `ExecSpec` and `JavaExecSpec` — the `fullTerminal` property goes here. |
| `ExecSpec` | `process-services-api/.../ExecSpec.java` | Public API: extends BaseExecSpec with command-line config |
| `JavaExecSpec` | `core-api/.../JavaExecSpec.java` | Public API: extends BaseExecSpec with Java-specific config |
| `ExecOperations` | `core-api/.../ExecOperations.java` | Public API: injectable service with `exec(Action<ExecSpec>)` and `javaexec(Action<JavaExecSpec>)`. Delegates to `ExecFactory`. |
| `DefaultExecOperations` | `process-services/.../DefaultExecOperations.java` | Wraps `ProcessOperations` (i.e., `ExecFactory`) |
| `ExecFactory` | `process-services/.../ExecFactory.java` | Internal: extends `ProcessOperations`, `ExecActionFactory`, and others. Implemented by `DefaultExecActionFactory`. |
| `DefaultExecActionFactory` | `process-services/.../DefaultExecActionFactory.java` | Creates `ExecAction`/`JavaExecAction` instances. `exec(action)` creates action, configures via callback, calls `execute()`. |
| `AbstractExecTask` | `core/.../AbstractExecTask.java` | Task base: wraps `DefaultExecSpec`, calls `ExecActionFactory` |
| `Exec` | `core/.../Exec.java` | Concrete task |
| `JavaExec` | `language-java/.../JavaExec.java` | Concrete task |
| `DefaultClientExecHandleBuilder` | `process-services/.../DefaultClientExecHandleBuilder.java` | Builds `DefaultExecHandle` with stream handlers |
| `ExecHandleRunner` | `process-services/.../ExecHandleRunner.java` | Runs process: creates `ProcessBuilder`, launches via `ProcessLauncher`, connects streams |
| `OutputStreamsForwarder` | `process-services/.../streams/OutputStreamsForwarder.java` | Reads process stdout/stderr into Java OutputStreams |
| `ForwardStdinStreamsHandler` | `process-services/.../streams/ForwardStdinStreamsHandler.java` | Writes Java InputStream into process stdin |
| `ProcessBuilderFactory` | `process-services/.../ProcessBuilderFactory.java` | Creates `ProcessBuilder` from `ProcessSettings` |
| `NativeServices` | `native/.../NativeServices.java` | Provides `ProcessLauncher` (native-platform or fallback `DefaultProcessLauncher`) |

### Daemon I/O chain

```
Real terminal → client stdin
  → DaemonClientInputForwarder (reads via InputStreamReader)
  → ForwardInput(byte[]) protocol message
  → DefaultDaemonConnection receiver thread
  → StdinQueue → StdinHandler
  → ClientInputForwarder → StdInStream (PipedInputStream replacement for System.in)
  → Forked process stdin (pipe)
```

```
Forked process stdout (pipe)
  → ExecOutputHandleRunner (reads 8KB chunks)
  → OutputStreamsForwarder → System.out (captured by Gradle)
  → PrintStreamLoggingSystem → StyledTextOutputEvent
  → OutputMessage protocol message → client
  → Rich console rendering (AnsiConsole with status bar)
```

### Console management

- `ConsoleConfigureAction` sets up Rich/Plain/Verbose/Colored console based on
  `ConsoleOutput` enum and `ConsoleMetaData` (terminal detection).
- `OutputEventRenderer` saves original `System.out`/`System.err` before
  replacing them with capturing PrintStreams. Available via
  `getOriginalStdOut()` / `getOriginalStdErr()`.
- `UserInputConsoleRenderer.startInput()` hides the build progress area by
  calling `console.getBuildProgressArea().setVisible(false)`.
- `UserInputConsoleRenderer.finishInput()` shows it again.
- `AbstractUserInputRenderer` maintains a `paused` boolean and an `eventQueue`
  list. When `paused = true`, all non-control output events are buffered in
  `eventQueue` instead of being forwarded to the console. When resumed, queued
  events are replayed in order.

### Daemon message routing

`DefaultDaemonConnection` maintains a dedicated receiver thread that
continuously reads messages from the client connection and routes them:

- `InputMessage` (`ForwardInput`, `UserResponse`, `CloseInput`) → `StdinQueue`
- `Cancel` → `CancelQueue`
- Everything else → `ReceiveQueue`

Each queue is consumed by a separate managed executor. The `StdinQueue` handler
dispatches to a `StdinHandler` registered by `ForwardClientInput` (a
`DaemonCommandAction` in the execution chain). The handler writes `ForwardInput`
bytes into `ClientInputForwarder.StdInStream` which replaces `System.in` for
the build.

---

## Goal

When `fullTerminal = true` is set on any process execution — whether via `Exec`
/ `JavaExec` tasks or programmatic `ExecOperations.exec()` /
`ExecOperations.javaexec()` calls — allocate a PTY for the child process (via
native-platform's `PtyProcessLauncher`) and relay I/O between the client
terminal and the PTY through the daemon protocol. The child sees a real
terminal on stdin and stdout (`isatty() = true`), TUI apps work, and the
daemon handles everything transparently.

### Architecture Overview

```
┌──────────────────────────────────┐
│ Gradle Client                    │
│                                  │
│  Real terminal (raw mode)        │
│  TerminalInput.rawMode()         │
│  TerminalInput.getInputStream()  │
│       ↕ raw bytes                │
│  DaemonClientInputForwarder      │
│  (binary mode, no BufferedReader) │
│       ↕ ForwardInput / ForwardPtyOutput
│  Daemon connection               │
└──────────┬───────────────────────┘
           │ daemon protocol
┌──────────▼───────────────────────┐
│ Gradle Daemon                    │
│                                  │
│  PtyProcessLauncher.start(...)   │
│       ↕                          │
│  PtyProcess                      │
│    masterOutput → child stdin    │
│    masterInput  ← child stdout   │  PTY (isatty=true)
│    errorStream  ← child stderr   │  pipe (isatty=false)
│       ↕                          │
│  PtyStreamsHandler                │
│    relays master↔protocol        │
│    relays stderr→protocol        │
└──────────────────────────────────┘
```

---

## Concurrent Interactive Tasks — Terminal Lease

### The problem

A build can execute multiple tasks in parallel. Several scenarios involve
interactive tasks:

1. **Regular Exec + Interactive Exec**: The interactive task writes
   `ForwardPtyOutput` directly to the real terminal (bypassing the logging
   pipeline), while the regular task's output flows through `OutputMessage` →
   `OutputEventRenderer` → console. If both are active, the regular task's
   formatted output interleaves with the TUI's raw ANSI sequences, producing
   garbled output.

2. **Two Interactive Exec tasks**: Both want exclusive terminal ownership — raw
   mode input can only go to one PTY master, and two TUI apps painting the
   screen simultaneously is nonsensical. There is only one terminal.

### Design: exclusive terminal lock with worker lease release

Introduce a **`TerminalLease`** — an exclusive resource lock that interactive
tasks must acquire before launching their PTY process. This follows the existing
`ExclusiveAccessResourceLock` pattern in Gradle's resource management.

#### How it works

- The `TerminalLease` is a **build-session-scoped** singleton
  (`Scope.BuildSession.class`) managed by `ResourceLockCoordinationService`.
  Build-session scope is correct because the terminal is a per-session resource
  (shared across composite builds and `GradleBuild` task invocations within the
  same daemon session).
- When an interactive task reaches `ExecHandleRunner`, it attempts to acquire
  the terminal lease **before** launching the PTY process.
- If the lease is available, the task acquires it, sends
  `InteractiveProcessStarted` to the client, launches the PTY process, and
  runs until completion. On exit, it sends `InteractiveProcessEnded` and
  releases the lease.
- If the lease is already held by another interactive task, the requesting
  task **releases its worker lease** before blocking on the terminal lease.
  This uses the existing `WorkerLeaseService.blocking()` pattern — the same
  mechanism used by test execution (`TestMainAction`) and other long-blocking
  operations. Releasing the worker lease allows Gradle's task executor to
  schedule other non-interactive tasks on the freed lease, avoiding deadlocks
  and maintaining build throughput.

#### Worker lease release pattern

The blocking call follows the established Gradle pattern:

```java
// In ExecHandleRunner, when terminal lease is not immediately available:
workerLeaseService.blocking(() -> {
    terminalLease.acquire(taskDisplayName);
});
// Worker lease is released while waiting, re-acquired when terminal lease obtained
```

`WorkerLeaseService.blocking()` (in `DefaultWorkerLeaseService`):
1. Collects all worker leases and project locks held by the current thread.
2. Releases them via `withoutLocks()`.
3. Executes the blocking action (waiting for the terminal lease).
4. Re-acquires the worker lease and project locks before returning.

This means a build with `--max-workers=4` and two interactive tasks does not
waste two worker slots while one task waits — the waiting task's slot is
returned to the pool.

#### Output buffering while the lease is held

While the terminal lease is held:

- The client is in raw terminal mode — the rich console is suspended.
- Any `OutputMessage` events from other tasks are **queued** in
  `AbstractUserInputRenderer`'s existing `eventQueue` mechanism (the same
  pattern used today for user input prompts: set `paused = true`, buffer
  events, replay on resume).
- When the interactive task finishes and the console resumes, queued events
  are replayed so no output is lost.

#### Summary of behavior

| Scenario | Behavior |
|----------|----------|
| Single interactive task | Acquires lease, runs with PTY, releases on exit |
| Interactive + regular task | Interactive holds lease, regular runs concurrently but its output is buffered until interactive finishes |
| Two interactive tasks | First acquires lease, second releases its worker lease and blocks silently. When first finishes, second re-acquires worker lease and proceeds. Non-interactive tasks continue running on the freed worker slot. |
| Interactive task + user input prompt | The interactive task's `InteractiveProcessStartEvent` subsumes the existing user-input pause mechanism — both use the same `paused` + `eventQueue` pattern |

---

## Tooling API — No Terminal Available

### The problem

Gradle can be invoked not only from a CLI terminal but also via the **Tooling
API** (used by IDEs like IntelliJ, Eclipse, and custom tooling). Tooling API
clients have no real terminal — they use `FallbackConsoleMetaData.NOT_ATTACHED`
which reports `isStdOutATerminal() = false`.

Currently the daemon **cannot distinguish** whether it was invoked by a CLI
client or a Tooling API client. The `Build` command sent from client to daemon
does not carry any terminal-availability flag. If a build script sets
`fullTerminal = true`, the daemon would attempt to allocate a PTY and send
`InteractiveProcessStarted` to a client that has no terminal to put into raw
mode — this would fail or hang.

### Solution: `ClientTerminalInfo` in the build request

Add a `ClientTerminalInfo clientTerminalInfo` property to the build request
parameters sent from the client to the daemon. This groups all
terminal-related client state into a single serializable type:

```java
/**
 * Describes the client's terminal state at build start.
 * Sent from client to daemon as part of the build request parameters.
 */
public class ClientTerminalInfo implements Serializable {
    private final boolean attached;
    private final int cols;
    private final int rows;

    /** No terminal available (Tooling API, piped/redirected CLI). */
    public static final ClientTerminalInfo NOT_ATTACHED =
        new ClientTerminalInfo(false, 0, 0);

    public ClientTerminalInfo(boolean attached, int cols, int rows) {
        this.attached = attached;
        this.cols = cols;
        this.rows = rows;
    }

    /** Whether the client has a real terminal attached. */
    public boolean isAttached() { return attached; }

    /** Initial terminal width (columns). 0 if not attached. */
    public int getCols() { return cols; }

    /** Initial terminal height (rows). 0 if not attached. */
    public int getRows() { return rows; }
}
```

- **CLI client** (`DaemonClient`): Creates a `ClientTerminalInfo` with
  `attached = true` when `ConsoleMetaData.isStdOutATerminal()` returns `true`,
  plus the terminal dimensions from `TerminalOutput.getTerminalSize()`.
  Sets `NOT_ATTACHED` when piped or redirected.
- **Tooling API client**: Always sets `ClientTerminalInfo.NOT_ATTACHED`
  (inherits the `FallbackConsoleMetaData.NOT_ATTACHED` behavior — no real
  terminal is available).

On the **daemon side**, when `ExecHandleRunner` encounters `fullTerminal = true`:

1. Check `clientTerminalInfo.isAttached()` from the build request parameters.
2. If `false`, **silently fall back to pipe-based execution** — no PTY
   allocation, no `InteractiveProcessStarted` message, no warning. The process
   runs normally as if `fullTerminal` were not set.
3. If `true`, proceed with the normal PTY launch path, using
   `clientTerminalInfo.getCols()` / `clientTerminalInfo.getRows()` as the
   initial PTY dimensions.

This means build scripts can unconditionally set `fullTerminal = true` and the
behavior adapts automatically: PTY when run from a terminal, pipes when run
from an IDE or CI.

### Key classes

| Class | Role |
|-------|------|
| `ClientTerminalInfo` | **NEW** — groups `attached`, `cols`, `rows` into a single serializable type on `BuildActionParameters` |
| `LoggingBridgingBuildActionExecuter` | Already handles Tooling API console config with manual `isColorOutput()` flag — similar pattern |
| `ProviderConnection` | Tooling API entry point, constructs build parameters |
| `DaemonClient` | CLI entry point, constructs `Build` command — reads `ConsoleMetaData` |
| `Build` / `BuildParameters` | Daemon protocol command — carries `ClientTerminalInfo` |

---

## Terminal Environment Variables

### The problem

The Gradle daemon is a long-running background process. Its own environment
does not contain the client terminal's environment variables. When a forked
process runs with `fullTerminal = true`, TUI libraries (JLine, ncurses,
Lanterna) need terminal-related environment variables to detect capabilities
— color depth, terminal emulator features, unicode support, etc. Without
these variables, the forked process has a PTY (`isatty() = true`) but cannot
determine **what kind** of terminal it is connected to, resulting in degraded
rendering (e.g., falling back to basic VT100 or 8 colors).

### Solution: forward terminal environment from client to daemon

The client sends its terminal-related environment variables to the daemon as
part of the build request parameters (alongside `clientHasTerminal`). The
daemon injects them into the forked process's environment when
`fullTerminal = true`.

#### Initial terminal dimensions

The client also queries its terminal size at build start via
`TerminalOutput.getTerminalSize()` and includes the dimensions in
`ClientTerminalInfo` (see "Tooling API" section). These are used as the
initial PTY dimensions when starting an interactive process (see Step 8).

After the client receives `InteractiveProcessStarted` and enters raw mode,
it re-queries the terminal size and sends a `TerminalResize` message if the
dimensions have changed since build start. This corrects any drift (e.g.,
the user resized their terminal between `gradle ...` invocation and the
interactive task actually running). See Step 9 for details.

This avoids a synchronous round-trip at PTY launch time: the daemon already
has dimensions from `ClientTerminalInfo` and can start the PTY immediately
after acquiring the terminal lease. Any subsequent resize is handled
asynchronously via `TerminalResize`.

When `clientTerminalInfo.isAttached()` is `false`, dimensions default to `0`
and are unused.

#### Variables to forward

| Variable | Purpose | Example values |
|----------|---------|----------------|
| `TERM` | Terminal type for terminfo/termcap lookup. **Critical** — without it, curses-based apps cannot determine capabilities. | `xterm-256color`, `screen-256color` |
| `COLORTERM` | Color capability hint. Apps use this to detect truecolor (24-bit) support. | `truecolor`, `24bit` |
| `TERM_PROGRAM` | Terminal emulator name. Apps use this for emulator-specific feature detection. | `iTerm.app`, `WezTerm`, `vscode` |
| `TERM_PROGRAM_VERSION` | Terminal emulator version. Used alongside `TERM_PROGRAM` for version-gated features. | `3.5.2` |
| `NO_COLOR` | Disables color output (see no-color.org). Must be respected. | (any value — presence is the signal) |
| `FORCE_COLOR` | Forces color output even when not detected. | `1`, `true` |
| `COLUMNS` / `LINES` | Initial terminal dimensions. Some apps read these at startup before querying `TIOCGWINSZ`. | `120` / `40` |
| `VTE_VERSION` | VTE widget version (GNOME Terminal, Tilix, etc.). Apps detect truecolor and OSC support from this. | `6800`, `7200` |
| `WT_SESSION` | Windows Terminal session ID. Presence signals Windows Terminal features (truecolor, hyperlinks). | GUID string |
| `KONSOLE_VERSION` | KDE Konsole version. Feature detection for Konsole-specific capabilities. | `230400` |
| `ITERM_SESSION_ID` | iTerm2 session ID. Enables iTerm2-specific features (inline images, tmux integration). | `w0t0p0:...` |
| `KITTY_WINDOW_ID` | Kitty terminal window ID. Presence enables Kitty-specific features (graphics protocol, keyboard protocol). | `1` |
| `WEZTERM_PANE` | WezTerm pane ID. Presence enables WezTerm-specific features (image protocol, hyperlinks). | `0` |
| `TERMINAL_EMULATOR` | Set by JetBrains IDEs in their embedded terminal. | `JetBrains-JediTerm` |

#### How it works

`BuildActionParameters.getEnvVariables()` already contains the client's
full environment (filtered through `Jvm.getInheritableEnvironmentVariables()`
and serialized from client to daemon via `DefaultBuildActionParameters`).
No additional field is needed on the build request — the terminal variables
are already available on the daemon side.

When `ExecHandleRunner` launches a PTY process with `fullTerminal = true`:

1. Extract the terminal-related variables from
   `buildActionParameters.getEnvVariables()` using a fixed allowlist
   (the variables listed above).
2. **Merge** them into the child process environment using `putIfAbsent` —
   start with the environment configured on the `ExecSpec` (user-specified
   environment variables and the inherited daemon environment), then overlay
   the client terminal variables only for keys not explicitly set by the
   user. This ensures that if a build script explicitly sets
   `environment 'TERM', 'dumb'`, the user's intent is preserved.

When `clientHasTerminal = false` (Tooling API, piped CLI), no merging
occurs — the process runs with its normal environment.

```java
private static final Set<String> TERMINAL_ENV_VARS = Set.of(
    "TERM", "COLORTERM",
    "TERM_PROGRAM", "TERM_PROGRAM_VERSION",
    "NO_COLOR", "FORCE_COLOR",
    "COLUMNS", "LINES",
    "VTE_VERSION", "WT_SESSION",
    "KONSOLE_VERSION", "ITERM_SESSION_ID",
    "KITTY_WINDOW_ID", "WEZTERM_PANE",
    "TERMINAL_EMULATOR"
);
```

---

## Implementation Steps

### Step 1 — Add `fullTerminal` property to the public API

#### `BaseExecSpec.java`

```
platforms/core-runtime/process-services-api/src/main/java/org/gradle/process/BaseExecSpec.java
```

Add:

```java
/**
 * Whether the process should be connected interactively to the terminal.
 *
 * <p>When {@code true}, Gradle allocates a pseudo-terminal (PTY) for the
 * child process so that TUI applications (e.g., those using JLine or
 * ncurses) can render correctly and accept input.  Gradle's rich console
 * (status bar, progress) is suspended while the interactive process runs,
 * and raw terminal I/O is relayed between the user's terminal and the
 * child via the daemon protocol.</p>
 *
 * <p>If PTY support is not available on the current OS, Gradle falls back
 * to the normal pipe-based execution and logs a warning.</p>
 *
 * <p>Defaults to {@code false}.</p>
 *
 * @since 9.5
 */
@Incubating
Property<Boolean> getFullTerminal();
```

This is in the `org.gradle.process` package, which is part of the public API
(matches `PublicApi.includes` pattern `org/gradle/process/**`).

Since `BaseExecSpec` is the common ancestor of both `ExecSpec` and
`JavaExecSpec`, this single addition covers all process execution entry points:
- `Exec` / `JavaExec` tasks
- `ExecOperations.exec()` / `ExecOperations.javaexec()` (plugin/task injection)
- `ExecFactory.exec()` / `ExecFactory.javaexec()` (internal)

The property must also be:
- Added to `DefaultExecSpec` and `DefaultJavaExecSpec` (internal impls)
- Delegated from `AbstractExecTask` and `JavaExec`
- Copied in `DefaultExecSpec.copyTo()` and `DefaultJavaExecSpec.copyTo()`

#### Why `Property<Boolean>` and not `boolean`

Using a `Property` follows the lazy-properties pattern being adopted across the
Gradle codebase (see the `@ToBeReplacedByLazyProperty` annotations on existing
fields).  It also allows convention values.

### Step 2 — Thread `fullTerminal` through the exec handle builder

#### `ClientExecHandleBuilder.java` / `DefaultClientExecHandleBuilder.java`

```
process-services/.../ClientExecHandleBuilder.java
process-services/.../DefaultClientExecHandleBuilder.java
```

Add a `setFullTerminal(boolean)` method to the builder interface.  In the
implementation, when `fullTerminal = true`:

1. Set `inputHandler = DEFAULT_STDIN` — stdin will come from the PTY, not
   forwarded.
2. Set `streamsHandler = null` — stdout/stderr will be handled by the PTY
   streams handler, not the normal forwarders.
3. Set a new boolean field `fullTerminal = true` that is passed to
   `DefaultExecHandle`.

#### `DefaultExecHandle.java`

```
process-services/.../DefaultExecHandle.java
```

Add an `fullTerminal` field.  Expose it via `ProcessSettings` so that
`ExecHandleRunner` can read it.

### Step 3 — Copy `fullTerminal` in action factories

#### `DefaultExecAction.java` / `DefaultJavaExecAction.java`

```
process-services/.../DefaultExecAction.java
process-services/.../DefaultJavaExecAction.java
```

In `execute()`, after copying the spec to the exec handle builder, also set:

```java
builder.setFullTerminal(getFullTerminal().getOrElse(false));
```

This ensures the `fullTerminal` flag propagates regardless of the entry point:
- **Task path**: `AbstractExecTask.exec()` → `DefaultExecAction.execute()`
- **Programmatic path**: `ExecOperations.exec(action)` →
  `DefaultExecActionFactory.exec(action)` → creates `DefaultExecAction`,
  applies the user's `Action<ExecSpec>` callback (which may set
  `fullTerminal = true`), then calls `execute()`.

No changes needed to `ExecOperations`, `DefaultExecOperations`, or
`DefaultExecActionFactory` — they pass through to the action's `execute()`
which already reads the spec.

### Step 4 — Provide `PtyProcessLauncher` via `NativeServices`

```
native/.../NativeServices.java
```

Add a new provider method, following the existing `createProcessLauncher()`
pattern:

```java
@Provides
protected PtyProcessLauncher createPtyProcessLauncher() {
    if (useNativeIntegrations) {
        try {
            return nativeIntegration.get(PtyProcessLauncher.class);
        } catch (NativeIntegrationUnavailableException e) {
            LOGGER.debug("PTY process launcher is not available. Full terminal mode will fall back to pipes.");
        }
    }
    return null;  // null signals unavailability to callers
}
```

Callers check for `null` or use `isAvailable()` on the returned launcher.

### Step 5 — Terminal lease resource lock

```
process-services/.../interactive/TerminalLease.java   — NEW
```

A build-session-scoped exclusive resource lock that interactive processes must
hold while they own the terminal:

```java
/**
 * An exclusive lock representing ownership of the user's terminal.
 *
 * <p>Only one interactive process may hold the terminal lease at a time.
 * While the lease is held, the rich console is suspended and raw terminal
 * I/O is relayed to the PTY process.  Other interactive processes release
 * their worker lease (via {@code WorkerLeaseService.blocking()}) and wait
 * silently until the lease is released.</p>
 */
@ServiceScope(Scope.BuildSession.class)
public class TerminalLease {
    private final ExclusiveAccessResourceLock lock;
    private final ResourceLockCoordinationService coordinationService;

    public TerminalLease(ResourceLockCoordinationService coordinationService) {
        this.coordinationService = coordinationService;
        this.lock = new ExclusiveAccessResourceLock("terminal", coordinationService, ...);
    }

    /**
     * Acquires the terminal lease, blocking until available.
     *
     * <p>Callers should wrap this in {@code WorkerLeaseService.blocking()}
     * to release their worker lease while waiting, allowing other tasks to
     * use the freed slot.</p>
     */
    public void acquire() {
        coordinationService.withStateLock(lock::tryLock);
    }

    public void release() {
        coordinationService.withStateLock(lock::unlock);
    }

    public boolean isHeld() {
        return lock.isLocked();
    }
}
```

### Step 6 — New daemon protocol messages

```
daemon-protocol/.../protocol/ForwardPtyOutput.java          — NEW
daemon-protocol/.../protocol/TerminalResize.java             — NEW
daemon-protocol/.../protocol/InteractiveProcessStarted.java  — NEW
daemon-protocol/.../protocol/InteractiveProcessEnded.java    — NEW
```

#### `ForwardPtyOutput`

Carries raw bytes from the child's stdout (via PTY) from daemon to client.
Unlike `OutputMessage` which wraps `OutputEvent`s (styled text events that go
through the logging pipeline), this carries raw terminal bytes that must be
written directly to the client's terminal.

```java
public class ForwardPtyOutput extends Message implements Serializable {
    private final byte[] bytes;
    private final int offset;
    private final int length;
    private final boolean stderr;  // false = stdout (PTY), true = stderr (pipe)
}
```

#### `TerminalResize`

Carries terminal dimensions from client to daemon when the client terminal is
resized (SIGWINCH):

```java
public class TerminalResize extends InputMessage implements Serializable {
    private final int cols;
    private final int rows;
}
```

#### `InteractiveProcessStarted` / `InteractiveProcessEnded`

Signal the client to enter/exit raw terminal mode:

```java
public class InteractiveProcessStarted extends Message implements Serializable {
    // Sent by daemon when an interactive process is about to start
}

public class InteractiveProcessEnded extends Message implements Serializable {
    // Sent by daemon when the interactive process has exited
}
```

Register all in `DaemonMessageSerializer`.

### Step 7 — Daemon-side PTY streams handler

```
process-services/.../streams/PtyStreamsHandler.java   — NEW
```

A new `StreamsHandler` implementation that manages a `PtyProcess` instead of
a `java.lang.Process`:

```java
public class PtyStreamsHandler implements StreamsHandler {
    private final PtyProcess ptyProcess;
    private final OutputEventListener outputListener; // sends ForwardPtyOutput

    @Override
    public void connectStreams(Process process, String processName, Executor executor) {
        // Not used — PtyProcess is not a java.lang.Process
        throw new UnsupportedOperationException("Use connectPtyStreams instead");
    }

    public void connectPtyStreams(PtyProcess ptyProcess, Executor executor) {
        // Set up reader threads for:
        // 1. ptyProcess.getInputStream() → ForwardPtyOutput messages (stdout via PTY)
        // 2. ptyProcess.getErrorStream() → ForwardPtyOutput messages with stderr=true
    }

    @Override
    public void start() {
        // Start reader threads
    }

    @Override
    public void stop() {
        // Wait for reader threads to complete
    }

    // Called when ForwardInput arrives from client:
    public void onInput(byte[] bytes) {
        ptyProcess.getOutputStream().write(bytes);
        ptyProcess.getOutputStream().flush();
    }

    // Called when TerminalResize arrives from client:
    public void onResize(int cols, int rows) {
        ptyProcess.resize(cols, rows);
    }
}
```

### Step 8 — Interactive `ExecHandleRunner` for PTY launch

```
process-services/.../ExecHandleRunner.java
```

When `fullTerminal = true`, `ExecHandleRunner` uses a completely different
process launching path with terminal lease acquisition:

```java
if (execHandle.isFullTerminal()) {
    PtyProcessLauncher ptyLauncher = ... ; // from NativeServices
    if (ptyLauncher == null || !ptyLauncher.isAvailable()) {
        // Fallback: log warning, use normal ProcessBuilder path
        LOGGER.warn("PTY allocation is not available. TUI may not render correctly.");
        ProcessBuilder processBuilder = processBuilderFactory.createProcessBuilder(execHandle);
        Process process = processLauncher.start(processBuilder);
        streamsHandler.connectStreams(process, execHandle.getDisplayName(), executor);
        this.process = process;
    } else {
        // Acquire terminal lease — release worker lease while waiting so
        // other tasks can use the freed slot
        workerLeaseService.blocking(() -> {
            terminalLease.acquire();
        });
        try {
            // Notify client to enter raw mode
            connection.dispatch(new InteractiveProcessStarted());

            // PTY launch — initial dimensions come from ClientTerminalInfo
            // (queried by the client at build start). The client will send a
            // TerminalResize message after entering raw mode if the terminal
            // was resized between build start and now.
            ClientTerminalInfo termInfo = buildActionParameters.getClientTerminalInfo();
            int initialCols = termInfo.getCols();
            int initialRows = termInfo.getRows();
            PtyProcess ptyProcess = ptyLauncher.start(
                execHandle.getCommandWithArguments(),
                execHandle.getEffectiveEnvironment(),
                execHandle.getDirectory(),
                initialCols > 0 ? initialCols : 80,
                initialRows > 0 ? initialRows : 24
            );
            ptyStreamsHandler.connectPtyStreams(ptyProcess, executor);
            ptyStreamsHandler.start();
            int exitValue = ptyProcess.waitFor();
            ptyStreamsHandler.stop();
            completed(exitValue);
        } finally {
            // Notify client to exit raw mode
            connection.dispatch(new InteractiveProcessEnded());
            terminalLease.release();
        }
    }
}
```

This bypasses `ProcessBuilder` and `processLauncher.start()` entirely when
a PTY is available.

### Step 9 — Client-side raw terminal mode

#### `DaemonClientInputForwarder.java`

```
client-services/.../DaemonClientInputForwarder.java
```

Add a new forwarding mode for interactive processes.  The existing modes are:

- `readAndForwardStdin()` — reads chars via `InputStreamReader` into `char[]`
  buffer and wraps in `ForwardInput`.
- `readAndForwardText()` — reads lines via `BufferedReader` for user prompts.

Add a third mode:

- `readAndForwardRawBytes()` — reads raw bytes directly from
  `TerminalInput.getInputStream()` (already in raw mode) and wraps in
  `ForwardInput`.

The key difference: this mode reads from the native terminal input stream
(which delivers raw bytes including escape sequences for arrow keys, Ctrl
sequences, etc.) rather than from `System.in` through a `Reader`.

The client enters this mode when it receives `InteractiveProcessStarted`
from the daemon.

#### Terminal raw mode management

Before starting raw forwarding:
1. Call `TerminalInput.rawMode()` to disable line editing and echo.
2. Re-query the terminal dimensions via `TerminalOutput.getTerminalSize()`.
3. If the dimensions differ from what was sent in `ClientTerminalInfo`
   (`cols` / `rows`), send a `TerminalResize`
   message to the daemon so the PTY is resized before the child process reads
   its initial terminal size. This handles the case where the user resized
   their terminal between the `gradle` invocation and the interactive task
   actually starting.

After `InteractiveProcessEnded` is received:
1. Call `TerminalInput.reset()` to restore normal terminal mode.

These calls use the existing native-platform API — no new native code needed.

#### SIGWINCH handling

On POSIX, install a `SIGWINCH` handler (via `sun.misc.Signal` or native-platform
extension) that:
1. Queries new terminal size via `TerminalOutput.getTerminalSize()`.
2. Sends a `TerminalResize` message to the daemon.

On Windows, terminal resize is detected by polling
`TerminalOutput.getTerminalSize()` periodically (e.g., every 500ms) or by
monitoring console events.

### Step 10 — Client-side raw output rendering

When the client receives `ForwardPtyOutput` messages from the daemon, it
must write the raw bytes **directly to the real terminal**, bypassing the
logging pipeline entirely.

In `DaemonClient.monitorBuild()`:

```java
// Existing code handles OutputMessage:
if (message instanceof OutputMessage) {
    outputEventListener.onOutput(((OutputMessage) message).getEvent());
}

// Add handling for ForwardPtyOutput:
if (message instanceof ForwardPtyOutput) {
    ForwardPtyOutput ptyOutput = (ForwardPtyOutput) message;
    if (ptyOutput.isStderr()) {
        originalStdErr.write(ptyOutput.getBytes(), ptyOutput.getOffset(), ptyOutput.getLength());
        originalStdErr.flush();
    } else {
        originalStdOut.write(ptyOutput.getBytes(), ptyOutput.getOffset(), ptyOutput.getLength());
        originalStdOut.flush();
    }
}

// Handle interactive process lifecycle:
if (message instanceof InteractiveProcessStarted) {
    enterRawTerminalMode();
}
if (message instanceof InteractiveProcessEnded) {
    exitRawTerminalMode();
}
```

The `originalStdOut` / `originalStdErr` are the pre-capture streams obtained
from `OutputEventRenderer.getOriginalStdOut()` / `getOriginalStdErr()`.

### Step 11 — Suspend the rich console

When an interactive process is about to run, Gradle's rich console must be
suspended so it doesn't overwrite the TUI output with status bar redraws.

#### Use existing `AbstractUserInputRenderer` pattern

The user-input rendering already pauses output and queues events:

```java
// AbstractUserInputRenderer.java
private boolean paused;
private final List<OutputEvent> eventQueue = new ArrayList<>();

// When UserInputRequestEvent arrives:
paused = true;
startInput();  // hides progress area

// While paused, all other events go to eventQueue

// When UserInputResumeEvent arrives:
paused = false;
finishInput();  // shows progress area
replayEvents(); // forwards queued events
```

For interactive processes, introduce the same mechanism with new event types.

#### Implementation

Add new event types:

```
logging/.../events/InteractiveProcessStartEvent.java    — NEW
logging/.../events/InteractiveProcessEndEvent.java      — NEW
```

These events flow through the `OutputEventListener` chain.  When
`InteractiveProcessStartEvent` is received:

1. Set `paused = true`.
2. Hide the build progress area (`setVisible(false)`).
3. Flush the console.

When `InteractiveProcessEndEvent` is received:

1. Set `paused = false`.
2. Show the build progress area (`setVisible(true)`).
3. Flush the console.
4. Replay all queued events.

These events are dispatched on the **client side** when
`InteractiveProcessStarted` / `InteractiveProcessEnded` protocol messages are
received (in `DaemonClient.monitorBuild()`), via the `outputEventListener`.

### Step 12 — Wire daemon-side input routing

On the daemon side, `ForwardClientInput` registers a `StdinHandler` with
`DefaultDaemonConnection.onStdin()`. The `StdinQueue` dispatches incoming
`InputMessage`s to this handler. Currently, `ForwardInput` bytes go to
`ClientInputForwarder.StdInStream` which replaces `System.in`.

For interactive processes, the `StdinHandler` must route `ForwardInput` bytes
to the PTY master instead:

```java
// In ForwardClientInput or a new InteractivePtyForwarder:
stdinHandler = new StdinHandler() {
    @Override
    public void onInput(ForwardInput input) {
        if (activePtyStreamsHandler != null) {
            activePtyStreamsHandler.onInput(input.getBytes());
        } else {
            // Default: write to System.in replacement
            stdInStream.write(input.getBytes());
        }
    }

    @Override
    public void onEndOfInput() {
        if (activePtyStreamsHandler != null) {
            activePtyStreamsHandler.close();
        } else {
            stdInStream.close();
        }
    }
};
```

The `TerminalResize` messages also need routing. Since `TerminalResize` extends
`InputMessage`, it arrives via the `StdinQueue`:

```java
// In the StdinHandler or a dedicated handler in DefaultDaemonConnection:
if (message instanceof TerminalResize) {
    TerminalResize resize = (TerminalResize) message;
    activePtyStreamsHandler.onResize(resize.getCols(), resize.getRows());
}
```

The `activePtyStreamsHandler` is set when the interactive process starts and
cleared when it ends.

### Step 13 — No-op `StreamsHandler` for the interactive path

```
process-services/.../streams/InteractiveStreamsHandler.java  — NEW
```

A trivial `StreamsHandler` implementation used as the "normal" streams handler
slot in `DefaultExecHandle` when `fullTerminal = true`.  Since the real I/O is
handled by `PtyStreamsHandler`, this prevents the composite handler from trying
to connect standard pipes:

```java
public class InteractiveStreamsHandler implements StreamsHandler {
    @Override public void connectStreams(Process process, String processName, Executor executor) { }
    @Override public void start() { }
    @Override public void removeStartupContext() { }
    @Override public void disconnect() { }
    @Override public void stop() { }
}
```

### Step 14 — `ClientTerminalInfo` on `BuildActionParameters`

#### `ClientTerminalInfo.java` — NEW

```
daemon-protocol/.../ClientTerminalInfo.java
```

A simple serializable value type grouping all terminal-related client state
(see the class definition in the "Tooling API" section above).

#### `BuildActionParameters.java` / `DefaultBuildActionParameters.java`

```
daemon-protocol/.../BuildActionParameters.java
daemon-protocol/.../DefaultBuildActionParameters.java
```

Add a `ClientTerminalInfo getClientTerminalInfo()` property.
`DefaultBuildActionParameters` stores and serializes it alongside the
existing fields. The client environment variables (used for terminal
capability detection) are already available via `getEnvVariables()` — no
duplication needed.

#### CLI client — `DaemonClient.java`

```
client-services/.../DaemonClient.java
```

When constructing `BuildActionParameters`:

```java
ClientTerminalInfo terminalInfo;
if (consoleMetaData != null && consoleMetaData.isStdOutATerminal()) {
    TerminalSize size = terminalOutput.getTerminalSize();
    terminalInfo = new ClientTerminalInfo(true, size.getCols(), size.getRows());
} else {
    terminalInfo = ClientTerminalInfo.NOT_ATTACHED;
}
// Pass terminalInfo to DefaultBuildActionParameters constructor
```

#### Tooling API client — `ProviderConnection.java`

```
launcher/.../tooling/internal/provider/ProviderConnection.java
```

When constructing build action parameters for a Tooling API invocation,
pass `ClientTerminalInfo.NOT_ATTACHED`. This is implicit since the Tooling
API uses `FallbackConsoleMetaData.NOT_ATTACHED` which has no real terminal,
but making it explicit ensures correctness.

#### Daemon side — `ExecHandleRunner.java`

In the PTY launch path (Step 8), add a check before terminal lease
acquisition. `BuildActionParameters` is already available in the daemon's
execution context:

```java
if (execHandle.isFullTerminal()) {
    ClientTerminalInfo termInfo =
        buildActionParameters.getClientTerminalInfo();
    if (!termInfo.isAttached()) {
        // Tooling API or piped client — fall back to normal pipes silently
        ProcessBuilder processBuilder = processBuilderFactory.createProcessBuilder(execHandle);
        Process process = processLauncher.start(processBuilder);
        streamsHandler.connectStreams(process, execHandle.getDisplayName(), executor);
        this.process = process;
    } else if (ptyLauncher == null || !ptyLauncher.isAvailable()) {
        // PTY not available — fall back with warning
        ...
    } else {
        // Full PTY path — merge client terminal env vars before launch.
        // Terminal env vars are extracted from the already-available
        // BuildActionParameters.getEnvVariables() using an allowlist.
        Map<String, String> effectiveEnv =
            new LinkedHashMap<>(execHandle.getEffectiveEnvironment());
        Map<String, String> clientEnv =
            buildActionParameters.getEnvVariables();
        for (String key : TERMINAL_ENV_VARS) {
            String value = clientEnv.get(key);
            if (value != null) {
                effectiveEnv.putIfAbsent(key, value);
            }
        }
        ...
    }
}
```

### Step 15 — Tests

#### Integration tests

```
platforms/jvm/language-java/src/integTest/groovy/org/gradle/integtests/ExecInteractiveIntegrationTest.groovy  — NEW
```

Concurrent-execution tests use the `BlockingHttpServer` fixture (from
`internal-distribution-testing`) to prove tasks actually run in parallel.
Each concurrent task lives in its own **subproject** so that `--parallel`
enables cross-project parallelism.  Tasks use `ExecOperations` (the
injectable service) rather than `Exec` task types, exercising the
programmatic API path.

Following the patterns in `ParallelTaskExecutionIntegrationTest` and
`ParallelProjectExecutionIntegrationTest`:

```groovy
class ExecInteractiveIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    BlockingHttpServer blockingServer = new BlockingHttpServer()

    def setup() {
        blockingServer.start()
    }

    // --- Basic PTY tests ---

    def "interactive exec task sees a terminal"() {
        given:
        buildFile << """
            tasks.register('runInteractive', Exec) {
                commandLine '/bin/sh', '-c', 'test -t 0 && test -t 1 && echo TTY || echo PIPE'
                fullTerminal = true
            }
        """

        when:
        succeeds 'runInteractive'

        then:
        output.contains('TTY')
    }

    def "interactive exec falls back to pipes when PTY unavailable"() {
        // Mock or configure to simulate PTY unavailability
        // Verify warning is logged and process still runs
    }

    def "interactive exec forwards terminal resize"() {
        // Start a long-running interactive process
        // Resize terminal
        // Verify child sees new size
    }

    def "non-interactive exec still works normally"() {
        // Verify no regression for default fullTerminal=false
    }

    def "interactive and custom standardOutput are mutually exclusive"() {
        given:
        buildFile << """
            tasks.register('runBad', Exec) {
                commandLine 'echo', 'hello'
                fullTerminal = true
                standardOutput = new ByteArrayOutputStream()
            }
        """

        when:
        fails 'runBad'

        then:
        failure.assertHasCause('Cannot set standardOutput when fullTerminal is true')
    }

    def "interactive process via ExecOperations sees a terminal"() {
        given:
        buildFile << """
            import javax.inject.Inject

            abstract class InteractiveTask extends DefaultTask {
                @Inject abstract ExecOperations getExecOps()

                @TaskAction
                void run() {
                    execOps.exec {
                        commandLine '/bin/sh', '-c', 'test -t 0 && test -t 1 && echo TTY || echo PIPE'
                        fullTerminal = true
                    }
                }
            }
            tasks.register('runInteractive', InteractiveTask)
        """

        when:
        succeeds 'runInteractive'

        then:
        output.contains('TTY')
    }

    // --- Tooling API fallback ---

    def "fullTerminal falls back to pipes via Tooling API with only regular output events"() {
        given:
        buildFile << """
            tasks.register('runInteractive', Exec) {
                commandLine '/bin/sh', '-c', 'test -t 0 && echo TTY || echo PIPE'
                fullTerminal = true
            }
        """

        when:
        def stdoutCapture = new ByteArrayOutputStream()
        def stderrCapture = new ByteArrayOutputStream()
        def connector = GradleConnector.newConnector()
            .forProjectDirectory(testDirectory)
        def connection = connector.connect()
        try {
            connection.newBuild()
                .forTasks('runInteractive')
                .setStandardOutput(stdoutCapture)
                .setStandardError(stderrCapture)
                .run()
        } finally {
            connection.close()
        }

        then:
        // Child sees pipes, not a terminal — daemon fell back silently
        stdoutCapture.toString().contains('PIPE')
        // All output arrived via regular OutputMessage events (through
        // setStandardOutput), not via ForwardPtyOutput raw writes.
        // No InteractiveProcessStarted/Ended were sent — the Tooling API
        // client never entered raw mode and the rich console was never
        // suspended.
        !stdoutCapture.toString().contains('TTY')
    }

    // --- Concurrent execution with BlockingHttpServer ---

    def "interactive and regular tasks in separate subprojects run concurrently"() {
        given:
        settingsFile << """
            include 'interactive', 'regular'
        """

        // Both subprojects define a task that uses ExecOperations to call
        // the blocking server. The server expects both calls concurrently,
        // proving parallel execution.
        file('interactive/build.gradle') << """
            import javax.inject.Inject

            abstract class InteractiveExecTask extends DefaultTask {
                @Inject abstract ExecOperations getExecOps()

                @TaskAction
                void run() {
                    execOps.exec {
                        commandLine '/bin/sh', '-c', '${blockingServer.callFromBuild(":interactive:work")} && echo INTERACTIVE'
                        fullTerminal = true
                    }
                }
            }
            tasks.register('work', InteractiveExecTask)
        """

        file('regular/build.gradle') << """
            import javax.inject.Inject

            abstract class RegularExecTask extends DefaultTask {
                @Inject abstract ExecOperations getExecOps()

                @TaskAction
                void run() {
                    execOps.exec {
                        commandLine '/bin/sh', '-c', '${blockingServer.callFromBuild(":regular:work")}'
                    }
                }
            }
            tasks.register('work', RegularExecTask)
        """

        // The regular task runs concurrently while the interactive task
        // holds the terminal — its output is buffered until the interactive
        // task releases the lease.
        blockingServer.expectConcurrent(":interactive:work", ":regular:work")

        when:
        succeeds ':interactive:work', ':regular:work', '--parallel'

        then:
        output.contains('INTERACTIVE')
    }

    def "two interactive tasks in separate subprojects are serialized by terminal lease"() {
        given:
        settingsFile << """
            include 'a', 'b'
        """

        // Both subprojects use ExecOperations with fullTerminal = true.
        ['a', 'b'].each { proj ->
            file("${proj}/build.gradle") << """
                import javax.inject.Inject

                abstract class InteractiveExecTask extends DefaultTask {
                    @Inject abstract ExecOperations getExecOps()

                    @TaskAction
                    void run() {
                        execOps.exec {
                            commandLine '/bin/sh', '-c', '${blockingServer.callFromBuild(":${proj}:work")}'
                            fullTerminal = true
                        }
                    }
                }
                tasks.register('work', InteractiveExecTask)
            """
        }

        // The server expects the two calls sequentially — NOT concurrently.
        // The terminal lease serializes the two interactive tasks even
        // though --parallel is enabled and they are in different subprojects.
        blockingServer.expect(":a:work")
        blockingServer.expect(":b:work")

        when:
        succeeds ':a:work', ':b:work', '--parallel'

        then:
        noExceptionThrown()
    }

    def "second interactive task releases worker lease while waiting for terminal"() {
        given:
        settingsFile << """
            include 'interactive1', 'interactive2', 'regular'
        """

        file('interactive1/build.gradle') << """
            import javax.inject.Inject

            abstract class InteractiveExecTask extends DefaultTask {
                @Inject abstract ExecOperations getExecOps()

                @TaskAction
                void run() {
                    execOps.exec {
                        commandLine '/bin/sh', '-c', '${blockingServer.callFromBuild(":interactive1:work")}'
                        fullTerminal = true
                    }
                }
            }
            tasks.register('work', InteractiveExecTask)
        """

        file('interactive2/build.gradle') << """
            import javax.inject.Inject

            abstract class InteractiveExecTask extends DefaultTask {
                @Inject abstract ExecOperations getExecOps()

                @TaskAction
                void run() {
                    execOps.exec {
                        commandLine '/bin/sh', '-c', '${blockingServer.callFromBuild(":interactive2:work")}'
                        fullTerminal = true
                    }
                }
            }
            tasks.register('work', InteractiveExecTask)
        """

        // Regular task in its own subproject — uses ExecOperations too
        file('regular/build.gradle') << """
            import javax.inject.Inject

            abstract class RegularExecTask extends DefaultTask {
                @Inject abstract ExecOperations getExecOps()

                @TaskAction
                void run() {
                    execOps.exec {
                        commandLine '/bin/sh', '-c', '${blockingServer.callFromBuild(":regular:work")}'
                    }
                }
            }
            tasks.register('work', RegularExecTask)
        """

        // With --max-workers=2: interactive1 grabs terminal lease + worker
        // lease. interactive2 tries terminal lease, blocks, releases its
        // worker lease. The freed slot lets :regular:work run concurrently
        // with :interactive1:work.
        blockingServer.expectConcurrent(":interactive1:work", ":regular:work")
        // After interactive1 finishes, interactive2 acquires terminal lease
        blockingServer.expect(":interactive2:work")

        when:
        executer.withArguments('--parallel', '--max-workers=2')
        succeeds ':interactive1:work', ':interactive2:work', ':regular:work'

        then:
        noExceptionThrown()
    }
}
```

Run with:
```
./gradlew :language-java:forkingIntegTest --tests '*ExecInteractiveIntegrationTest*'
./gradlew :language-java:configCacheIntegTest --tests '*ExecInteractiveIntegrationTest*'
```

### Step 16 — Public API validation

Since `BaseExecSpec.getFullTerminal()` is a new public API member:

1. Run `./gradlew sanityCheck` — will report the addition.
2. Add the change to `accepted-public-api-changes.json`.
3. Run `./gradlew :architecture-test:sortAcceptedApiChanges`.
4. Re-run `./gradlew sanityCheck` to verify.

Add `@since 9.5` and `@Incubating` annotations (already shown in Step 1).

---

## File Inventory

### New files

| Path | Description |
|------|-------------|
| `process-services/.../interactive/TerminalLease.java` | Build-session-scoped exclusive lock for terminal ownership |
| `process-services/.../streams/PtyStreamsHandler.java` | Manages PTY ↔ protocol relay |
| `process-services/.../streams/InteractiveStreamsHandler.java` | No-op `StreamsHandler` placeholder for interactive mode |
| `daemon-protocol/.../protocol/ForwardPtyOutput.java` | Raw PTY bytes, daemon→client |
| `daemon-protocol/.../protocol/TerminalResize.java` | Terminal dimensions, client→daemon |
| `daemon-protocol/.../protocol/InteractiveProcessStarted.java` | Signal to enter raw mode |
| `daemon-protocol/.../protocol/InteractiveProcessEnded.java` | Signal to exit raw mode |
| `logging/.../events/InteractiveProcessStartEvent.java` | Event to suspend console |
| `logging/.../events/InteractiveProcessEndEvent.java` | Event to resume console |
| `daemon-protocol/.../ClientTerminalInfo.java` | Serializable value type: `attached`, `cols`, `rows` |
| `language-java/src/integTest/.../ExecInteractiveIntegrationTest.groovy` | Integration tests |

### Modified files

| Path | Change |
|------|--------|
| `process-services-api/.../BaseExecSpec.java` | Add `getFullTerminal()` property |
| `process-services/.../DefaultExecSpec.java` | Implement `fullTerminal` property |
| `process-services/.../DefaultJavaExecSpec.java` | Implement `fullTerminal` property |
| `core/.../AbstractExecTask.java` | Delegate `fullTerminal` property |
| `language-java/.../JavaExec.java` | Delegate `fullTerminal` property |
| `process-services/.../ClientExecHandleBuilder.java` | Add `setFullTerminal(boolean)` |
| `process-services/.../DefaultClientExecHandleBuilder.java` | Implement `setFullTerminal`, choose `InteractiveStreamsHandler` |
| `process-services/.../DefaultExecHandle.java` | Add `fullTerminal` field, expose via `ProcessSettings` |
| `process-services/.../ExecHandleRunner.java` | PTY launch path with terminal lease (via `WorkerLeaseService.blocking()`) and fallback |
| `process-services/.../DefaultExecAction.java` | Pass `fullTerminal` to builder |
| `process-services/.../DefaultJavaExecAction.java` | Pass `fullTerminal` to builder |
| `native/.../NativeServices.java` | Add `createPtyProcessLauncher()` provider |
| `daemon-protocol/.../DaemonMessageSerializer.java` | Register new message types |
| `client-services/.../DaemonClientInputForwarder.java` | Add raw byte forwarding mode |
| `client-services/.../DaemonClient.java` | Handle `ForwardPtyOutput`, `InteractiveProcess*` messages |
| `daemon-services/.../ClientInputForwarder.java` | Route input to PTY handler when interactive |
| `launcher/.../server/exec/ForwardClientInput.java` | Support routing `ForwardInput` to PTY and handling `TerminalResize` |
| `launcher/.../server/DefaultDaemonConnection.java` | Route `TerminalResize` messages from `StdinQueue` |
| `logging/.../console/AbstractUserInputRenderer.java` | Handle `InteractiveProcessStart/EndEvent` with existing pause/queue mechanism |
| `daemon-protocol/.../BuildActionParameters.java` | Add `getClientTerminalInfo()` property |
| `daemon-protocol/.../DefaultBuildActionParameters.java` | Implement `getClientTerminalInfo()`, serialize `ClientTerminalInfo` |
| `launcher/.../tooling/internal/provider/ProviderConnection.java` | Pass `ClientTerminalInfo.NOT_ATTACHED` for Tooling API clients |

---

## Risks and Mitigations

| Risk | Mitigation |
|------|-----------|
| Rich console redraws during interactive process | Console events are suspended via `InteractiveProcessStartEvent` using existing `AbstractUserInputRenderer` pause/queue pattern |
| Daemon stdin forwarding is character-oriented | Raw byte mode added to `DaemonClientInputForwarder` |
| Terminal not restored after crash | Use try-finally and shutdown hook to call `TerminalInput.reset()` |
| Configuration cache compatibility | `fullTerminal` is a `Property<Boolean>` — compatible with configuration cache. The property value is serialized, not the streams. |
| Multiple interactive tasks in parallel | Serialized via `TerminalLease` exclusive lock (build-session-scoped). Second task releases its worker lease via `WorkerLeaseService.blocking()` and waits silently, freeing the slot for other tasks. |
| Long-running interactive task buffers too much regular output | `AbstractUserInputRenderer`'s event queue has no size limit today. For extremely long interactive sessions, could add overflow-to-file, but this is an edge case — most TUI interactions are bounded. |
| User sets both `fullTerminal=true` and `standardOutput`/`standardInput` | When `fullTerminal=true`, reject custom stream settings with a clear error at task configuration time. |
| PTY unavailable (see RESEARCH_TUI.md Fallback section) | Catch `NativeIntegrationUnavailableException` or check `isAvailable()`, fall back to `ProcessBuilder` with warning |
| Daemon protocol version skew (old client, new daemon or vice versa) | New message types are only sent when an interactive process runs. Older clients/daemons that don't recognize the messages will fail gracefully since full terminal tasks didn't exist before. |
| Tooling API / IDE clients attempt PTY allocation | `ClientTerminalInfo.isAttached()` on `BuildActionParameters` lets the daemon silently fall back to pipe mode. Build scripts can set `fullTerminal = true` unconditionally — behavior adapts automatically. |
| Piped/redirected CLI (e.g., `gradle build \| tee log`) | `ConsoleMetaData.isStdOutATerminal()` returns `false` when stdout is not a terminal, so `ClientTerminalInfo.NOT_ATTACHED` is used and the daemon falls back to pipes — same as Tooling API case. |
| Forked TUI process cannot detect terminal capabilities | Terminal environment variables (`TERM`, `COLORTERM`, etc.) are extracted from the already-available `BuildActionParameters.getEnvVariables()` (which contains the client's environment) using a fixed allowlist, and merged into the child process environment using `putIfAbsent` — user-configured environment on `ExecSpec` takes precedence. |

---

## Implementation Order

1. **Public API**: Add `fullTerminal` to `BaseExecSpec` → `DefaultExecSpec` → `AbstractExecTask`
2. **Builder plumbing**: Thread `fullTerminal` through `ClientExecHandleBuilder` → `DefaultExecHandle`
3. **Action factories**: Pass `fullTerminal` in `DefaultExecAction` / `DefaultJavaExecAction`
4. **NativeServices**: Add `PtyProcessLauncher` provider
5. **Terminal lease**: Create build-session-scoped `TerminalLease` with `ExclusiveAccessResourceLock`
6. **No-op handler**: Create `InteractiveStreamsHandler`
7. **Protocol messages**: `ForwardPtyOutput`, `TerminalResize`, `InteractiveProcessStarted/Ended`
8. **Serializer**: Register new messages in `DaemonMessageSerializer`
9. **`PtyStreamsHandler`**: Implement PTY ↔ protocol relay
10. **`ExecHandleRunner`**: Add PTY launch path with terminal lease acquisition and fallback
11. **Console suspend**: Add `InteractiveProcessStart/EndEvent`, handle in `AbstractUserInputRenderer`
12. **Client raw mode**: Add `readAndForwardRawBytes()` to `DaemonClientInputForwarder`
13. **Client output**: Handle `ForwardPtyOutput` in `DaemonClient.monitorBuild()`
14. **Client terminal management**: Raw mode enter/exit, SIGWINCH handling
15. **Daemon input routing**: Route `ForwardInput` / `TerminalResize` to `PtyStreamsHandler` via `ForwardClientInput`
16. **`ClientTerminalInfo`**: Add `ClientTerminalInfo` type and `getClientTerminalInfo()` to `BuildActionParameters`, set in CLI/Tooling API clients, check in `ExecHandleRunner`. Terminal env vars extracted from existing `BuildActionParameters.getEnvVariables()`
17. **Tests**: Integration tests for daemon mode, including parallel task scenarios and Tooling API fallback
18. **API validation**: `sanityCheck`, `accepted-public-api-changes.json`

