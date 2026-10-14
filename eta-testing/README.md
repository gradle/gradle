# ETA test builds

Each directory is a standalone build that shows how the console estimates the time remaining in the execution phase.
Every build has an `all` task that runs the scenario, and an `intro` task, which every other task depends on, that prints what the build does.

## How to run

Install a Gradle distribution built from this branch, and stop its daemon after every install so the next build does not reuse a daemon running the previous code:

```
./gradlew install -Pgradle_installPath=/tmp/gradle-eta
/tmp/gradle-eta/bin/gradle --stop
```

Then run a scenario with the rich console:

```
cd eta-testing/<scenario>
/tmp/gradle-eta/bin/gradle all --console=rich
```

The first run of a scenario shows `ESTIMATE UNAVAILABLE`, because no task has a recorded duration yet.
Run it again to see the estimate.
Each task keeps up to its 3 most recent durations, in `.gradle/task-durations.txt`; delete that file to start over.
Run `./clear-history.sh` to delete the history of every scenario.

Two options simulate Develocity:

- `--simulate-dv-on` prints a message and waits 5s when the build starts, as if downloading historical timing data.
- `--simulate-dv-off` advertises Develocity next to the estimate.

## Scenarios

### test-build

36 independent tasks, taking 1-5s each and varying by up to 3s per run, run in parallel.
Then a slow task, taking 12-15s, runs after all of them.

Shows the ETA accounting for a long task at the end of the build: while the slow task runs, the ETA keeps counting down instead of dropping to 0.

### test-build-new-tasks-scheduled

12 tasks each run a nested build, with a `GradleBuild` task, of 2 more tasks.
The nested tasks are only scheduled when the task that runs them executes, so the number of scheduled tasks grows during execution.
Then a slow task, taking 12-15s, runs after all of them.

Shows the ETA rising when work is added during execution, and a task that waits on its nested build no longer counting as running.
The nested build in `extra-tasks` keeps its own task duration history.

### test-build-overrun

8 parallel tasks take 2s longer on every run, so they always take longer than the average of their recorded durations.
Then a finish task takes 3s.
The run count is kept in `build/run-count.txt`; delete `build` to start over.

Shows the ETA stalling, rather than counting up, while tasks overrun their estimates: once the 8 tasks pass their estimates, the ETA holds at about the finish task's duration until they complete.

### test-build-early-finish

A chain of 8 tasks, each depending on the previous one.
Each task takes 3s, or finishes early after 0.3s, picked at random on every run, so its recorded average lies somewhere in between.

Shows the ETA dropping in jumps when a task finishes earlier than its average, and holding when a task takes longer than its average.

### test-build-diamond

Stages alternate between a single task and 8 parallel tasks: `a` (2s), then `b1`-`b8` (3s), then `c` (4s), then `d1`-`d8` (3s), then `e` (2s).

Shows the ETA following the longest chain of dependent tasks, about 14s, rather than the total work spread across the parallel tasks.

It also shows a weakness: while only `a` runs, the most tasks seen running at once is 1, so the remaining work is not spread across any parallelism and the ETA shows over a minute.
It drops to the right value once the `b` tasks start.

### test-build-long-chain

A chain of 20 tasks, each taking 1s and depending on the previous one, runs alongside 40 independent tasks that also take 1s each.

Shows the ETA following the 20s chain, even though there is twice as much independent work: spread across the parallel workers, the independent work finishes in a few seconds.
The progress bar passes two thirds within 2s, while the ETA correctly still shows about 18s.

### test-build-composite

4 app tasks (2s each) depend on 2 tasks (3s each) of the included build `lib`.
The tasks of `lib` do not depend on `intro`, as an included build cannot depend on tasks of the build that includes it.

Shows the ETA covering work in an included build: the included build schedules its work separately from the root build, and the estimate covers both.
The tasks of `lib` are recorded in the root build's history, as `:lib:libA` and `:lib:libB`, since the history is kept per build tree.

### test-build-transform

A task in the `producer` project (2s) writes a file that a task in the root project (2s) consumes through an artifact transform (1s).

Shows the ETA unavailable on every run, even once every task has a recorded duration: the transform of another project's artifact runs as its own step in the plan, and has no recorded duration.
This is common in real multi-project builds.
A transform of a file dependency, by contrast, runs inside the task that consumes it, so its time counts as part of that task and the ETA stays available.

### test-build-new-task

6 known tasks (2s each) run in parallel.
Passing `-PnewTask=<name>` adds a task with that name (2s); use a name that has never run, for example `-PnewTask=new$RANDOM`.

Shows a single task without a recorded duration making the ETA unavailable for the whole build.
Run without the property first, twice, to see the ETA, then with it.
