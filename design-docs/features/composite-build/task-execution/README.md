# M8 Task execution within a Composite

The task path is the fully-qualified path to the task including the project's path and task name separated by colons.

An unqualified name is a name without any project path information (i.e., without colons).

Unqualified task names and project paths can be shortened using normal shortening rules (camel case, shortest unambiguous name). These are not treated as separate cases below.

Note that `buildSrc` can be a multi-project build.  We want `buildSrc` to eventually be a built-in composite.

## Scenarios

### Single-project build

A user may want to (from the root directory):
1. Run a task in a project.
    _Use case_: Build my project.
    - `gradle <unqualified task name>`
    - `gradle :<task path>`

Behavior appears identical to the user with a task path and with an unqualified task name. 

### Multi-project build

A user may want to (from the root directory):
1. Run a task in a project.
    _Use case_: Build my single project in a huge multi-project build.
    When defined in the root and subproject: 
    - `gradle :<task path>`
    - `gradle -p projectDir <unqualified task name>` 
    - `gradle -p projectDir :<task path>`
    When defined _only_ in the project:
    - All of the above 
    - `gradle <unqualified task name>`
2. Run a task in the root project.
    _Use case_: Generate something that's build-specific.
    When defined in the root and subproject: 
    - `gradle :<task path>` 
    When defined _only_ in the root: 
    - All of the above 
    - `gradle <unqualified task name>` 
3. Run a task with an unqualified name in all projects (does not include `buildSrc`).
    _Use case_: Build everything and let Gradle handle the dependencies/parallelization.
    - `gradle <unqualified task name>`
4. Run a task with an unqualified name in all projects under a subproject (does not include `buildSrc`).
    _Use case_: Build all of my "web" projects that are organized under `:web:*`
    - `gradle -p projectDir <unqualified task name>`

### Single-project build with `buildSrc`

A user would want to:
1. Run a task in a project. 
    _Use case_: Build my project.
    - `gradle <unqualified task name>`
    - `gradle :<task path>`
2. Run task in `buildSrc` only. 
    _Use case_: Build the plugins that are used by my project.
    - `gradle -p buildSrc <unqualified task name>` 
    - `gradle -p buildSrc :<task path>` 
3. Run task in both the project and `buildSrc`. 
    _Use case_: Generate IDE configuration for my project and `buildSrc` so I can edit both of them in one view.
    - This is not supported.  `buildSrc` is not a subproject, so this can be seen as the same situation as trying to run tasks in two entirely separate builds with some special (hardcoded) handling.

### Multi-project build with `buildSrc`

A user would want to:
1. Run a task in a project. (same as above)
2. Run a task in the root project. (same as above)
3. Run a task with an unqualified name in all projects (does not include `buildSrc`). (same as above)
4. Run a task with an unqualified name in all projects under a subproject (does not include `buildSrc`). (same as above)
4. Run task in `buildSrc` only. 
    - `gradle -p buildSrc <unqualified task name>` 
    - `gradle -p buildSrc :<task path>` 
5. Run task in both a project and `buildSrc`. 
    - This is not supported.

### Aggregated Composite build (one single-project participant build)

Participant is in `A/` and is included in a composite.

The composite build defines tasks too for convenience. For example, the composite build could have a `setupWorkspace` task that installs the appropriate tools for building `A/`. 

A/build.gradle
```
apply plugin: 'java'
```

composite/settings.gradle
```
includeBuild('A/') {
    buildName = "A" // NEW (default to basename of directory)
}
```

composite/build.gradle
```
apply plugin: 'base'

task setupWorkspace
build.dependsOn setupWorkspace, gradle.includedBuild("A").task("build")
```

A user would want to:
1. Run a task in the composite build.
    _Use case_: Setup my workspace for building.
    - `gradle setupWorkspace` or `gradle :setupWorkspace` (current behavior)
    - (Solution 1) `gradle composite::setupWorkspace` (build name prefix)
    - (Solution 2) `gradle :composite:setupWorkspace` (collapse into build/project namespace)
    - (Solution 3) `gradle --participant composite setupWorkspace` or `gradle -r composite setupWorkspace` (positional arguments)
2. Run a task in the `A/` build. 
    _Use case_: Build my project (`A/`).
    - (Solution 1) `gradle A::build` 
    - (Solution 2) `gradle :A:build` 
    - (Solution 3) `gradle --participant A build` or `gradle -r A build` 
3. Run a task that is defined in the "aggregate" build and the `A/` participant.
    _Use case_: Clean everything.
    - (Solution 1) `gradle clean` (implicitly does clean in composite and all participants) 
    - (Solution 2) `gradle clean` (implicitly does clean in composite and all participants)
    - (Solution 3) `gradle -r clean` (positional arguments, no argument means "look everywhere")

### Aggregated Composite build (one multi-project participant build)

Participant is in `A/` with subprojects (x, y, z) and is included in a composite.

The composite build defines tasks too for convenience as described above.

See example above, but make `A/` a multi-project build.

A user would want to:
1. Run a task in the composite build.
    _Use case_: Setup my workspace for building.
    - As above.
