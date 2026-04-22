# Plan: Extending native-platform for PTY Support

This plan describes the changes required in the `native-platform` library to
enable Gradle's `Exec` and `JavaExec` tasks to run TUI applications with a
real pseudo-terminal.

Relative to this repository, the `native-platform` source code can be found
at [../native-platform](../native-platform).

## Background

Gradle uses `native-platform`'s `ProcessLauncher` to fork child processes.
The current implementation delegates to `ProcessBuilder.start()`, which
connects the child's stdin/stdout/stderr via anonymous pipes.  Pipes are not
terminals: `isatty()` returns false, `tcsetattr()` fails, `ioctl(TIOCGWINSZ)`
fails, and applications that use JLine, Lanterna, or raw ANSI cursor control
cannot operate.

The goal is to add a new public API that allocates a pseudo-terminal (PTY)
for a child process so the child sees a real terminal on stdin and stdout,
while keeping stderr on a separate pipe so the caller can distinguish the
two output streams.

### Why separate stderr?

A naive PTY setup connects all three fds (stdin, stdout, stderr) to the same
PTY slave.  This merges stdout and stderr into a single byte stream on the
master side, making them indistinguishable.  Gradle needs to tell them apart:

- `standardOutput` and `errorOutput` are independently configurable on
  `Exec`/`JavaExec` tasks.
- Gradle's logging pipeline routes stdout and stderr to different log levels.
- Users may want to capture stderr (e.g., compiler warnings) separately
  while the TUI renders on stdout.

The design therefore uses the **PTY for stdin+stdout** and a **pipe for
stderr**:

| Child fd | Connected to | `isatty()` | Purpose |
|----------|-------------|-----------|---------|
| 0 (stdin) | PTY slave | `true` | Receives keyboard input from the PTY master |
| 1 (stdout) | PTY slave | `true` | TUI output — terminal escape sequences work |
| 2 (stderr) | Pipe (write end) | `false` | Error/diagnostic output — readable separately |

TUI libraries (JLine, Lanterna, ncurses) detect terminal capabilities by
checking **stdout** (fd 1), not stderr.  Having stderr on a pipe is the norm
even in interactive use (`myapp 2>err.log`) and does not affect TUI rendering.

---

## Scope

### In scope

- POSIX PTY allocation and child-process launching (`openpty` / `fork+exec`)
- POSIX separate stderr pipe (`pipe()`)
- Windows ConPTY allocation and child-process launching (`CreatePseudoConsole`)
- Windows separate stderr pipe
- Terminal size propagation and runtime resize
- New Java public API: `PtyProcess` (with distinct stdout and stderr streams) + `PtyProcessLauncher`
- JNI bindings for the above
- Unit/integration tests

### Out of scope (handled in Gradle, not here)

- Daemon client↔daemon protocol changes
- Rich-console suspend/resume
- The `fullTerminal` property on `Exec` / `JavaExec` tasks

---

## Step 1 — New Public API Interfaces

Create two new interfaces in `net.rubygrapefruit.platform.terminal`.

### `PtyProcess`

Represents a running child process whose stdin and stdout are connected to
the slave side of a PTY, and whose stderr is connected to a separate pipe.
The caller interacts with the master side of the PTY and the read end of the
stderr pipe.

```
net/rubygrapefruit/platform/terminal/PtyProcess.java
```

```java
package net.rubygrapefruit.platform.terminal;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * A child process connected to a pseudo-terminal.
 *
 * <p>The child's stdin and stdout are connected to the slave side of a PTY.
 * The child's stderr is connected to a separate pipe so that the caller can
 * read stdout and stderr independently.</p>
 *
 * <p>From the child's perspective:</p>
 * <ul>
 *   <li>fd 0 (stdin)  — PTY slave ({@code isatty()} returns {@code true})</li>
 *   <li>fd 1 (stdout) — PTY slave ({@code isatty()} returns {@code true})</li>
 *   <li>fd 2 (stderr) — pipe     ({@code isatty()} returns {@code false})</li>
 * </ul>
 *
 * <h3>Thread safety</h3>
 * <p>The following methods are safe to call concurrently with stream
 * reads/writes and with each other: {@link #resize}, {@link #destroy},
 * {@link #isAlive}, {@link #waitFor}. Stream objects returned by
 * {@link #getInputStream}, {@link #getOutputStream}, and
 * {@link #getErrorStream} are <em>not</em> thread-safe — callers must
 * synchronize externally if multiple threads access the same stream.</p>
 *
 * <h3>PTY master read behavior on child exit</h3>
 * <p>When the child exits and the slave side of the PTY is closed, reading
 * from {@link #getInputStream()} returns EOF. On Linux the kernel signals
 * this as {@code EIO} on the master fd. On macOS it typically returns
 * 0 (clean EOF), but {@code EIO} or {@code ENXIO} can also occur depending
 * on timing. The implementation normalizes all of these to standard EOF
 * behavior (returning {@code -1} from {@code read()}) so callers do not
 * see an {@code IOException} for normal child termination.</p>
 */
public interface PtyProcess extends AutoCloseable {

    /**
     * Returns an output stream connected to the master side of the PTY.
     * Writing to this stream delivers bytes to the child's stdin.
     */
    OutputStream getOutputStream();

    /**
     * Returns an input stream connected to the master side of the PTY.
     * Reading from this stream receives bytes written by the child to
     * stdout (fd 1).
     *
     * <p>Note: because a PTY echoes input by default, bytes written via
     * {@link #getOutputStream()} may also appear on this stream unless
     * the child has switched the PTY to raw mode.</p>
     */
    InputStream getInputStream();

    /**
     * Returns an input stream connected to the read end of the stderr pipe.
     * Reading from this stream receives bytes written by the child to
     * stderr (fd 2).
     *
     * <p>This stream is independent of the PTY and does not carry any
     * terminal escape sequences from the TUI.</p>
     */
    InputStream getErrorStream();

    /**
     * Changes the terminal size reported to the child.
     * On POSIX this delivers SIGWINCH to the child process group.
     *
     * @param cols Number of columns (width in characters).
     * @param rows Number of rows (height in characters).
     */
    void resize(int cols, int rows);

    /**
     * Returns the OS process ID of the child.
     */
    long getPid();

    /**
     * Waits for the child process to exit and returns its exit code.
     */
    int waitFor() throws InterruptedException;

    /**
     * Requests the child process to terminate gracefully.
     *
     * <p>On POSIX, sends SIGTERM. On Windows, writes Ctrl+C ({@code \x03})
     * to the PTY input pipe (which ConPTY translates to CTRL_C_EVENT),
     * then waits briefly for exit. If the child does not exit within the
     * grace period, falls back to forceful termination.</p>
     *
     * @see #destroyForcibly()
     */
    void destroy();

    /**
     * Forcefully terminates the child process without giving it a chance
     * to clean up.
     *
     * <p>On POSIX, sends SIGKILL. On Windows, calls TerminateProcess.</p>
     */
    void destroyForcibly();

    /**
     * Returns true if the child process has exited.
     */
    boolean isAlive();

    /**
     * Returns the exit code.  Only valid after the process has exited.
     *
     * @throws IllegalStateException if the process is still running.
     */
    int exitValue();

    /**
     * Closes the PTY master file descriptor, the stderr pipe, and releases
     * native resources.  If the child is still running it is destroyed first.
     * Safe to call multiple times; subsequent calls are no-ops.
     */
    @Override
    void close();
}
```

### `PtyProcessLauncher`

Factory for creating `PtyProcess` instances.  Registered as a
`NativeIntegration` so callers obtain it via `Native.get(PtyProcessLauncher.class)`.

```
net/rubygrapefruit/platform/terminal/PtyProcessLauncher.java
```

```java
package net.rubygrapefruit.platform.terminal;

import net.rubygrapefruit.platform.NativeIntegration;
import net.rubygrapefruit.platform.ThreadSafe;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Launches a child process with stdin+stdout connected to a new PTY
 * and stderr connected to a separate pipe.
 *
 * <p>PTY support depends on OS-level APIs that may not be available on all
 * systems.  Callers should check {@link #isAvailable()} before calling
 * {@link #start} if they intend to fall back gracefully.</p>
 */
@ThreadSafe
public interface PtyProcessLauncher extends NativeIntegration {

    /**
     * Returns {@code true} if the current operating system supports
     * PTY allocation.
     *
     * <p>On POSIX systems this verifies that {@code openpty(3)} works
     * (attempts a test allocation and immediately closes both fds).
     * On Windows this checks for the {@code CreatePseudoConsole} API
     * (Windows 10 version 1809 / build 17763 or later).</p>
     *
     * <p>This method never throws.  If it returns {@code false}, calling
     * {@link #start} will fail with a {@link net.rubygrapefruit.platform.NativeException}.</p>
     */
    boolean isAvailable();

    /**
     * Starts a new process.
     *
     * @param command     The command and its arguments.
     * @param environment The environment variables for the child (complete, not merged).
     * @param workingDir  The working directory, or null for the current directory.
     * @param cols        Initial terminal width.
     * @param rows        Initial terminal height.
     * @return A handle to the running child with its PTY master streams.
     * @throws net.rubygrapefruit.platform.NativeException if PTY allocation
     *         fails (e.g., OS does not support the required API).
     */
    @ThreadSafe
    PtyProcess start(List<String> command, Map<String, String> environment,
                     File workingDir, int cols, int rows);
}
```

---

## Step 2 — JNI Declarations

### POSIX: `PosixPtyFunctions.java`

New file alongside the existing `PosixTerminalFunctions.java`:

```
net/rubygrapefruit/platform/internal/jni/PosixPtyFunctions.java
```

```java
package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.internal.FunctionResult;

public class PosixPtyFunctions {

    /**
     * Tests whether openpty(3) works on this system by performing a
     * trial allocation and immediately closing both fds.
     * Returns true if PTY allocation succeeded.
     */
    public static native boolean isPtyAvailable();

    /**
     * Atomically allocates a PTY + stderr pipe, sets initial terminal size,
     * forks, and execs the child process.
     *
     * <p>This is a single JNI call to avoid race windows between PTY
     * allocation and fork. On failure at any stage, all allocated fds are
     * closed internally before returning — no cleanup needed on the Java
     * side.</p>
     *
     * <p>In the child (between fork and exec), only async-signal-safe
     * functions are called: setsid, ioctl, dup2, close, chdir, execve,
     * _exit. No memory allocation or stdio. The child uses {@code execve}
     * (not {@code execvp}) with manual PATH resolution from the passed
     * environment to ensure the child sees the correct PATH, not the
     * parent's.</p>
     *
     * <p>On success, outFds is populated:</p>
     * <ul>
     *   <li>outFds[0] = PTY master fd (for stdin+stdout I/O)</li>
     *   <li>outFds[1] = stderr pipe read fd</li>
     * </ul>
     *
     * <p>The master fd has FD_CLOEXEC set via fcntl after openpty.
     * The stderr pipe is created with pipe2(O_CLOEXEC) for atomic
     * CLOEXEC. Both parent-side fds do not leak into subsequent
     * child processes.</p>
     *
     * <p>All blocking syscalls (waitpid, read, write) retry on EINTR.</p>
     *
     * @param command     Command array (program + arguments).
     * @param environment "KEY=VALUE" array (used for both the child's
     *                    environment and PATH resolution).
     * @param workingDir  Working directory path, or null.
     * @param cols        Initial terminal width.
     * @param rows        Initial terminal height.
     * @param outFds      Output array of length >= 2 (master fd, stderr read fd).
     * @return Child PID (> 0 on success).
     */
    public static native long spawnPty(String[] command,
                                       String[] environment,
                                       String workingDir,
                                       int cols, int rows,
                                       int[] outFds,
                                       FunctionResult result);

    /**
     * Sets the terminal size on a PTY master fd via ioctl(TIOCSWINSZ).
     * This causes the kernel to deliver SIGWINCH to the child's process group.
     */
    public static native void setPtySize(int masterFd, int cols, int rows,
                                         FunctionResult result);

    /**
     * Closes a file descriptor.
     */
    public static native void closeFd(int fd, FunctionResult result);

    /**
     * Waits for a child process by PID.  Returns the exit status.
     *
     * <p>Retries on {@code EINTR} (JVM safepoint signals). Decodes the
     * exit status: {@code WIFEXITED} → {@code WEXITSTATUS},
     * {@code WIFSIGNALED} → {@code 128 + WTERMSIG}.</p>
     *
     * <p>This is a blocking call that pins a native thread. Callers should
     * be aware that this holds a carrier thread for the duration of the
     * child process. Use {@link #tryWaitPid} for non-blocking polling.</p>
     */
    public static native int waitPid(long pid, FunctionResult result);

    /**
     * Sends a signal to a process.
     */
    public static native void killProcess(long pid, int signal,
                                          FunctionResult result);

    /**
     * Non-blocking check: returns true if the child has exited.
     * If true, exitCode[0] is set.
     */
    public static native boolean tryWaitPid(long pid, int[] exitCode,
                                            FunctionResult result);
}
```

### Windows: `WindowsPtyFunctions.java`

```
net/rubygrapefruit/platform/internal/jni/WindowsPtyFunctions.java
```

