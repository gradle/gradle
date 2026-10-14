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