2. Run a task in a particular project in the `A/` build. 
    _Use case_: Build my x project in `A/` build.
    - (Solution 1) `gradle A::x:build` 
    - (Solution 2) `gradle :A:x:build` 
    - (Solution 3) `gradle --participant A :x:build` or `gradle -r A :x:build` 
3. Run a task in the root project of `A/`.
    _Use case_: Generate something specific to the `A/` build.
    - (Solution 1) `gradle A::generate` 
    - (Solution 2) `gradle :A:generate` 
    - (Solution 3) `gradle --participant A :generate` or `gradle -r A :generate` or (shorthand?) `gradle -r :generate` if `:generate` is only in `A/`
4. Run a task with an unqualified name in all projects in `A/`.
    _Use case_: Build everything and let Gradle handle the dependencies/parallelization.
    - (Solution 1) `gradle A:build` 
    - (Solution 2) `gradle :A ???` (not expressible without something else)
    - (Solution 3) `gradle --participant A build` or `gradle -r A build` or `gradle -r build` if `build` is only in `A/`
4. Run a task with an unqualified name in all projects under a subproject in `A/`.
    _Use case_: Build all of my "web" projects that are organized under `:web:*`
    - (Solution 1) `gradle A: ???` (not expressible)
    - (Solution 2) `gradle :A ???` (not expressible)
    - (Solution 3) `gradle --participant A :web:build` or `gradle -r A :web:build` or `gradle -r :web:build` if `:web:build` is only in `A/`
5. Run a task that is defined in the "aggregate" build and the `A/` participant.
    _Use case_: Clean everything.
    - As above.

### "Primary" Multi-project composite build with "provider" participant builds

Participant is in `A/` and is included in a composite.

The composite build is the "primary" build and consumes artifacts from `A/` usually as external dependencies.

A/build.gradle
```
apply plugin: 'java'
group = "org"
version = "2.0"
```

primary/settings.gradle
```
includeBuild('A/')
```

primary/build.gradle
```
apply plugin: 'java'
dependencies { compile "org:A:1.0" }
```

A user would want to:
1. Be able to do all of the things from "Multi-project build" above as if `A/` did not exist in the composite.
    _Use case_: Build my project, but build and substitute 3rd party dependencies from `A/` to handle non-upstreamed patches (for example). Do not care if unused `A/` projects are built or not.
    - `gradle build` (current behavior)
    - (Solution 1) `gradle primary::build` 
    - (Solution 2) `gradle :primary:build` 
    - (Solution 3) `gradle --participant primary build` or `gradle -r primary build` 
2. Run a task in a particular project in the `A/` build. 
    _Use case_: Build my single project in a huge multi-project build.
    - As above.
3. Run a task in the root project of `A/`.
    _Use case_: Generate something specific to the `A/` build.
    - As above.
4. Run a task with an unqualified name in all projects in `A/`.
    _Use case_: Build everything.
    - As above.
4. Run a task with an unqualified name in all projects under a subproject in `A/`.
    _Use case_: Build all of my "web" projects that are organized under `:web:*`
    - As above.
5. Run a task that is defined in the "primary" build and the `A/` participant.
    _Use case_: Clean everything or build everything and let Gradle handle the dependencies/parallelization.
    - As above.

### Composite build with composite participants

Support addressing tasks within a composite that is within another composite.

e.g., if `A/` consists of the composite of `A/buildSrc` and `A/` (root project). And `B/` consists of the composite of `B/buildSrc` and `B/` (root project).  

- How do I run `build` in `A/buildSrc` vs `B/buildSrc` and `A/` vs `B/`?
- Unsupported right now. This would need to work once `buildSrc` is an implicit composite.

### Comparisons/Observations between solutions

0. Overall:
    - We should consider making the `buildName` configurable on `IncludedBuild`s
    - You can't express unqualified names that apply to a subset of projects (none of the solutions directly allow for this)
    - Maybe we can re-use `-p` to select subprojects to avoid cryptic command-line invocations
        - e.g., `gradle -p :web build` to run `build` for all subprojects under `:web`
1. Solution 1 is different enough from the current syntax that it's likely to be understood as doing "something different". 
    - There is some ambiguity if we allow "unqualified" names, since these would appear to be relative paths from the current project. 
    - To disambiguate between unqualified names and relative subprojects, we would need to make sure composite build names do not overlap with any subproject names or detect when this problem may occur.
2. Solution 2 makes composite builds and multi-project builds appear similar, but raises some edge cases:
    - Project name and build names must be unique so no potential paths overlap.
    - You can no longer tell that `:foo:bar:bazTask` is coming from another build. So using the command-line syntax in `dependsOn` would either need to work for composites or we would add a new inconsistency. 
3. Solution 3 adds something similar to `TaskReference` to the CLI by using a new position-sensitive option `-r <buildName>` or `--participant <buildName>` or `-r`.
    - Tasks/paths listed after `-r` are considered relative to that build.
    - `-r` without a buildName is "search everywhere"
    - Combined with changes to `-p` above...
        - `gradle -r A -p :web build` -- execute `build` for all subprojects under `:web` in participant `A/`

## Open questions 

- Does `-x` to exclude a task follow these same rules?
- When someone types a task path or name wrong, what does the error look like?
- What does the presentation of `tasks` look like for composite builds?
- Could `-p` be made to accept project paths as well as projectDirs?