```java
package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.internal.FunctionResult;

public class WindowsPtyFunctions {

    /**
     * Tests whether the ConPTY API (CreatePseudoConsole) is available on
     * this system.  Uses GetProcAddress to resolve the function from
     * kernel32.dll at runtime — returns true only if the function is present.
     */
    public static native boolean isConPtyAvailable();

    /**
     * Atomically creates a ConPTY, stderr pipe, and spawns a child process.
     *
     * <p>This is a single JNI call that performs the entire ConPTY setup →
     * process creation sequence. Internal pipe handles used by the ConPTY
     * are never exposed to Java — only the handles the caller needs are
     * returned. On failure at any stage, all allocated resources are closed
     * internally before returning.</p>
     *
     * <p>The implementation:</p>
     * <ol>
     *   <li>Creates two pipe pairs for ConPTY I/O.</li>
     *   <li>Calls CreatePseudoConsole (resolved via GetProcAddress).</li>
     *   <li>Closes the internal pipe ends that the ConPTY now owns.</li>
     *   <li>Creates a stderr pipe with non-inheritable read handle.</li>
     *   <li>Builds a STARTUPINFOEX with PROC_THREAD_ATTRIBUTE_PSEUDOCONSOLE
     *       and PROC_THREAD_ATTRIBUTE_HANDLE_LIST (to securely limit which
     *       handles the child inherits).</li>
     *   <li>Calls CreateProcessW with the properly quoted command line and
     *       double-null-terminated Unicode environment block.</li>
     *   <li>If CreateProcessW fails with separate stderr, retries with
     *       merged stderr (ConPTY on all fds).</li>
     *   <li>Closes child-side handles in the parent.</li>
     * </ol>
     *
     * <p>On success, outHandles is populated:</p>
     * <ul>
     *   <li>outHandles[0] = HPCON (pseudo-console handle)</li>
     *   <li>outHandles[1] = ptyReadHandle (master reads child output)</li>
     *   <li>outHandles[2] = ptyWriteHandle (master writes child input)</li>
     *   <li>outHandles[3] = stderrReadHandle (0 if stderr is merged)</li>
     *   <li>outHandles[4] = processHandle</li>
     * </ul>
     *
     * <p>The command array is quoted per Windows CommandLineToArgvW rules.
     * The environment map is encoded as a double-null-terminated UTF-16
     * block with CREATE_UNICODE_ENVIRONMENT.</p>
     *
     * @param command     Command array (program + arguments). Quoted internally.
     * @param environment "KEY=VALUE" array. Encoded as double-null-terminated block.
     * @param workingDir  Working directory path, or null.
     * @param cols        Initial terminal width.
     * @param rows        Initial terminal height.
     * @param outHandles  Output array of length >= 5.
     * @return Child process ID (> 0 on success). outHandles[3] == 0
     *         indicates stderr is merged into the PTY.
     */
    public static native long spawnConPty(String[] command,
                                          String[] environment,
                                          String workingDir,
                                          int cols, int rows,
                                          long[] outHandles,
                                          FunctionResult result);

    /**
     * Resizes an existing pseudo-console.
     *
     * <p><b>Not thread-safe</b> with respect to {@link #closePseudoConsole}.
     * Callers must synchronize resize and close on the Java side.</p>
     */
    public static native void resizePseudoConsole(long hPC, int cols, int rows,
                                                  FunctionResult result);

    /**
     * Waits for a process to exit.  Returns exit code.
     *
     * <p>Uses WaitForSingleObject followed by GetExitCodeProcess. Note that
     * exit code 259 (STILL_ACTIVE) is a valid exit code — always call
     * WaitForSingleObject first, never rely on GetExitCodeProcess alone.</p>
     */
    public static native int waitForProcess(long processHandle,
                                            FunctionResult result);

    /**
     * Non-blocking check: returns true if the process has exited.
     * If true, exitCode[0] is set.
     *
     * <p>Uses WaitForSingleObject(handle, 0) before GetExitCodeProcess
     * to correctly distinguish exit code 259 from STILL_ACTIVE.</p>
     */
    public static native boolean hasProcessExited(long processHandle,
                                                  int[] exitCode,
                                                  FunctionResult result);

    /**
     * Sends Ctrl+C to the child via the PTY input pipe, then waits up to
     * {@code gracePeriodMs} for the child to exit. If the child is still
     * alive after the grace period, calls TerminateProcess.
     *
     * <p>On Windows, TerminateProcess is an unconditional kill (like
     * SIGKILL, not SIGTERM). For ConPTY processes, writing '\x03' to the
     * PTY input pipe translates to CTRL_C_EVENT, which the child can
     * catch and handle gracefully.</p>
     *
     * @param processHandle  The process handle.
     * @param ptyWriteHandle The PTY input pipe (for sending Ctrl+C). May
     *                       be INVALID_HANDLE_VALUE if already closed.
     * @param gracePeriodMs  Milliseconds to wait after Ctrl+C before
     *                       forceful termination. 0 = immediate kill.
     */
    public static native void destroyProcess(long processHandle,
                                             long ptyWriteHandle,
                                             int gracePeriodMs,
                                             FunctionResult result);

    /**
     * Cancels a blocked synchronous I/O operation on the given thread.
     * Used to unblock a ReadFile call before closing the handle.
     *
     * <p>On Windows, CloseHandle does NOT unblock a blocked ReadFile on
     * another thread — this can cause crashes or hangs. Use this function
     * to cancel the blocked I/O first, then close the handle.</p>
     *
     * @param threadHandle Handle to the thread blocked in ReadFile/WriteFile.
     */
    public static native void cancelSynchronousIo(long threadHandle,
                                                  FunctionResult result);

    /**
     * Closes a native handle. Sets the handle to INVALID_HANDLE_VALUE
     * in native code to detect double-close.
     *
     * <p>Validates that the handle is not INVALID_HANDLE_VALUE before
     * calling CloseHandle.</p>
     */
    public static native void closeHandle(long handle, FunctionResult result);

    /**
     * Closes a pseudo-console.
     *
     * <p><b>IMPORTANT: Close ptyReadHandle and ptyWriteHandle BEFORE
     * calling this.</b> ClosePseudoConsole is a blocking call that waits
     * for ConPTY's internal I/O threads to drain. If the parent is still
     * blocked reading from the PTY output pipe, ClosePseudoConsole
     * deadlocks (ConPTY's thread waits for us, we wait for it).</p>
     */
    public static native void closePseudoConsole(long hPC, FunctionResult result);
}
```

---

## Step 3 — Native C/C++ Implementations

### 3a. POSIX — extend `src/main/cpp/posix.cpp`

Add the following functions after the existing terminal functions section.
The implementations use only POSIX APIs available on Linux, macOS, and FreeBSD.

**Headers to add:**

```c
#include <sys/wait.h>   // waitpid
#include <signal.h>     // kill, SIGTERM
#include <fcntl.h>      // O_RDWR, FD_CLOEXEC, F_SETFD

#if defined(__APPLE__)
  #include <util.h>     // openpty on macOS
#else
  #include <pty.h>      // openpty on Linux/FreeBSD
#endif
```

**Functions to implement:**

| JNI Method | Syscalls | Notes |
|-----------|----------|-------|
| `isPtyAvailable` | `openpty(3)`, `close(2)` | Performs a trial `openpty()` call. If it returns 0 (success), close both fds and return `true`. If it fails, return `false`. This handles edge cases like containers without `/dev/pts` mounted, macOS sandbox restrictions blocking PTY allocation, exhaustion of the kernel PTY namespace (`/proc/sys/kernel/pty.max` on Linux), and per-process fd limits (`EMFILE`). |
| `spawnPty` | `openpty(3)`, `pipe2(2)`, `fcntl(FD_CLOEXEC)`, `ioctl(TIOCSWINSZ)`, `fork(2)`, `setsid(2)`, `ioctl(TIOCSCTTY)`, `dup2(2)`, `close(2)`, `chdir(2)`, `execve(2)`, `_exit(2)` | See detailed description below. |
| `setPtySize` | `ioctl(masterFd, TIOCSWINSZ, &ws)` | Construct a `struct winsize` with `ws_col` and `ws_row`, apply to master fd. Setting `TIOCSWINSZ` on the master fd updates the slave's `winsize` struct and delivers `SIGWINCH` to the slave's foreground process group — verified to work identically to setting it on the slave on Linux and macOS. Note: `SIGWINCH` is not coalesced by the kernel — rapid resize calls each generate a separate signal. If multiple signals are pending, standard POSIX semantics apply: only one is delivered. The child must call `ioctl(TIOCGWINSZ)` after receiving the signal to get the current size. PTY-based `SIGWINCH` delivery is PID-namespace-safe (the kernel uses the terminal's session, not PID translation). |
| `closeFd` | `close(2)` | Closes a file descriptor. **On Linux, `close()` is NOT retried on `EINTR`** — the fd is already closed even when `EINTR` is returned (a well-known Linux kernel quirk). Retrying would risk closing a different fd that was allocated in the meantime by another thread. On macOS and FreeBSD, `EINTR` from `close()` means the fd may still be open, but in practice this only occurs with `SO_LINGER` sockets, not PTY/pipe fds. The safest portable approach: do not retry `close()` on `EINTR` on any platform. Log `EBADF` as a bug indicator (double-close or fd reuse race). |
| `waitPid` | `waitpid(pid, &status, 0)` | Blocking wait with `EINTR` retry loop. Decodes exit status: `WIFEXITED(status)` → `WEXITSTATUS(status)`, `WIFSIGNALED(status)` → `128 + WTERMSIG(status)`. If `waitpid` returns `-1` with `errno == ECHILD` (child was auto-reaped because `SIGCHLD` is `SIG_IGN` in some JVM configurations), report a clear error via `FunctionResult` rather than returning garbage. |
| `killProcess` | `kill(pid, signal)` | Signal is typically SIGTERM (15). |
| `tryWaitPid` | `waitpid(pid, &status, WNOHANG)` | Non-blocking. Returns true if child exited. Same `WIFEXITED`/`WIFSIGNALED` decoding and `ECHILD` handling as `waitPid`. |

#### `spawnPty` — atomic PTY allocation + fork/exec

This is a **single JNI function** that performs the entire PTY-allocation →
fork → exec sequence. Consolidating these steps avoids race windows where
fds are allocated but no child process exists to use them, and avoids
fragile multi-step error handling on the Java side. On failure at any stage,
all resources allocated so far are cleaned up internally before returning.

**Sequence in the parent (before fork):**

1. Call `openpty(&masterFd, &slaveFd, NULL, NULL, NULL)` — pass `NULL`
   for both `termp` and `winp`. The slave starts with the kernel's default
   `termios` (cooked mode with echo, `ONLCR` output translation, etc.).
   Initial terminal size is set separately in step 4.
2. Immediately set `FD_CLOEXEC` on `masterFd` via
   `fcntl(masterFd, F_SETFD, FD_CLOEXEC)`. There is an inherent race
   between `openpty()` returning and this `fcntl()` call — if another
   thread in the JVM calls `fork()+exec()` in that window, the master fd
   leaks into the unrelated child. `openpty()` has no `O_CLOEXEC`
   variant. Since both `openpty()` and `fcntl()` happen within a single
   JNI call (`spawnPty`), the window is between two consecutive syscalls
   in native code — no JVM safepoint can occur between them (the thread
   is in native state). The window is on the order of nanoseconds. The
   leaked fd is a PTY master, not a security-sensitive resource, but it
   would keep the slave-side reference alive and prevent `SIGHUP`
   delivery until the unrelated child exits. On Linux ≥ 4.13 with glibc
   ≥ 2.29, `openpty()` may internally use `ioctl(TIOCGPTPEER)` to
   obtain the slave fd — currently without `O_CLOEXEC`, but future glibc
   versions could add it. Defensively clear `CLOEXEC` on `slaveFd` after
   `openpty()` to guarantee the child can use it.
3. Call `pipe2(stderrPipe, O_CLOEXEC)` to create the stderr pipe with
   `FD_CLOEXEC` set atomically on **both** ends. Then clear `CLOEXEC`
   on the write end (`stderrPipe[1]`) via
   `fcntl(stderrPipe[1], F_SETFD, 0)` because the child needs it.
   Using `pipe2()` instead of `pipe()` + `fcntl()` eliminates the
   `CLOEXEC` race window for the stderr pipe read end. `pipe2()` is
   available on Linux glibc ≥ 2.9 / musl ≥ 0.9.0 (kernel ≥ 2.6.27),
   macOS ≥ 12.0 (Monterey, Darwin 21), and FreeBSD ≥ 10 — all platforms
   native-platform currently targets (macOS 13+, Ubuntu 22+, Alpine 3.20+,
   CentOS Stream 9+, FreeBSD 13+).
   The slave fd must **not** have `CLOEXEC` since the child needs it.
4. Set initial terminal size on `masterFd` via `ioctl(TIOCSWINSZ)`.
5. Call `fork()`. On failure, `fork()` returns `-1` with `errno` set to
   `EAGAIN` (process/thread limit — check `ulimit -u` or cgroup
   `pids.max`) or `ENOMEM` (insufficient memory for page tables).
   `ENOMEM` is common in three scenarios: (a) strict
   `vm.overcommit_memory=2`, (b) **cgroup memory limits** (the most
   common cause on modern CI systems like GitHub Actions, Jenkins on
   Kubernetes — `fork()` in a large JVM requires kernel memory for page
   table copies even with copy-on-write, and tight `memory.max` can
   reject this allocation), and (c) exhaustion of the kernel's
   `vm.max_map_count` (default 65536, can be hit with large JVM heaps
   that have many memory-mapped regions). The error message must include
   `strerror(errno)` so the user sees the actual reason (e.g., "Cannot
   allocate memory") rather than a generic failure. Note: `vfork()` is
   **not** a solution for the `ENOMEM` case — it shares the parent's
   address space and is even more dangerous in a multi-threaded JVM.

**Sequence in the child (between fork and exec):**

Only async-signal-safe functions are called. No `malloc`, no `printf`, no
stdio, no JNI calls. This is critical because the Gradle daemon is a
multi-threaded JVM process — after `fork()`, only the calling thread exists
in the child, and any mutex held by another thread in the parent is
permanently locked in the child.

1. `setsid()` — create a new session (detach from parent's controlling
   terminal). The child is now a session leader with no controlling
   terminal, so no `SIGTTOU`/`SIGTTIN` risk exists at this point.
   `setsid()` should never fail post-fork: the child's PID is fresh and
   cannot equal any existing session/process-group ID. Nevertheless,
   check the return value — if `setsid()` fails (returns `-1`), write a
   diagnostic byte to the stderr pipe write end (still available at this
   point) and call `_exit(126)` to signal a setup failure distinct from
   `_exit(127)` (exec failure). This handles exotic scenarios like
   seccomp-BPF filters blocking `setsid()`.
2. `ioctl(slaveFd, TIOCSCTTY, 0)` — set the slave as the controlling
   terminal of the new session. `setsid()` must come first so the
   process has no controlling terminal. On Linux, the `0` argument means
   "do not steal the terminal from another session" (a non-zero value
   allows root to steal). On macOS/XNU, the argument is ignored entirely
   — the kernel always refuses if the terminal belongs to another
   session (regardless of root/non-root). The effective behavior is the
   same: the freshly-allocated PTY slave has no owning session, so
   `TIOCSCTTY` always succeeds. Check the return value — if it fails
   (`EPERM` in exotic namespace setups, `ENOTTY` if the slave is not a
   terminal device, or seccomp blocking `ioctl`), write a diagnostic
   byte to stderr and `_exit(126)`.
   No terminal-control functions (`tcsetattr`, `tcsetpgrp`) are called
   between `setsid` and `exec`, so there is no risk of
   `SIGTTOU`/`SIGTTIN` being generated.
3. `dup2(slaveFd, 0)` — stdin from PTY slave.
4. `dup2(slaveFd, 1)` — stdout to PTY slave.
5. `dup2(stderrWriteFd, 2)` — stderr to pipe.
6. Close originals if they differ from 0/1/2: `close(slaveFd)`,
   `close(stderrWriteFd)`. In practice, `slaveFd` and `stderrWriteFd`
   are always ≥ 3 because the JVM keeps fds 0–2 open, so the
   `if (slaveFd > 2)` guard is technically redundant — but retain it
   as defensive coding. (The master fd and stderr read fd have
   `FD_CLOEXEC` and will be closed automatically by `execve`.)
7. `chdir(workingDir)` if non-null. `chdir` is not in the POSIX
   async-signal-safe list. On Linux (glibc/musl), it is a thin syscall
   wrapper with no userspace locking. On macOS, `chdir()` goes through
   `libsystem_kernel.dylib` and may involve internal locking in
   `_dirhelper`, but this is safe post-fork because only one thread
   exists in the child — no lock contention is possible. (Apple's own
   `posix_spawn()` calls `chdir` in its post-fork child path,
   confirming they consider it safe.) On FreeBSD, it is a direct
   syscall. Verified safe in practice on all target platforms.
8. Resolve the command and call `execve()`:
   - If `command[0]` contains a `/`, call `execve(command[0], argv, envp)`
     directly (absolute or relative path — no `PATH` search).
   - If `command[0]` does not contain a `/`, iterate over `PATH` entries
     from the **child's** `envp` (not the parent's `environ`),
     constructing `dir/command[0]` for each entry, and try
     `execve(candidate, argv, envp)`. Continue searching on `ENOENT`,
     `EACCES`, `ELOOP`, `ENAMETOOLONG`, `ETXTBSY`, and `ENOTDIR`.
     Stop on `ENOEXEC` (report as error — unlike `execvp`, we do
     **not** fall back to `/bin/sh`) or success. Track whether any
     `EACCES` was seen during the search: if all candidates fail and
     at least one returned `EACCES`, report "Permission denied" rather
     than "No such file or directory" — this distinction is critical
     for user diagnostics. Empty `PATH` entries (e.g., `PATH=:/usr/bin`
     or `PATH=/usr/bin:`) mean "current directory" per POSIX and are
     treated as such.
   - **Why not `execvp`**: `execvp(3)` searches the **parent's**
     `environ` `PATH`, not the passed environment. This would cause the
     child to resolve commands using the Gradle daemon's `PATH` instead
     of the user-configured environment. `execvpe()` (glibc extension)
     is not portable. Manual `PATH` iteration with `execve()` is the
     correct async-signal-safe approach.
   - **Security note**: the caller (Gradle) is responsible for the
     `PATH` in the passed environment. If absolute paths are provided,
     no `PATH` search occurs.
9. If `execve` fails: write the `errno` value as a single byte to the
   stderr pipe write end (fd 2 at this point) for parent-side
   diagnostics, then `_exit(127)` — **not** `exit()`, which would run
   atexit handlers and flush JVM stdio buffers. The parent can read
   this diagnostic byte from the stderr pipe to distinguish "exec
   failed" from "child exited with code 127". Use `_exit(126)` for
   pre-exec setup failures (`setsid`, `TIOCSCTTY`, `dup2`, `chdir`)
   and `_exit(127)` for exec failures, matching the bash convention.

**Sequence in the parent (after fork):**

1. Close `slaveFd` and `stderrPipe[1]` (write end) — the child has its
   own copies.
2. Populate `outFds[0] = masterFd`, `outFds[1] = stderrPipe[0]`.
3. Return child PID.

**Why not `posix_spawn()`?** `posix_spawn` is the safer alternative to
`fork()` in multi-threaded processes, but it does not support `setsid()` or
`ioctl(TIOCSCTTY)` — both are required to give the child its own session
with the PTY as controlling terminal. Without a controlling terminal,
`SIGWINCH` is not delivered on resize, and TUI libraries that call
`tcgetpgrp()` fail. `fork()+execve()` with only async-signal-safe calls
between them is the correct approach.

#### macOS `fork()` and the Objective-C runtime / libdispatch

On macOS, the Objective-C runtime and `libdispatch` (GCD) mark themselves as
fork-unsafe via `pthread_atfork` handlers. After `fork()`, if the child calls
any ObjC or GCD function before `exec()`, it will crash or deadlock. Since
this plan calls `execve()` immediately after `fork()` with **only**
async-signal-safe POSIX calls between them (no ObjC, no CoreFoundation, no
`dispatch_*`), this is safe. **This is a hard constraint**: any future change
that adds code between `fork()` and `execve()` must not touch ObjC,
CoreFoundation, Foundation, or `libdispatch` — doing so will crash on macOS.

macOS 10.12+ also sets `OBJC_DISABLE_NONPOINTER_ISA` after fork, and
`libdispatch` aborts if you try to use GCD after fork without exec. These are
not concerns for this implementation but define a "do not touch" perimeter.

#### `SIGCHLD` interaction with the JVM

Since this plan uses `fork()` directly (not `ProcessBuilder`), the JVM's
internal process reaper thread is unaware of our child. This is fine:
`waitpid()` in JNI will correctly reap the child. However, if `SIGCHLD` is
set to `SIG_IGN` (which causes automatic child reaping by the kernel),
`waitpid()` may return `-1` with `errno == ECHILD`. The `waitPid` and
`tryWaitPid` implementations must handle this case — treat `ECHILD` as
"child already exited" rather than an error. In practice, the HotSpot JVM
does **not** set `SIGCHLD` to `SIG_IGN` (it installs its own handler), but
other JVM implementations or user code might.

On macOS, an additional `ECHILD` source exists: if any loaded framework
uses `NSTask` (Foundation's process management), `NSTask` monitors child
processes via `dispatch_source_create(DISPATCH_SOURCE_TYPE_PROC, ...)`.
Modern `NSTask` implementations use `waitpid` with specific PIDs and
should not interfere, but older versions or third-party libraries that
call `wait()` (reap any child) could race with our `waitpid()`. The
`ECHILD` handling covers this scenario.

#### `EINTR` handling in blocking syscalls

JNI calls can be interrupted by JVM-internal signals (e.g., safepoint
signals using `SIGUSR1` or `SIGUSR2` on Linux). All blocking syscalls —
`waitpid()`, `read()`, `write()` — can return `-1` with `errno = EINTR`.
All implementations must retry on `EINTR`:

```c
do {
    ret = waitpid(pid, &status, 0);
} while (ret == -1 && errno == EINTR);
```

The same pattern applies to `nativeRead` and `nativeWrite` — a `read()`
or `write()` interrupted by a signal must be retried, not reported as an
error.

#### Exit status decoding

`waitpid()` returns a raw status word. The exit code must be decoded
correctly:

```c
if (WIFEXITED(status))   return WEXITSTATUS(status);   // normal exit
if (WIFSIGNALED(status)) return 128 + WTERMSIG(status); // killed by signal
```

`WEXITSTATUS(status)` is **only defined when `WIFEXITED(status)` is true**.
Calling it on a signal-killed process returns garbage. This is critical
because `destroy()` sends `SIGTERM` — without the `WIFSIGNALED` check,
`waitFor()` after `destroy()` would return an incorrect exit code. The
`128 + signal` convention matches bash and `java.lang.Process` behavior.

#### Terminal attributes initialization

When a PTY is allocated via `openpty(... NULL, NULL)`, the slave starts
with the kernel's default `termios` settings. This means:

- **Cooked mode** — line buffering, canonical input processing.
- **Echo enabled** — bytes written to the master (stdin input) are echoed
  back on the master read stream (stdout).
- **`ONLCR` enabled** — output `\n` is translated to `\r\n`.

The daemon has no real terminal whose `termios` to copy. This is correct —
TUI applications (JLine, ncurses, Lanterna) switch the PTY to raw mode
themselves as part of their initialization. Attempting to pre-configure
`termios` would be counterproductive.

**Cooked-mode artifacts**: before the child switches to raw mode, any
output it produces will have `\r` inserted before `\n`, and any bytes
written to the master will be echoed. Gradle's output routing must be
aware that initial output may contain `\r\n` line endings. This is
standard PTY behavior and matches what a real terminal sees.

**Platform-specific considerations:**

- **macOS**: `openpty` is in `<util.h>`. On macOS, `openpty` is part of
  the `libutil` sublibrary within `libSystem.B.dylib`, which the compiler
  driver auto-links — no explicit `-lutil` flag is needed. This is
  because macOS bundles `libutil`, `libpthread`, `libm`, etc. into the
  umbrella `libSystem`. This matters if cross-compiling or using
  non-standard toolchains that don't auto-link `libSystem` components.
  `TIOCSCTTY` takes an int arg (0 by convention, though XNU ignores it —
  see child setup notes above). Already handled by the existing
  `#ifdef __APPLE__` pattern in posix.cpp.
- **macOS code signing and AMFI**: On macOS 10.15+, binaries typically
  run with the hardened runtime (required for notarization). When
  `execve()` targets a binary with the `com.apple.quarantine` extended
  attribute (e.g., downloaded from the internet and not cleared), macOS's
  AMFI (Apple Mobile File Integrity) may block execution with `EPERM` or
  `EACCES`. The error message from `spawnPty` should suggest checking
  quarantine attributes (`xattr -l <binary>`) when `execve()` fails
  with these errno values.
- **macOS sandbox (enterprise MDM)**: If Gradle runs under a macOS
  sandbox profile (possible in enterprise MDM-managed environments),
  `openpty()` requires access to `/dev/ptmx`. The App Sandbox does not
  allow PTY allocation by default. `isPtyAvailable()` correctly catches
  this — the error/warning message should mention sandbox restrictions
  as a possible cause, not just "containers without `/dev/pts`."
- **macOS file descriptor limits**: macOS has historically low default
  `ulimit -n` (256 on some configurations, 10240 on recent versions).
  In a long-running Gradle daemon, `openpty()` can fail with `EMFILE`.
  The error message should distinguish `EMFILE`/`ENFILE` and suggest
  checking `ulimit -n`.
- **macOS Rosetta 2 (Apple Silicon)**: When a Rosetta 2 (x86_64) JVM
  calls `fork()+execve()` on an ARM64-only binary, macOS handles the
  architecture transition transparently (the kernel re-execs). The PID
  from `fork()` remains valid for `waitpid()`, but `getPid()` may not
  match the PID visible in `ps` for the final running command. This is
  a minor observability issue, not a functional bug.
- **Linux (glibc)**: `openpty` is in `<pty.h>` and requires linking
  `-lutil`. The build must add this linker flag for Linux glibc targets.
- **Linux (musl/Alpine)**: `openpty` is in `<pty.h>` but is part of libc
  directly — `libutil` does not exist. The linker flag must be conditional:
  add `-lutil` only on glibc-based systems, not on musl. See Step 6 for
  the concrete build solution.
- **Linux kernel PTY limits**: The maximum number of PTYs is controlled by
  `/proc/sys/kernel/pty.max` (default 4096) and
  `/proc/sys/kernel/pty.reserve` (default 1024). In containerized
  environments (Docker, Kubernetes), the `devpts` mount may have its own
  `max=<N>` option. `openpty()` fails with `ENOSPC` or `EAGAIN` when the
  limit is reached. The error message should suggest checking
  `pty.max` / container devpts configuration.
- **Linux seccomp-BPF filters**: In hardened environments (Docker with
  custom seccomp profiles, Firejail, etc.), `openpty()` (which internally
  calls `posix_openpt` → `ioctl(TIOCGPTPEER)` on glibc ≥ 2.29),
  `setsid()`, or `ioctl(TIOCSCTTY)` may be blocked. `isPtyAvailable()`
  catches `openpty` failures; post-fork failures (`setsid`, `TIOCSCTTY`)
  are reported via the diagnostic `_exit(126)` mechanism.
- **FreeBSD**: `openpty` is in `<libutil.h>` and requires `-lutil`.
  Use the existing FreeBSD platform detection in the build.

#### Session leader behavioral difference

Because the child calls `setsid()`, it becomes a **session leader**. This
is a behavioral difference from `ProcessBuilder`-launched processes (which
inherit the parent's session and are not session leaders):

- When the session leader exits, the kernel sends `SIGHUP` to all
  processes in the session's foreground process group. If the child
  `fork()`s grandchildren that don't call `setsid()` themselves, those
  grandchildren receive `SIGHUP` when the child exits. This is standard
  terminal behavior and is desirable for TUI applications (it ensures
  cleanup), but callers should be aware of this difference when
  `fullTerminal` mode is enabled.
- If the Gradle daemon dies (including `SIGKILL`), the kernel closes the
  PTY master fd, which causes the slave to see a hangup condition. The
  kernel delivers `SIGHUP` to the child's session. Most programs exit on
  `SIGHUP` by default — this is the real backstop for orphan cleanup, not
  shutdown hooks (see resource management below).

#### PTY buffer size and deadlock avoidance

PTY buffers on Linux are typically **4 KB** (much smaller than pipe buffers
which are 64 KB). If the child writes to stdout faster than the parent
reads, the child blocks when the PTY buffer fills. Similarly, if the child
writes to stderr faster than the parent reads the stderr pipe, the child
blocks. Callers **must** drain both `getInputStream()` and
`getErrorStream()` concurrently to avoid deadlock — the same requirement as
`ProcessBuilder`, but more acute because the PTY buffer is smaller.

#### Write-path error handling

When the child exits and the slave side of the PTY is closed, `write()`
on the master fd returns `-1` with `errno = EIO` or `ENXIO`. Both errno
values can occur on both Linux and macOS depending on timing and whether
the slave was explicitly closed vs. the child process exited. The JNI
`nativeWrite` implementation must catch **both `EIO` and `ENXIO`** on the
PTY master on **all platforms** and throw a specific exception (e.g.,
"process has exited") rather than a generic `IOException` with a confusing
`EIO` message. This affects any thread pumping stdin into the PTY master
while the child terminates.

### 3b. Windows — extend `src/main/cpp/win.cpp`

Add ConPTY functions after the existing console functions section.

#### `WINDOWS_MIN` compile-time guard

The native-platform build produces two sets of Windows binaries per
architecture:

| Target name | Variant | `WINDOWS_MIN` defined | Purpose |
|-------------|---------|----------------------|---------|
| `windows_amd64` | full | No | Modern Windows (Vista+) — symlinks, Cygwin/MSYS detection |
| `windows_amd64_min` | minimal | Yes | Older Windows — no symlinks, no Cygwin/MSYS |
| (same pattern for `i386` and `aarch64`) | | | |

The `WINDOWS_MIN` preprocessor macro is defined automatically by
`JniPlugin.java` when the binary name contains `_min`.  Existing code in
`win.cpp` already uses `#ifdef WINDOWS_MIN` / `#ifndef WINDOWS_MIN` to
exclude modern APIs (symlink reparse-point detection, Cygwin/MSYS console
detection via `GetFileInformationByHandleEx`).

**All ConPTY function implementations must be wrapped in
`#ifndef WINDOWS_MIN`**.  The `_min` variant gets stub implementations that
return failure or `false`:

```c
#ifndef WINDOWS_MIN

// --- ConPTY type definitions and function pointer resolution ---
// (see "Headers and SDK independence" above for typedefs)

// --- Full ConPTY implementations ---

JNIEXPORT jboolean JNICALL
Java_...WindowsPtyFunctions_isConPtyAvailable(...) {
    return resolve_conpty() ? JNI_TRUE : JNI_FALSE;
}

// spawnConPty: atomic ConPTY + process creation.
// - Creates pipes with CreatePipe, marks parent handles non-inheritable
// - Calls pCreatePseudoConsole (resolved via GetProcAddress)
// - Closes internal ConPTY pipe ends immediately
// - Builds STARTUPINFOEX with PROC_THREAD_ATTRIBUTE_PSEUDOCONSOLE
//   and PROC_THREAD_ATTRIBUTE_HANDLE_LIST (secure handle inheritance)
// - Quotes command line per CommandLineToArgvW rules
// - Encodes environment as double-null-terminated UTF-16 block
// - Calls CreateProcessW with CREATE_UNICODE_ENVIRONMENT
// - On failure with separate stderr, retries with merged stderr
// - On any failure, cleans up all handles before returning

// resizePseudoConsole: calls pResizePseudoConsole with COORD.
// closePseudoConsole: calls pClosePseudoConsole.

#else // WINDOWS_MIN

// --- Minimal stubs: ConPTY is not available ---

JNIEXPORT jboolean JNICALL
Java_...WindowsPtyFunctions_isConPtyAvailable(...) {
    return JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_...WindowsPtyFunctions_spawnConPty(...) {
    mark_failed_with_message(env, "ConPTY is not available in the minimal Windows variant", result);
    return 0;
}

// Similar stubs for resizePseudoConsole, closePseudoConsole.

#endif // WINDOWS_MIN

// --- Functions that work on ALL Windows variants (no ConPTY dependency) ---
// waitForProcess, hasProcessExited, destroyProcess, cancelSynchronousIo,
// closeHandle, nativeRead, nativeWrite
// — these use basic Win32 APIs (WaitForSingleObject, TerminateProcess,
// CancelSynchronousIo, ReadFile, WriteFile, CloseHandle) available on
// all supported Windows versions.
// They are NOT guarded by WINDOWS_MIN.
```

This follows the same pattern as existing code: symlink detection is guarded
by `#ifdef WINDOWS_MIN` (line ~101 of `win.cpp`) and Cygwin/MSYS console
detection is guarded by `#ifndef WINDOWS_MIN` (line ~550 of `win.cpp`).

**The minimal variant's support level does not change.**  No new OS version
requirements are introduced for the `_min` targets.

#### Headers and SDK independence

```c
// Already included: <windows.h>
```

The ConPTY API declarations (`CreatePseudoConsole`, `HPCON`, etc.) are only
available in Windows SDK headers when `_WIN32_WINNT >= 0x0A00` **and** the
SDK is version 10.0.17763.0 or later. To avoid SDK version dependencies,
**manually declare the necessary types and function pointer typedefs** inside
`#ifndef WINDOWS_MIN`:

```c
#ifndef WINDOWS_MIN

// ConPTY types — declared manually to avoid SDK version dependency.
// All ConPTY functions are resolved via GetProcAddress at runtime.
typedef VOID* HPCON;
typedef HRESULT (WINAPI *PFN_CreatePseudoConsole)(COORD, HANDLE, HANDLE, DWORD, HPCON*);
typedef HRESULT (WINAPI *PFN_ResizePseudoConsole)(HPCON, COORD);
typedef VOID    (WINAPI *PFN_ClosePseudoConsole)(HPCON);

static PFN_CreatePseudoConsole pCreatePseudoConsole = NULL;
static PFN_ResizePseudoConsole pResizePseudoConsole = NULL;
static PFN_ClosePseudoConsole pClosePseudoConsole = NULL;

static BOOL resolve_conpty() {
    HMODULE kernel32 = GetModuleHandleW(L"kernel32.dll");
    if (!kernel32) return FALSE;
    pCreatePseudoConsole = (PFN_CreatePseudoConsole)GetProcAddress(kernel32, "CreatePseudoConsole");
    pResizePseudoConsole = (PFN_ResizePseudoConsole)GetProcAddress(kernel32, "ResizePseudoConsole");
    pClosePseudoConsole  = (PFN_ClosePseudoConsole)GetProcAddress(kernel32, "ClosePseudoConsole");
    return pCreatePseudoConsole && pResizePseudoConsole && pClosePseudoConsole;
}

#endif // WINDOWS_MIN
```

This makes the code buildable with **any** Windows SDK version. The
`GetProcAddress` pattern avoids both compile-time and load-time dependencies.

#### Functions to implement (in the `#ifndef WINDOWS_MIN` block)

| JNI Method | Win32 APIs | Notes |
|-----------|-----------|-------|
| `isConPtyAvailable` | `GetModuleHandleW`, `GetProcAddress` | Calls `resolve_conpty()`. Returns `true` if all three function pointers were resolved. Works across all Windows editions. |
| `spawnConPty` | `CreatePipe`, `SetHandleInformation`, `CreatePseudoConsole`, `InitializeProcThreadAttributeList`, `UpdateProcThreadAttribute(PSEUDOCONSOLE + HANDLE_LIST)`, `CreateProcessW`, `CloseHandle` | See detailed description below. |
| `resizePseudoConsole` | `ResizePseudoConsole` | Pass COORD struct with new size. Uses cached function pointer. |
| `closePseudoConsole` | `ClosePseudoConsole` | Closes the HPCON. **Must be called after closing ptyReadHandle and ptyWriteHandle** to avoid deadlock (see below). Uses cached function pointer. |

#### `spawnConPty` — atomic ConPTY setup + process creation

This is a **single JNI function** that performs the entire ConPTY
allocation → process creation sequence. Internal pipe handles used by the
ConPTY never cross the JNI boundary. On failure at any stage, all resources
are cleaned up internally.

**Handle inheritance security:**

The JVM is multi-threaded. Between `CreatePipe` and `CreateProcessW`,
another thread could call `CreateProcess` and accidentally inherit our
pipe handles into an unrelated child. To prevent this:

1. Create all pipes with `CreatePipe(..., NULL)` (inheritable by default).
2. Immediately mark parent-side handles as **non-inheritable** via
   `SetHandleInformation(handle, HANDLE_FLAG_INHERIT, 0)`:
   - `ptyReadHandle` (parent reads child output)
   - `ptyWriteHandle` (parent writes child input)
   - `stderrReadHandle` (parent reads child stderr)
3. Use `PROC_THREAD_ATTRIBUTE_HANDLE_LIST` in the `STARTUPINFOEX` to
   **explicitly list** which handles the child inherits:
   - `stderrWriteHandle` (the only handle the child needs beyond ConPTY)
4. This is the modern secure approach — avoids the historical Windows
   handle inheritance race (cf. JDK-6921885).

**Command-line quoting:**

The Java side passes `String[] command` (tokenized arguments). The JNI
function must join and quote them into a single `lpCommandLine` string
per Windows `CommandLineToArgvW` parsing rules:

- Arguments containing spaces, tabs, or quotes are wrapped in double quotes
- Backslashes before double quotes are escaped (`\"` → `\\\"`)
- Trailing backslashes before the closing quote are doubled

`CreateProcessW` requires `lpCommandLine` to be a **mutable** `LPWSTR` —
the JNI code must copy the Java string into a writable `wchar_t` buffer,
not use `GetStringChars` directly.

**Environment block encoding:**

The Java side passes `String[] environment` (`"KEY=VALUE"` entries). The
JNI function must encode this as a **double-null-terminated UTF-16 block**
(`L"KEY=VALUE\0KEY=VALUE\0\0"`) and pass `CREATE_UNICODE_ENVIRONMENT` to
`CreateProcessW`.

**Separate stderr fallback:**

1. First attempt: `STARTUPINFOEX` with both
   `PROC_THREAD_ATTRIBUTE_PSEUDOCONSOLE` and
   `PROC_THREAD_ATTRIBUTE_HANDLE_LIST` (containing `stderrWriteHandle`),
   plus `STARTF_USESTDHANDLES` with `hStdError = stderrWriteHandle`.
2. If `CreateProcessW` fails: retry without `STARTF_USESTDHANDLES` (stderr
   merged into ConPTY). Set `outHandles[3] = 0` to signal merged stderr.
   Close the now-unused stderr pipe handles.
3. If ConPTY creation itself fails: clean up all handles and report error.

#### `ClosePseudoConsole` ordering — deadlock prevention

`ClosePseudoConsole` is a **blocking call** that waits for ConPTY's internal
I/O threads to drain. If the parent is still blocked in `ReadFile` on the
PTY output pipe when `ClosePseudoConsole` is called, a deadlock occurs:
ConPTY's thread tries to write to the pipe → waits for space → but the
reader (our thread) is waiting for ConPTY to finish.

The `WindowsPtyProcess.close()` must follow this exact order:

1. **Cancel any blocked I/O** on the read/write threads via
   `CancelSynchronousIo(threadHandle)`.
2. **Close `ptyReadHandle` and `ptyWriteHandle`** — this causes ConPTY's
   internal I/O threads to see broken pipes and unblock.
3. **Call `ClosePseudoConsole(hPC)`** — now safe because the pipe ends are
   closed.
4. **Close `processHandle`** and **`stderrReadHandle`**.

#### `ReadFile` / `WriteFile` cancellation on Windows

On Windows, `CloseHandle` does **not** unblock a blocked `ReadFile` on
another thread (unlike POSIX where `close(fd)` unblocks a blocked `read`).
A `ReadFile` on an anonymous pipe blocks until data arrives or the write
end is closed.

The JNI-backed `InputStream` on Windows must support cancellation:

- Store the native thread handle (via `GetCurrentThread()` /
  `DuplicateHandle`) when entering a `ReadFile` call.
- Expose a `cancel()` method that calls
  `CancelSynchronousIo(readerThreadHandle)`.
- `WindowsPtyProcess.close()` calls `cancel()` on the stream before
  closing the handle.

Alternatively, use overlapped I/O (`OVERLAPPED` structures with
`CreateEvent`) and `CancelIoEx` — but this adds complexity. The
`CancelSynchronousIo` approach is simpler for this use case.

#### Functions available on ALL Windows variants (no `WINDOWS_MIN` guard)

| JNI Method | Win32 APIs | Notes |
|-----------|-----------|-------|
| `waitForProcess` | `WaitForSingleObject`, `GetExitCodeProcess` | Blocking wait. Uses `WaitForSingleObject` first — never relies on `GetExitCodeProcess` alone, because exit code 259 (`STILL_ACTIVE`) is a legitimate exit code. |
| `hasProcessExited` | `WaitForSingleObject(handle, 0)`, `GetExitCodeProcess` | Non-blocking (timeout = 0). Same `STILL_ACTIVE` (259) caveat: always checks `WaitForSingleObject` return value before interpreting the exit code. |
| `destroyProcess` | `WriteFile` (Ctrl+C), `WaitForSingleObject`, `TerminateProcess` | Graceful shutdown: writes `\x03` to the PTY input pipe (ConPTY translates to `CTRL_C_EVENT`), waits for grace period, then `TerminateProcess` as last resort. |
| `cancelSynchronousIo` | `CancelSynchronousIo` | Cancels blocked `ReadFile`/`WriteFile` on a thread. Required before `CloseHandle` on pipe handles. |
| `closeHandle` | `CloseHandle` | Validates handle is not `INVALID_HANDLE_VALUE` before calling. |

**Windows stderr note**: Combining ConPTY with a separately-redirected stderr
is **best-effort** — the details are handled inside `spawnConPty` (see
Step 3b). The interaction between `PROC_THREAD_ATTRIBUTE_PSEUDOCONSOLE`
and `STARTF_USESTDHANDLES` is poorly documented by Microsoft. The
pseudo-console takes over all console I/O for the child, and on some
Windows versions setting `hStdError` via `STARTF_USESTDHANDLES` alongside
a pseudo-console attribute is ignored or causes `CreateProcessW` to fail.
Merged stderr is the realistic common case. When `spawnConPty` returns
`outHandles[3] == 0`, stderr is merged and `getErrorStream()` returns an
empty `InputStream`.

The separate-stderr path should be tested against Windows 10 1809, Windows 10
22H2, and Windows 11 23H2+ to determine which versions actually support the
combination.

**ConPTY minimum version**: Windows 10 version 1809 (build 17763). Gradle
officially supports Windows 10 starting from version 1507 (build 10240),
so builds 10240–17134 lack ConPTY entirely.  Even in the non-`_min` variant,
`createPseudoConsole` **must not be linked directly** — it must be resolved
via `GetProcAddress` at runtime so the native library remains loadable on
pre-1809 Windows.  If ConPTY is not available at runtime,
`createPseudoConsole` reports a clear error via `FunctionResult`.

---

## Step 4 — Java Internal Implementations

### `PosixPtyProcess.java`

```
net/rubygrapefruit/platform/internal/PosixPtyProcess.java
```

Internal class implementing `PtyProcess` for POSIX platforms.  Stores:

- `int masterFd` — the PTY master file descriptor (stdin+stdout)
- `int stderrReadFd` — the read end of the stderr pipe
- `long childPid` — returned by `spawnPty`
- JNI-backed `InputStream` for the master fd (stdout)
- JNI-backed `OutputStream` for the master fd (stdin)
- JNI-backed `InputStream` for stderrReadFd (stderr)

#### Stream implementation: JNI-backed I/O

Use thin JNI-backed `InputStream`/`OutputStream` implementations that call
`read(2)`/`write(2)` directly on the raw fd. This is preferred over both
`/dev/fd/<n>` paths and `FileDescriptor` field injection:

- `/dev/fd/<n>` creates a **new file description** via `open()`, which does
  not share flags with the existing fd and has subtly different semantics
  across Linux (symlink to `/proc/self/fd`) and macOS (real filesystem).
  Closing the `FileInputStream` closes the new fd, not the original,
  creating confusing resource management.
- `FileDescriptor` field injection via `Unsafe` requires `--add-opens` on
  Java 21+ and is fragile under module encapsulation.
- JNI-backed streams operate directly on the fd, have no `FileDescriptor`
  dependency, work identically on all POSIX platforms, and use the same
  pattern as the Windows implementation (see below). Adding two small
  native functions (`nativeRead(int fd, byte[], int off, int len)` and
  `nativeWrite(int fd, byte[], int off, int len)`) to `PosixPtyFunctions`
  is straightforward.

Both `nativeRead` and `nativeWrite` must retry on `EINTR` (JVM safepoint
signals can interrupt any blocking syscall). `nativeRead` normalizes both
`EIO` and `ENXIO` to EOF on the read path (see below). `nativeWrite`
catches both `EIO` and `ENXIO` on the PTY master write path on **all
platforms** (both errors can occur on both Linux and macOS depending on
timing) and throws a specific "process has exited" exception rather than
a generic `IOException`.

#### `EIO`/`ENXIO` normalization on child exit (read path)

When the child exits and the slave side of the PTY is closed, `read()` on
the master fd signals EOF differently across platforms and timing:

- **Linux**: returns `-1` with `errno = EIO` (always).
- **macOS**: typically returns `0` (clean EOF), but can return `-1` with
  `errno = EIO` when `read()` is blocked at the moment the slave closes
  (observed on macOS 11+ in terminal emulator implementations). `ENXIO`
  has also been observed in some timing scenarios.

The JNI `nativeRead` implementation must treat **both `EIO` and `ENXIO`**
as EOF on PTY master fds on **all platforms** — return `-1` to Java's
`InputStream.read()`, not throw an `IOException`. This ensures consistent
behavior regardless of platform or timing.

The `nativeRead` implementation must also retry on `EINTR` (see `EINTR`
handling above) before checking for `EIO`/`ENXIO`.

### `WindowsPtyProcess.java`

```
net/rubygrapefruit/platform/internal/WindowsPtyProcess.java
```

Internal class implementing `PtyProcess` for Windows.  Stores:

- `long hPC` — pseudo-console handle
- `long processHandle` — child process handle
- `long pid` — child process ID
- `long ptyReadHandle` — master reads child stdout (from PTY pipe)
- `long ptyWriteHandle` — master writes child stdin (to PTY pipe)
- `long stderrReadHandle` — master reads child stderr (from stderr pipe)
- `boolean stderrMerged` — `true` if the separate-stderr path failed and
  stderr is merged into the PTY (see ConPTY fallback in Step 3b). When
  `true`, `getErrorStream()` returns an empty `InputStream`.
- JNI-backed `InputStream`/`OutputStream` that call `ReadFile`/`WriteFile`
  via JNI on the raw HANDLEs. The `InputStream` implementation stores its
  native thread handle and exposes a `cancel()` method that calls
  `CancelSynchronousIo` — required for safe close (see Step 3b).

All `long` handle fields are set to `INVALID_HANDLE_VALUE` (`-1`) after
being closed, so native code can detect and reject double-close or
use-after-close. Every JNI function validates that the handle is not
`INVALID_HANDLE_VALUE` before use.

### Resource management (both platforms)

- `PtyProcess` extends `AutoCloseable` — callers should use
  try-with-resources or call `close()` explicitly.
- `close()` is idempotent — safe to call multiple times.
- Handle fields are set to `-1` / `INVALID_HANDLE_VALUE` after close so
  native code can detect double-close and use-after-close.
- Shutdown hook registered in the launcher as a **best-effort** cleanup if
  not explicitly closed. JVM shutdown hooks are **not guaranteed to run** —
  they don't execute on `kill -9`, `Runtime.halt()`, or JVM crashes
  (`SIGSEGV`, `SIGABRT`). The real backstop for orphan cleanup on POSIX is
  `SIGHUP`: when the daemon dies, the kernel closes the PTY master fd,
  causing a hangup on the slave. The kernel delivers `SIGHUP` to the
  child's session (because the child is a session leader via `setsid()`).
  Most programs exit on `SIGHUP` by default. Orphaned children are
  reparented to PID 1 (init/systemd), which reaps them — no zombies
  accumulate.

**POSIX `close()` order:**

1. `closeFd(masterFd)` — unblocks any blocked `read()` on another thread.
2. `closeFd(stderrReadFd)`.
3. `destroy()` child if still alive.
4. `waitPid()` to reap.

**Windows `close()` order** (critical for deadlock prevention):

1. Cancel blocked I/O on reader/writer threads via
   `CancelSynchronousIo(threadHandle)`.
2. `CloseHandle(ptyReadHandle)` and `CloseHandle(ptyWriteHandle)` —
   unblocks ConPTY's internal I/O threads.
3. `ClosePseudoConsole(hPC)` — now safe because pipe ends are closed.
4. `CloseHandle(processHandle)`.
5. `CloseHandle(stderrReadHandle)`.

### Thread safety implementation (both platforms)

- `destroy()`, `destroyForcibly()`, `isAlive()`: safe to call concurrently
  with `waitFor()` and with stream reads/writes. On POSIX, `kill` is
  inherently thread-safe.
- `resize()`: on POSIX, `ioctl` is inherently thread-safe. On Windows,
  `ResizePseudoConsole` is **not documented as thread-safe** — the Java
  implementation must synchronize `resize()` with `close()` to prevent
  calling `ResizePseudoConsole` on a closed HPCON.
- `waitFor()`: blocks a native thread. Since the project targets Java 21
  which supports virtual threads, callers should be aware this pins a
  carrier thread. The non-blocking `tryWaitPid` / `hasProcessExited`
  alternatives exist for polling patterns.
- `close()`: uses an `AtomicBoolean` guard to ensure idempotency and
  thread safety. Synchronizes with `resize()` on Windows.

### `PosixPtyProcessLauncher.java`

```
net/rubygrapefruit/platform/internal/PosixPtyProcessLauncher.java
```

```java
package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.internal.jni.PosixPtyFunctions;
import net.rubygrapefruit.platform.terminal.PtyProcess;
import net.rubygrapefruit.platform.terminal.PtyProcessLauncher;

import java.io.File;
import java.util.List;
import java.util.Map;

public class PosixPtyProcessLauncher implements PtyProcessLauncher {

    @Override
    public boolean isAvailable() {
        return PosixPtyFunctions.isPtyAvailable();
    }

    @Override
    public PtyProcess start(List<String> command, Map<String, String> environment,
                            File workingDir, int cols, int rows) {
        FunctionResult result = new FunctionResult();

        // Build env array: ["KEY=VALUE", ...]
        String[] envArray = environment.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .toArray(String[]::new);

        String dir = workingDir != null ? workingDir.getAbsolutePath() : null;

        // Single atomic JNI call: allocate PTY + stderr pipe, fork, exec.
        // On failure, all fds are cleaned up internally — no cleanup needed here.
        int[] outFds = new int[2]; // [0]=masterFd, [1]=stderrReadFd
        long pid = PosixPtyFunctions.spawnPty(
            command.toArray(new String[0]),
            envArray, dir, cols, rows,
            outFds, result);
        if (result.isFailed()) {
            throw new NativeException("Could not spawn PTY process: " + result.getMessage());
        }

        return new PosixPtyProcess(outFds[0], outFds[1], pid);
    }
}
```

### `WindowsPtyProcessLauncher.java`

```
net/rubygrapefruit/platform/internal/WindowsPtyProcessLauncher.java
```

Follows the same pattern as `PosixPtyProcessLauncher` but calls
`WindowsPtyFunctions`:

```java
@Override
public PtyProcess start(List<String> command, Map<String, String> environment,
                        File workingDir, int cols, int rows) {
    FunctionResult result = new FunctionResult();

    // Build env array: ["KEY=VALUE", ...]
    String[] envArray = environment.entrySet().stream()
        .map(e -> e.getKey() + "=" + e.getValue())
        .toArray(String[]::new);

    String dir = workingDir != null ? workingDir.getAbsolutePath() : null;

    // Single atomic JNI call: create ConPTY + stderr pipe, spawn process.
    // Internal ConPTY pipe handles never cross the JNI boundary.
    // On failure, all handles are cleaned up internally.
    long[] outHandles = new long[5];
    // [0]=hPC, [1]=ptyRead, [2]=ptyWrite, [3]=stderrRead (0=merged), [4]=process
    long pid = WindowsPtyFunctions.spawnConPty(
        command.toArray(new String[0]),
        envArray, dir, cols, rows,
        outHandles, result);
    if (result.isFailed()) {
        throw new NativeException("Could not spawn ConPTY process: " + result.getMessage());
    }

    boolean stderrMerged = (outHandles[3] == 0);
    return new WindowsPtyProcess(
        outHandles[0], outHandles[1], outHandles[2],
        outHandles[3], outHandles[4], pid, stderrMerged);
}
```

---

## Step 5 — Registration in `Platform.java`

Add `PtyProcessLauncher` to the platform resolution in `Platform.java`.

In the `Posix.get()` method, add:

```java
if (type.equals(PtyProcessLauncher.class)) {
    return type.cast(new PosixPtyProcessLauncher());
}
```

In the `Windows.get()` method, add:

```java
if (type.equals(PtyProcessLauncher.class)) {
    return type.cast(new WindowsPtyProcessLauncher());
}
```

This follows the existing pattern used for `ProcessLauncher`, `Terminals`, etc.

---

## Step 6 — Build Changes

### `native-platform/build.gradle`

No new native library components are needed.  The PTY functions are compiled
into the existing `nativePlatform` shared library alongside the terminal and
process code, since they use the same `src/main/cpp` source set.

### Linux linker flag

The `openpty` function on **glibc-based** Linux is in `libutil`. On
**musl-based** Linux (Alpine), `openpty` is part of libc directly — `libutil`
does not exist and passing `-lutil` to the linker will fail.

In `buildSrc/.../JniPlugin.java`, in the section that configures
platform-specific linker args for Linux, add `-lutil` **conditionally**:

```java
// Linux targets: link libutil for openpty (glibc only; musl has it in libc)
if (targetPlatform.getOperatingSystem().isLinux()) {
    // Use --as-needed so the linker silently ignores -lutil if the
    // symbols are already resolved (musl). This is the default on most
    // modern Linux toolchains but we make it explicit for safety.
    linkerArgs.addAll(Arrays.asList("-Wl,--as-needed", "-lutil"));
}
```

Alternatively, detect musl vs glibc at build time (e.g., check if the
target is Alpine via the binary name convention) and only add `-lutil` for
glibc targets.

macOS includes `openpty` in `libSystem.B.dylib` (auto-linked via the
`libutil` sublibrary) and does not need an extra linker flag. FreeBSD
includes `openpty` in `libutil` (requires `-lutil`).

### Windows SDK version

For the **non-`_min` variant only**, ensure `_WIN32_WINNT` is defined to at
least `0x0A00` (Windows 10) so that `CreatePseudoConsole` and related API
declarations are available in the headers.  Since the ConPTY code is inside
`#ifndef WINDOWS_MIN`, the `_min` variant does not need this — its
`_WIN32_WINNT` can remain unchanged.  The existing `win.cpp` already includes
`<windows.h>`; verify that the non-`_min` build does not set a lower
`_WIN32_WINNT`.

### JNI headers

No manual action needed.  The `gradlebuild.jni` plugin auto-generates JNI
headers from the Java `native` method declarations using the `-h` compiler
flag.  Adding `PosixPtyFunctions.java` and `WindowsPtyFunctions.java` with
native methods will cause the corresponding `.h` files to be generated into
`build/generated` and included in the C++ compilation.

---

## Step 7 — Tests

### Unit test: `PtyProcessLauncherTest.groovy`

```
src/test/groovy/net/rubygrapefruit/platform/terminal/PtyProcessLauncherTest.groovy
```

Following the existing `ProcessLauncherTest` pattern:

```groovy
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Timeout(30)
class PtyProcessLauncherTest extends NativePlatformSpec {
    final PtyProcessLauncher launcher = getIntegration(PtyProcessLauncher)

    def "can start a child process with a PTY"() {
        given:
        def command = Platform.current().windows
            ? ["cmd.exe", "/c", "echo hello"]
            : ["/bin/sh", "-c", "echo hello"]

        when:
        def pty = launcher.start(command, System.getenv(), null, 80, 24)
        def output = pty.inputStream.text
        def exitCode = pty.waitFor()

        then:
        output.contains("hello")
        exitCode == 0

        cleanup:
        pty?.close()
    }

    @Requires({ !Platform.current().windows })
    def "child sees a terminal on stdin and stdout but not stderr"() {
        given:
        // test -t N exits 0 if fd N is a terminal
        def command = ["/bin/sh", "-c",
            "test -t 0 && test -t 1 && ! test -t 2"]

        when:
        def pty = launcher.start(command, System.getenv(), null, 80, 24)
        // Drain stdout in background to prevent PTY buffer from filling
        // (cooked-mode echo or shell prompts could produce output)
        def executor = Executors.newSingleThreadExecutor()
        executor.submit { pty.inputStream.text }
        def exitCode = pty.waitFor()

        then:
        exitCode == 0

        cleanup:
        executor?.shutdownNow()
        pty?.close()
    }

    @Requires({ !Platform.current().windows })
    def "stdout and stderr are received on separate streams"() {
        given:
        def command = ["/bin/sh", "-c",
            "echo out_message ; echo err_message >&2"]

        when:
        def pty = launcher.start(command, System.getenv(), null, 80, 24)
        // Read both streams concurrently using an ExecutorService to
        // avoid blocking and to get proper Future-based result retrieval
        def executor = Executors.newFixedThreadPool(2)
        def stdoutFuture = executor.submit({ pty.inputStream.text } as java.util.concurrent.Callable<String>)
        def stderrFuture = executor.submit({ pty.errorStream.text } as java.util.concurrent.Callable<String>)
        def exitCode = pty.waitFor()
        def stdout = stdoutFuture.get(10, TimeUnit.SECONDS)
        def stderr = stderrFuture.get(10, TimeUnit.SECONDS)

        then:
        exitCode == 0
        stdout.contains("out_message")
        !stdout.contains("err_message")
        stderr.contains("err_message")
        !stderr.contains("out_message")

        cleanup:
        executor?.shutdownNow()
        pty?.close()
    }

    @Requires({ !Platform.current().windows })
    def "child process sees correct terminal size"() {
        given:
        // `stty size` reads the kernel's winsize struct via ioctl(TIOCGWINSZ)
        // directly — no terminfo dependency, unlike `tput`.
        // Output format: "rows cols" (e.g., "40 120")
        def command = ["/bin/sh", "-c", "stty size"]

        when:
        def env = new HashMap(System.getenv())
        env.put("TERM", "xterm-256color")
        def pty = launcher.start(command, env, null, 120, 40)
        def output = pty.inputStream.text
        def exitCode = pty.waitFor()

        then:
        // stty size outputs "rows cols", e.g., "40 120"
        output.contains("40 120")
        exitCode == 0

        cleanup:
        pty?.close()
    }

    @Requires({ !Platform.current().windows })
    def "can resize PTY"() {
        given:
        // Child signals readiness on stderr (pipe — not garbled by PTY
        // echo/ONLCR), then waits for SIGWINCH and prints new size.
        // Uses `stty size` instead of `tput` to avoid terminfo dependency.
        def script = '''
            trap 'stty size >&2; exit 0' WINCH
            echo READY >&2
            # Block until signal (or timeout)
            sleep 10
        '''
        def command = ["/bin/sh", "-c", script]

        when:
        def env = new HashMap(System.getenv())
        env.put("TERM", "xterm-256color")
        def pty = launcher.start(command, env, null, 80, 24)
        // Drain stdout in background to prevent PTY buffer from filling
        def executor = Executors.newSingleThreadExecutor()
        executor.submit { pty.inputStream.text }
        // Wait for child to be ready — read "READY" from stderr (pipe),
        // which is not subject to PTY echo or ONLCR garbling
        def stderrReader = new BufferedReader(new InputStreamReader(pty.errorStream))
        def readyLine = stderrReader.readLine()
        assert readyLine.contains("READY")
        // Now resize
        pty.resize(132, 50)
        // Read the new size from stderr (pipe) — "rows cols"
        def sizeLine = stderrReader.readLine()
        def exitCode = pty.waitFor()

        then:
        exitCode == 0
        // stty size outputs "rows cols", e.g., "50 132"
        sizeLine.contains("50 132")

        cleanup:
        executor?.shutdownNow()
        pty?.close()
    }

    @Requires({ !Platform.current().windows })
    def "can destroy child process"() {
        given:
        // exec replaces the shell with sleep, so SIGTERM goes directly
        // to the sleep process (no orphaned child lingering until close())
        def command = ["/bin/sh", "-c", "exec sleep 60"]

        when:
        def pty = launcher.start(command, System.getenv(), null, 80, 24)
        // Drain stdout in background to prevent PTY buffer from filling
        def executor = Executors.newSingleThreadExecutor()
        executor.submit { pty.inputStream.text }
        assert pty.alive
        pty.destroy()
        def exitCode = pty.waitFor()

        then:
        !pty.alive
        exitCode != 0

        cleanup:
        executor?.shutdownNow()
        pty?.close()
    }

    @Requires({ !Platform.current().windows })
    def "child sees passed environment variables"() {
        given:
        def command = ["/bin/sh", "-c",
            'echo "TERM=$TERM COLORTERM=$COLORTERM CUSTOM=$MY_CUSTOM_VAR"']

        when:
        def env = new HashMap(System.getenv())
        env.put("TERM", "xterm-256color")
        env.put("COLORTERM", "truecolor")
        env.put("MY_CUSTOM_VAR", "hello_pty")
        def pty = launcher.start(command, env, null, 80, 24)
        def output = pty.inputStream.text
        def exitCode = pty.waitFor()

        then:
        exitCode == 0
        output.contains("TERM=xterm-256color")
        output.contains("COLORTERM=truecolor")
        output.contains("CUSTOM=hello_pty")

        cleanup:
        pty?.close()
    }

    @Requires({ !Platform.current().windows })
    def "child runs in specified working directory"() {
        given:
        // Use pwd -P (physical path, resolves symlinks) and compare against
        // canonicalPath on both sides to avoid symlink mismatches
        // (e.g., macOS /tmp → /private/tmp)
        def workDir = new File(System.getProperty("java.io.tmpdir"))
        def command = ["/bin/sh", "-c", "pwd -P"]

        when:
        def pty = launcher.start(command, System.getenv(), workDir, 80, 24)
        def output = pty.inputStream.text
        def exitCode = pty.waitFor()

        then:
        exitCode == 0
        // Normalize both sides: pwd -P resolves symlinks in the child,
        // canonicalPath resolves symlinks on the Java side. Strip \r
        // from PTY cooked-mode ONLCR output.
        output.replaceAll("\\r", "").trim() == workDir.canonicalPath

        cleanup:
        pty?.close()
    }

    @Requires({ !Platform.current().windows })
    @Timeout(60)
    def "handles large output without deadlock"() {
        given:
        // Generate ~1MB of output through the PTY with periodic newlines
        // so the PTY line discipline flushes regularly (cooked-mode canonical
        // buffer is limited to ~4KB and needs newlines to flush).
        // `yes` outputs "y\n" repeatedly; `head -c` limits to 1MB.
        def command = ["/bin/sh", "-c",
            "yes 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA' | head -c 1048576; exit 0"]

        when:
        def pty = launcher.start(command, System.getenv(), null, 80, 24)
        // Must read output concurrently to avoid blocking the child
        def outputBytes = new ByteArrayOutputStream()
        def readThread = Thread.start {
            byte[] buf = new byte[8192]
            int n
            while ((n = pty.inputStream.read(buf)) != -1) {
                outputBytes.write(buf, 0, n)
            }
        }
        def exitCode = pty.waitFor()
        readThread.join(60000)
        assert !readThread.isAlive() : "Read thread did not complete within timeout"

        then:
        exitCode == 0
        // PTY adds CR before each LF (ONLCR), so output is larger than 1MB.
        // Assert at least 500KB to account for any variance.
        outputBytes.size() > 500_000

        cleanup:
        pty?.close()
    }

    @IgnoreIf({!new File("/proc/self/fd").exists()})
    def "does not leak file descriptors"() {
        given:
        // Linux-specific: /proc/self/fd lists open fds.
        // Not available on macOS/FreeBSD — test is skipped there.
        def countFds = {
            new File("/proc/self/fd").list()?.length ?: -1
        }
        // Check for PTY master fds specifically (symlink to /dev/ptmx or /dev/pts/*)
        def findPtyFds = {
            new File("/proc/self/fd").listFiles()?.findAll { f ->
                try {
                    def target = java.nio.file.Files.readSymbolicLink(f.toPath()).toString()
                    target.contains("/dev/ptmx") || target.contains("/dev/pts/")
                } catch (ignored) { false }
            }?.collect { it.name } ?: []
        }
        // Warmup: run a few iterations to let JVM settle (JIT, class loading, etc.)
        5.times {
            def pty = launcher.start(
                ["/bin/sh", "-c", "exit 0"],
                System.getenv(), null, 80, 24)
            pty.waitFor()
            pty.close()
        }
        def initialPtyFds = findPtyFds()
        def initialFds = countFds()

        when:
        // Start and close 50 PTY processes in a loop
        50.times {
            def pty = launcher.start(
                ["/bin/sh", "-c", "exit 0"],
                System.getenv(), null, 80, 24)
            pty.waitFor()
            pty.close()
        }
        def finalFds = countFds()
        def finalPtyFds = findPtyFds()

        then:
        // Primary check: no new PTY master fds leaked
        // (new PTY fds not present in the initial set)
        (finalPtyFds - initialPtyFds).isEmpty()
        // Secondary check: overall fd count hasn't grown significantly.
        // Allow generous slack for JVM GC, JIT, profiling opening transient fds.
        initialFds == -1 || (finalFds - initialFds) < 20

        cleanup:
        // nothing
    }

    @Requires({ !Platform.current().windows })
    def "read from master returns EOF after child exits"() {
        given:
        def command = ["/bin/sh", "-c", "echo done"]

        when:
        def pty = launcher.start(command, System.getenv(), null, 80, 24)
        def output = pty.inputStream.text // reads until EOF
        def exitCode = pty.waitFor()

        then:
        exitCode == 0
        output.contains("done")
        // If EIO normalization were broken, pty.inputStream.text would
        // have thrown an IOException — reaching this point proves it works.

        cleanup:
        pty?.close()
    }
}
```

POSIX-only tests are annotated with `@Requires({ !Platform.current().windows })`.
Windows-specific equivalents follow below.

### Windows-specific tests

These tests use `cmd.exe` or PowerShell and are annotated with
`@Requires({ Platform.current().windows })`. They mirror the POSIX tests
above for key scenarios.

```groovy
    @Requires({ Platform.current().windows })
    def "stdout and stderr are received on separate streams [Windows]"() {
        given:
        def command = ["cmd.exe", "/c", "echo out_message & echo err_message >&2"]

        when:
        def pty = launcher.start(command, System.getenv(), null, 80, 24)
        def executor = Executors.newFixedThreadPool(2)
        def stdoutFuture = executor.submit({ pty.inputStream.text } as java.util.concurrent.Callable<String>)
        def stderrFuture = executor.submit({ pty.errorStream.text } as java.util.concurrent.Callable<String>)
        def exitCode = pty.waitFor()
        def stdout = stdoutFuture.get(10, TimeUnit.SECONDS)
        def stderr = stderrFuture.get(10, TimeUnit.SECONDS)

        then:
        exitCode == 0
        stdout.contains("out_message")
        // stderr may be empty if ConPTY merged stderr — that's acceptable
        // (separate stderr on Windows is best-effort)

        cleanup:
        executor?.shutdownNow()
        pty?.close()
    }

    @Requires({ Platform.current().windows })
    def "child sees passed environment variables [Windows]"() {
        given:
        def command = ["cmd.exe", "/c", "echo TERM=%TERM% CUSTOM=%MY_CUSTOM_VAR%"]

        when:
        def env = new HashMap(System.getenv())
        env.put("TERM", "xterm-256color")
        env.put("MY_CUSTOM_VAR", "hello_pty")
        def pty = launcher.start(command, env, null, 80, 24)
        def output = pty.inputStream.text
        def exitCode = pty.waitFor()

        then:
        exitCode == 0
        output.contains("TERM=xterm-256color")
        output.contains("CUSTOM=hello_pty")

        cleanup:
        pty?.close()
    }

    @Requires({ Platform.current().windows })
    def "child runs in specified working directory [Windows]"() {
        given:
        def workDir = new File(System.getProperty("java.io.tmpdir"))
        def command = ["cmd.exe", "/c", "cd"]

        when:
        def pty = launcher.start(command, System.getenv(), workDir, 80, 24)
        def output = pty.inputStream.text
        def exitCode = pty.waitFor()

        then:
        exitCode == 0
        output.trim().equalsIgnoreCase(workDir.canonicalPath)

        cleanup:
        pty?.close()
    }

    @Requires({ Platform.current().windows })
    def "can destroy child process [Windows]"() {
        given:
        // timeout /t 60 waits for 60 seconds — gives us time to destroy
        def command = ["cmd.exe", "/c", "timeout /t 60"]

        when:
        def pty = launcher.start(command, System.getenv(), null, 80, 24)
        def executor = Executors.newSingleThreadExecutor()
        executor.submit { pty.inputStream.text }
        assert pty.alive
        pty.destroy()
        def exitCode = pty.waitFor()

        then:
        !pty.alive

        cleanup:
        executor?.shutdownNow()
        pty?.close()
    }

    @Requires({ Platform.current().windows })
    def "read from master returns EOF after child exits [Windows]"() {
        given:
        def command = ["cmd.exe", "/c", "echo done"]

        when:
        def pty = launcher.start(command, System.getenv(), null, 80, 24)
        def output = pty.inputStream.text
        def exitCode = pty.waitFor()

        then:
        exitCode == 0
        output.contains("done")

        cleanup:
        pty?.close()
    }
```

### Availability and fallback tests

```groovy
    def "isAvailable returns true on supported platforms"() {
        expect:
        launcher.isAvailable()
    }

    def "isAvailable returns a boolean without throwing"() {
        // Verifies the contract: isAvailable() never throws
        when:
        def result = launcher.isAvailable()

        then:
        noExceptionThrown()
        result instanceof Boolean
    }
```

On Windows, add a test that verifies `isConPtyAvailable()` matches the
expected result for the CI environment (Windows 10 1809+ should return `true`).
The test should be informational — it logs the result rather than asserting,
since CI may run on various Windows versions:

```groovy
    @IgnoreIf({!Platform.current().windows})
    def "reports ConPTY availability on Windows"() {
        when:
        def available = WindowsPtyFunctions.isConPtyAvailable()

        then:
        // Log for CI visibility; don't assert since Windows version may vary
        System.out.println("ConPTY available: " + available)
        noExceptionThrown()
    }
```

---

## File Inventory

### New files

| Path | Description |
|------|-------------|
| `src/main/java/net/rubygrapefruit/platform/terminal/PtyProcess.java` | Public API interface |
| `src/main/java/net/rubygrapefruit/platform/terminal/PtyProcessLauncher.java` | Public API interface |
| `src/main/java/net/rubygrapefruit/platform/internal/jni/PosixPtyFunctions.java` | JNI declarations for POSIX |
| `src/main/java/net/rubygrapefruit/platform/internal/jni/WindowsPtyFunctions.java` | JNI declarations for Windows |
| `src/main/java/net/rubygrapefruit/platform/internal/PosixPtyProcess.java` | POSIX PtyProcess implementation |
| `src/main/java/net/rubygrapefruit/platform/internal/PosixPtyProcessLauncher.java` | POSIX PtyProcessLauncher |
| `src/main/java/net/rubygrapefruit/platform/internal/WindowsPtyProcess.java` | Windows PtyProcess implementation |
| `src/main/java/net/rubygrapefruit/platform/internal/WindowsPtyProcessLauncher.java` | Windows PtyProcessLauncher |
| `src/test/groovy/net/rubygrapefruit/platform/terminal/PtyProcessLauncherTest.groovy` | Tests |

### Modified files

| Path | Change |
|------|--------|
| `src/main/cpp/posix.cpp` | Add ~350 lines: `isPtyAvailable`, `spawnPty` (atomic PTY alloc via `openpty` + `pipe2(O_CLOEXEC)` + fork/exec with `FD_CLOEXEC`, child diagnostic `_exit(126)`/`_exit(127)`, manual `PATH` resolution with `execve` + `EACCES` tracking, `EINTR` retry), `setPtySize`, `closeFd` (no `EINTR` retry on Linux), `waitPid` (with `EINTR` retry, `WIFEXITED`/`WIFSIGNALED` decoding, `ECHILD` handling), `killProcess`, `tryWaitPid`, `nativeRead` (`EINTR` retry + `EIO`/`ENXIO` normalization to EOF on all platforms), `nativeWrite` (`EINTR` retry + `EIO`/`ENXIO` → specific "process exited" exception on all platforms) |
| `src/main/cpp/win.cpp` | Add ~300 lines in two sections: (1) ConPTY functions inside `#ifndef WINDOWS_MIN` — `isConPtyAvailable` (via `GetProcAddress`), `createPseudoConsole` (runtime-resolved), `resizePseudoConsole`, `createProcessWithPty` (with stderr handle + fallback to merged stderr), `closePseudoConsole`; plus stubs inside `#else` that return `false`/error. (2) Basic Win32 functions outside any guard (all variants) — `createStderrPipe`, `waitForProcess`, `hasProcessExited`, `terminateProcess`, `closeHandle`, `nativeRead`, `nativeWrite` (JNI-backed stream I/O via `ReadFile`/`WriteFile`). |
| `src/main/java/net/rubygrapefruit/platform/internal/Platform.java` | Register `PtyProcessLauncher` in `Posix.get()` and `Windows.get()` |
| `buildSrc/src/main/java/gradlebuild/JniPlugin.java` | Add `-lutil` linker flag for Linux targets |

### Unchanged files (referenced for patterns)

| Path | Relevance |
|------|-----------|
| `src/shared/headers/generic.h` | Error reporting macros (`mark_failed_with_errno`, etc.) — reuse in new native code |
| `src/main/java/net/rubygrapefruit/platform/internal/FunctionResult.java` | Error reporting from native to Java — use in all new JNI methods |
| `src/main/java/net/rubygrapefruit/platform/internal/PosixTerminalInput.java` | Reference for existing raw-mode terminal handling |
| `src/main/java/net/rubygrapefruit/platform/internal/DefaultProcessLauncher.java` | Existing process launcher pattern |
| `src/main/java/net/rubygrapefruit/platform/internal/WindowsProcessLauncher.java` | Existing Windows process launcher pattern |
| `src/test/groovy/net/rubygrapefruit/platform/ProcessLauncherTest.groovy` | Test pattern to follow |
| `src/test/groovy/net/rubygrapefruit/platform/terminal/TerminalsTest.groovy` | Test pattern for terminal-specific tests |
| `native-platform/build.gradle` | Build structure — no component changes needed |

---

## Risks and Mitigations

| Risk | Mitigation |
|------|-----------|
| `openpty` is not available on all POSIX systems | It is available on Linux (glibc, musl), macOS, and FreeBSD — all platforms native-platform targets.  No exotic POSIX platforms are supported.  Edge cases (containers without `/dev/pts`, macOS sandbox, PTY namespace exhaustion) are handled by `isPtyAvailable()` returning `false`. |
| `openpty` fails in minimal containers | `isPtyAvailable()` performs a trial allocation at check time.  If `/dev/pts` is not mounted, the trial fails and `isAvailable()` returns `false`.  Gradle falls back to `ProcessBuilder` with a warning. |
| Linux kernel PTY namespace limit | `/proc/sys/kernel/pty.max` (default 4096) limits PTY allocation system-wide. Containerized environments may have lower limits via `devpts` mount `max=<N>` option. `openpty()` fails with `ENOSPC`/`EAGAIN`. Error message includes the errno to help diagnose. |
| macOS file descriptor soft limit | macOS has historically low default `ulimit -n` (256 on some configs, 10240 on recent). In a long-running daemon, `openpty()` can fail with `EMFILE`. Error message distinguishes `EMFILE`/`ENFILE` and suggests checking `ulimit -n`. |
| macOS sandbox blocks PTY allocation | Enterprise MDM can apply sandbox profiles that block `/dev/ptmx` access. `isPtyAvailable()` catches this; error message mentions sandbox/security policy as a possible cause. |
| macOS code signing / AMFI blocks `execve` | On macOS 13+, AMFI may block execution of quarantined binaries (`com.apple.quarantine` xattr). `execve()` fails with `EPERM`/`EACCES`. Error message suggests checking `xattr -l <binary>`. |
| macOS Rosetta 2 PID observability | When a Rosetta 2 JVM `fork()+execve()` targets an ARM64 binary, macOS re-execs transparently. `getPid()` may not match `ps` output. Does not affect `waitpid()` correctness. |
| Linux requires linking `-lutil` for `openpty` | On glibc-based Linux, `openpty` is in `libutil`. On musl (Alpine), it is in libc and `libutil` does not exist. Use `-Wl,--as-needed -lutil` so the linker silently ignores `-lutil` if symbols are already resolved. See Step 6 for the concrete build solution. |
| Creating Java streams from raw file descriptors is non-trivial | Use JNI-backed `InputStream`/`OutputStream` that call `read(2)`/`write(2)` (POSIX) or `ReadFile`/`WriteFile` (Windows) directly on the raw fd/HANDLE.  This avoids `/dev/fd/<n>` (creates new file descriptions with different semantics) and `FileDescriptor` field injection (requires `--add-opens` on Java 21+). Two small native functions (`nativeRead`, `nativeWrite`) are added to each platform's JNI class. |
| ConPTY not available on older Windows (non-`_min` variant) | Gradle supports Windows 10 starting from version 1507, but ConPTY requires version 1809 (build 17763).  In the non-`_min` variant, `isConPtyAvailable()` resolves `CreatePseudoConsole` via `GetProcAddress` at runtime — returns `false` on older builds.  Gradle falls back to `ProcessBuilder` with a warning.  **The native library must not directly link `CreatePseudoConsole`** so it remains loadable on pre-1809 Windows. |
| ConPTY not available in `WINDOWS_MIN` variant | The `_min` build variant excludes all ConPTY implementations at compile time with `#ifndef WINDOWS_MIN` / `#else` guards.  The `_min` variant provides stubs: `isConPtyAvailable()` returns `false`, other ConPTY functions fail with a clear error via `FunctionResult`.  Basic Win32 functions (`createStderrPipe`, `waitForProcess`, `closeHandle`, etc.) remain available in both variants.  **The `_min` variant's minimum Windows version does not change.** |
| `fork()` in a multi-threaded JVM process is dangerous | Use `fork()` + immediate `execve()` with only async-signal-safe functions between fork and exec: `setsid`, `ioctl`, `dup2`, `close`, `chdir`, `execve`, `_exit`.  No `malloc`, `printf`, stdio, or (on macOS) any Objective-C, CoreFoundation, or `libdispatch` calls.  `posix_spawn()` is not an alternative because it does not support `setsid()` or `ioctl(TIOCSCTTY)`.  All resource allocation (PTY, pipes) happens **before** `fork()` in a single atomic JNI call (`spawnPty`) to minimize the window.  Parent-side fds (`masterFd`, `stderrReadFd`) have `FD_CLOEXEC` set to prevent leaking into the child or into other children forked by concurrent threads. |
| `fork()` fails with `ENOMEM` on large JVM heaps | Most commonly caused by **cgroup memory limits** on CI (GitHub Actions, Jenkins on Kubernetes) — `fork()` requires kernel memory for page table copies. Also triggered by `vm.overcommit_memory=2` or `vm.max_map_count` exhaustion. `vfork()` is **not** a safe alternative in a multi-threaded JVM. Error message includes `strerror(errno)` so the user sees the actual reason. |
| `FD_CLOEXEC` race on `openpty()` master fd | `openpty()` has no `O_CLOEXEC` variant. Between `openpty()` returning and `fcntl(FD_CLOEXEC)`, another thread can `fork()+exec()` and inherit the master fd. Both calls happen within a single JNI function (no JVM safepoint possible between them), so the window is between two consecutive syscalls — nanoseconds. A leaked master fd keeps the slave reference alive (delaying `SIGHUP` delivery). The stderr pipe uses `pipe2(O_CLOEXEC)` to eliminate its race entirely. Defensively clear `CLOEXEC` on `slaveFd` after `openpty()` in case future glibc uses `TIOCGPTPEER` with `O_CLOEXEC`. |
| `execvp` uses parent's `PATH`, not child's environment | The child must use `execve()` with manual `PATH` resolution from the passed environment array, not `execvp()` which reads the parent's `environ`. This ensures the child resolves commands using the user-configured `PATH`. The manual implementation tracks `EACCES` during PATH search to report "Permission denied" instead of "No such file or directory" when appropriate. Empty PATH entries mean current directory per POSIX. Does not fall back to `/bin/sh` on `ENOEXEC` (unlike `execvp`). |
| `EINTR` from JVM safepoint signals | All blocking syscalls (`waitpid`, `read`, `write`) can return `-1` with `errno = EINTR` due to JVM-internal signals (e.g., `SIGUSR1`/`SIGUSR2` for safepoints). All implementations retry on `EINTR` in a loop. **Exception: `close()` is NOT retried on `EINTR` on Linux** — the fd is already closed even when `EINTR` is returned (Linux kernel quirk). Retrying would risk closing a different fd. |
| `WEXITSTATUS` undefined for signal-killed processes | `WEXITSTATUS(status)` is only valid when `WIFEXITED(status)` is true. Signal-killed children (e.g., after `destroy()` sends `SIGTERM`) must be decoded via `WIFSIGNALED(status)` → `128 + WTERMSIG(status)`. Without this, `waitFor()` after `destroy()` returns garbage. |
| Child zombie processes if `waitFor`/`close` is not called | `PtyProcess` extends `AutoCloseable`. Best-effort shutdown hook registered in the launcher. Real backstop: when the daemon dies, kernel closes the PTY master fd → child receives `SIGHUP` (as session leader via `setsid()`) → most programs exit. Orphans reparented to PID 1 for reaping. |
| `SIGCHLD` interaction with the JVM | Since `fork()` is called directly (not `ProcessBuilder`), the JVM's process reaper thread is unaware of our child. `waitpid()` in JNI correctly reaps the child. If `SIGCHLD` is `SIG_IGN` (auto-reaping), `waitpid` returns `ECHILD` — handled by treating `ECHILD` as "child already exited". On macOS, `NSTask` / `libdispatch` process monitoring can also cause spurious `ECHILD` — same handling applies. HotSpot does not set `SIG_IGN` but other JVMs or user code might. |
| PTY master returns `EIO`/`ENXIO` on child exit — read path | On Linux, `read()` on the PTY master after the slave closes returns `-1` with `errno = EIO`. On macOS, typically returns 0 (clean EOF), but `EIO` or `ENXIO` can also occur depending on timing. The JNI `nativeRead` treats **both `EIO` and `ENXIO`** as EOF on **all platforms**. |
| PTY master returns `EIO`/`ENXIO` on child exit — write path | `write()` on the PTY master after the child exits returns `-1` with `errno = EIO` or `ENXIO` — both can occur on both Linux and macOS depending on timing. The JNI `nativeWrite` catches both and throws a specific "process has exited" exception. |
| PTY buffer smaller than pipe buffer — deadlock risk | PTY buffers on Linux are typically 4 KB (vs 64 KB for pipes). Callers must drain both `getInputStream()` and `getErrorStream()` concurrently to avoid deadlocking the child. |
| Cooked-mode artifacts before child switches to raw mode | Default `termios` includes `ONLCR` (NL→CRNL) and echo. Initial child output has `\r\n` line endings and stdin bytes are echoed. This is standard PTY behavior. Gradle's output routing must tolerate `\r` in initial output. |
| Session leader behavioral difference | Child calls `setsid()` and becomes a session leader. When it exits, `SIGHUP` is sent to all processes in the foreground process group. Grandchildren that don't call `setsid()` receive `SIGHUP`. This differs from `ProcessBuilder`-launched processes. Documented as a known behavioral change when `fullTerminal` is enabled. |
| Post-fork child setup failures (`setsid`, `TIOCSCTTY`) are hard to diagnose | Child writes diagnostic byte to stderr pipe and uses `_exit(126)` for setup failures vs `_exit(127)` for exec failures. Parent can read the diagnostic byte to distinguish "exec failed" from "child setup failed." Seccomp-BPF filters in hardened containers may block `setsid()` or `ioctl(TIOCSCTTY)` — the diagnostic mechanism reports this to the parent. |
| Linux seccomp-BPF filters may block PTY-related syscalls | In hardened Docker environments with custom seccomp profiles, `openpty()` (internally calls `ioctl(TIOCGPTPEER)` on glibc ≥ 2.29), `setsid()`, or `ioctl(TIOCSCTTY)` may be blocked. `isPtyAvailable()` catches `openpty` failures; post-fork failures are reported via diagnostic `_exit(126)`. |
| `SIGWINCH` rapid resize — signal not coalesced | Rapid `resize()` calls each generate a separate `SIGWINCH`. Pending signals are not queued (standard POSIX). The child must call `ioctl(TIOCGWINSZ)` after receiving the signal. Transient size mismatches are inherent to the POSIX signal model, not a defect. PTY-based `SIGWINCH` is PID-namespace-safe. |
| macOS ObjC runtime / libdispatch after `fork()` | ObjC runtime and `libdispatch` are fork-unsafe (crash if called between fork and exec). Safe because `execve()` is called immediately with only async-signal-safe POSIX calls. Hard constraint: future code between fork/exec must never touch ObjC/CoreFoundation/GCD. |
| Windows ConPTY + separate stderr may not combine cleanly | The interaction between `PROC_THREAD_ATTRIBUTE_PSEUDOCONSOLE` and `STARTF_USESTDHANDLES` is poorly documented by Microsoft. Separate stderr is **best-effort** — merged stderr (ConPTY on all fds) is the realistic common case. `spawnConPty` retries with merged stderr automatically. Test on Windows 10 1809, 22H2, and Windows 11 23H2+. |
| Windows handle inheritance race | Multi-threaded JVM may leak pipe handles into unrelated child processes between `CreatePipe` and `CreateProcessW`.  Mitigated by: (1) marking parent-side handles non-inheritable via `SetHandleInformation`; (2) using `PROC_THREAD_ATTRIBUTE_HANDLE_LIST` to explicitly whitelist inherited handles. Same class of bug as JDK-6921885. |
| `ClosePseudoConsole` deadlock | `ClosePseudoConsole` blocks until ConPTY's internal I/O threads drain. If a `ReadFile` on the PTY output pipe is still blocked, deadlock occurs. `WindowsPtyProcess.close()` must cancel I/O via `CancelSynchronousIo`, close pipe handles, **then** call `ClosePseudoConsole`. |
| Windows `CloseHandle` does not unblock blocked `ReadFile` | Unlike POSIX `close(fd)`, `CloseHandle` does not unblock a `ReadFile` on another thread — may crash or hang. JNI-backed `InputStream` on Windows stores its native thread handle and exposes `cancel()` via `CancelSynchronousIo`. `close()` calls `cancel()` before closing handles. |
| `TerminateProcess` is SIGKILL, not SIGTERM | `TerminateProcess` is unconditional and immediate — the child cannot catch it. `destroy()` on Windows sends Ctrl+C (`\x03`) to the PTY input pipe first (ConPTY translates to `CTRL_C_EVENT`), waits briefly, then falls back to `TerminateProcess`. `destroyForcibly()` calls `TerminateProcess` directly. |
| `GetExitCodeProcess` with exit code 259 (`STILL_ACTIVE`) | Exit code 259 is a legitimate value, not a sentinel. `waitForProcess` and `hasProcessExited` always use `WaitForSingleObject` before `GetExitCodeProcess` to avoid misinterpreting 259 as "still running". |
| `ResizePseudoConsole` thread safety | Not documented as thread-safe by Microsoft. Calling concurrently with `ClosePseudoConsole` may cause undefined behavior. `resize()` and `close()` are synchronized on the Java side. |
| Windows command-line quoting | `CreateProcessW` takes a single mutable `LPWSTR` command line. `spawnConPty` quotes the `String[] command` per `CommandLineToArgvW` rules (spaces, quotes, backslashes). The JNI code copies into a writable `wchar_t` buffer — never passes `GetStringChars` directly. |
| Windows environment block encoding | `CreateProcessW` expects a double-null-terminated `wchar_t` block (`L"K=V\0K=V\0\0"`). `spawnConPty` encodes `String[] environment` into this format and passes `CREATE_UNICODE_ENVIRONMENT`. |
| ConPTY VT output encoding | ConPTY always outputs UTF-8 VT sequences regardless of the child's console code page. The JNI-backed `InputStream` returns raw bytes — the caller (Gradle) must treat them as UTF-8. |
| PTY echo: stdin bytes echoed to the master read stream (stdout) | This is standard PTY behavior.  The TUI application is expected to set the PTY to raw mode (disabling echo) via `tcsetattr`.  Gradle should not attempt to filter echoed bytes. |
| Cygwin/MSYS2 environment | Gradle may detect a Cygwin console (via existing `isConsole` in `win.cpp`), but ConPTY is the correct API for child PTY allocation regardless.  The ConPTY availability check works the same way in Cygwin/MSYS2 environments. |
| `waitFor()` blocks a native/carrier thread | `waitPid` / `waitForProcess` is a blocking JNI call that pins a carrier thread (relevant with Java 21 virtual threads). In Gradle's daemon, `ExecHandleRunner` runs on a managed executor thread, so this is acceptable. The non-blocking `tryWaitPid` / `hasProcessExited` alternatives exist for polling patterns if needed. |
| File descriptor / handle leaks on error paths | `spawnPty` (POSIX) and `spawnConPty` (Windows) perform all allocation and process creation atomically. On failure at any stage, all resources allocated so far are cleaned up internally before returning to Java — no fragile multi-step cleanup on the Java side. |

---

## Graceful Fallback Architecture

PTY support must not change which operating system versions Gradle can run on.
The native library must remain loadable on all currently supported platforms,
even those that lack PTY primitives.

### Fallback chain

```
Gradle sets fullTerminal=true on Exec/JavaExec task
  │
  ├─ Try: Native.get(PtyProcessLauncher.class)
  │   └─ Fail (NativeIntegrationUnavailableException): platform not recognized
  │       → Log warning, fall back to ProcessBuilder
  │
  ├─ Try: launcher.isAvailable()
  │   └─ Returns false: OS lacks PTY API
  │       → Log warning ("PTY not available: <reason>"), fall back to ProcessBuilder
  │
  ├─ Try: launcher.start(...)
  │   ├─ [Windows] ConPTY + separate stderr
  │   │   └─ Fail: CreateProcessW rejects combined attributes
  │   │       → Retry: ConPTY with merged stderr (log warning)
  │   │           └─ Fail: ConPTY allocation itself fails
  │   │               → Throw NativeException → Gradle falls back to ProcessBuilder
  │   │
  │   └─ [POSIX] openpty + fork/exec
  │       └─ Fail: openpty returns -1 (fd exhaustion, no /dev/pts)
  │           or fork returns -1 (ENOMEM, EAGAIN)
  │           → Throw NativeException → Gradle falls back to ProcessBuilder
  │
  └─ Success: TUI process runs with PTY
```

### Key principles

1. **`isAvailable()` never throws** — it returns a boolean.  Safe to call
   on any platform at any time.

2. **Runtime resolution on Windows** — `CreatePseudoConsole` is resolved via
   `GetProcAddress`, not linked directly.  The DLL remains loadable on
   pre-1809 Windows where the function does not exist.

3. **`WINDOWS_MIN` variant** — The `_min` native library variant (for older
   Windows) excludes all ConPTY implementations at compile time behind
   `#ifndef WINDOWS_MIN` / `#else` guards.  The `_min` variant compiles
   stubs that return `false` / fail with `FunctionResult`.  Only the
   non-`_min` variant resolves ConPTY at runtime via `GetProcAddress`.
   The `_min` variant's minimum Windows version is unchanged.

4. **No new OS requirements** — Gradle's supported platform matrix is
   unchanged: Windows 10+, macOS 13+, Ubuntu 22+, Alpine 3.20+,
   CentOS Stream 9+.  PTY features are available on most of these but
   degrade gracefully where they are not.

5. **Error messages identify the requirement** — When PTY is unavailable,
   the error/warning message states what is needed (e.g., "ConPTY requires
   Windows 10 version 1809 or later", "openpty failed: /dev/pts not
   mounted", "openpty failed: PTY allocation may be blocked by macOS
   sandbox or security policy", "openpty failed: per-process file
   descriptor limit reached (check ulimit -n)", "openpty failed: kernel
   PTY limit reached (check /proc/sys/kernel/pty.max)").

## Implementation Order

1. **POSIX JNI + native code** — `PosixPtyFunctions.java` + `posix.cpp` additions.
   Test with a simple `spawnPty(/bin/sh)` → read output flow.
2. **POSIX Java implementation** — `PosixPtyProcess`, `PosixPtyProcessLauncher`.
3. **Public API interfaces** — `PtyProcess`, `PtyProcessLauncher`.
4. **Platform registration** — `Platform.java` changes.
5. **POSIX tests** — `PtyProcessLauncherTest.groovy`.
6. **Build changes** — `-lutil` linker flag.
7. **Windows JNI + native code** — `WindowsPtyFunctions.java` + `win.cpp`.
8. **Windows Java implementation** — `WindowsPtyProcess`, `WindowsPtyProcessLauncher`.
9. **Windows tests** — Windows-specific test cases.
