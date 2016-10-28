# M8 Task execution within a Composite

The task path is the fully-qualified path to the task including the project's path and task name separated by colons.

An unqualified name is a name without any project path information (i.e., without colons).

Unqualified task names and project paths can be shortened using normal shortening rules (camel case, shortest unambiguous name). These are not treated as separate cases below.

Note that `buildSrc` can be a multi-project build.  We want `buildSrc` to eventually be a built-in composite.

## Story Breakdown

### M8.X: When displaying tasks in a composite, tasks use composite-aware path

Make task paths/names from within a composite build make sense from the console output.

#### Implementation

TBD

#### Test Cases

- Tasks from root composite look "normal"
- Tasks from participant have the participants path tacked on (as in a multi-project build)
- Error messages when a task fails is "composite-aware"

### M8.X: User can provide a name for overlapping composite builds

Unify the composite build namespace with the subproject namespace of the root composite build.  Subprojects named `buildSrc` emit a deprecation warning.

#### Implementation 

- Expand `IncludedBuild` to allow someone to set the name of a composite participant.
- Enforce that all included builds and all root-most subprojects must have unique names.
- Fail with a useful message if an included build is added that overlaps with an existing subproject (subprojects should "win") or a subproject is added when an included build exists that overlaps (subprojects should still "win"). We should suggest renaming the participant over the subproject.

#### Test Cases

- When a subproject with path `:A` exists and an included build with the name `A` is added, fail with a message that suggests renaming the included build.
- When a included build with the name `A` exists and a subproject with path `:A` is added, fail with a message that suggests renaming the included build.
- When two included builds are added with the same name, fail with a message that suggests renaming the second one.
- When two included builds are added one with the name `A` and one in the directory `A` (but rootProject.name isn't set), fail with a message that suggests renaming the second one. It would be nice if we could detect that the name isn't set explicitly.
- When two included builds have overlapping names, after giving them unique names, the build is successful.
- When an included build and a subproject have overlapping names, after giving the included build a unique name, the build is successful.
- When an included build and a subproject have overlapping names, after giving the subproject a unique path, the build is successful.
- Changing the included build name should affect the console output.
- Subprojects named `buildSrc` emit a deprecation warning.

### M8.X: User can use participant name to select tasks from the command-line

See more scenarios below. Essentially, this allows composite build participants to appear as if they are subprojects in a multi-project build when executing tasks.

#### Implementation

- Brain-dead implementation:
    - For tasks that are not found within the current build
        - Create a task delegate for the participant it is from
        - Assign all task selectors (or wildcard task selectors) to it
    - For unqualified task selectors, create a task delegate for all participants?
- Unified DAG?

#### Test Cases

- A composite with a participant `A`
    - `gradle :A:build` executes `:build` in `A`
    - `gradle :A:x:build` executes `:x:build` in `A`
    - `gradle :doesNotExist:build` provides a useful error message
    - `gradle :A:doesNotExist` provides a useful error message
    - `gradle createdByRule` does not fail when rule that creates "createdByRule" exists in `A` or in the root composite build.
    - `gradle existsInSome` does not fail when "existsInSome" only exists in `A` or in the root composite build.
    - `gradle :A:build -x :A:test` should exclude `:A:test` from execution
    - `gradle :A:someTask --option-for-task` should pass `--option-for-task` to `:A:someTask`

#### Questions

- How do we handle doing redundant work without a unified DAG?
- How do we handle cross-participant dependencies? 
- How does this interact with `-p`?
- How does this interact with `-D` or `-P` and other "build-level" command-line arguments?

### M8.X: User can use participant name to select tasks from the TAPI

### M8.X: User can see the project/composite structure

Enhance `gradle projects` to also list included builds.

### M8.X: User can see the tasks available in a composite build

Enhance `gradle tasks` to also list included builds tasks (if this doesn't automatically work from above).

### M8.X: User can use `::` to select tasks from the current project and all subprojects

Allow someone to use this from the command-line and TAPI.

`dependsOn` will not allow `::`.

Update `gradle tasks` to mention this.

#### Test Cases

- When `:build`, `:sub:build` and `:sub:x:build` exist:
    - `gradle ::build` executes all three.
    - `gradle :sub::build` executes `:sub:build` and `:sub:x:build`
    - `gradle :sub:x::build` executes `:sub:x:build`
    - `gradle ::sub:build` and `gradle :sub::x:build` fails with a useful error message.
- When `:build` does not exist but `:sub:build` and `:sub:x:build` exist:
    - `gradle ::build` executes `:sub:build` and `:sub:x:build`
- When doing `gradle build -x ::test` should exclude all tasks named "test" from execution.
- `gradle ::doesNotExist` should fail with a useful error message
- `gradle ::createdByRule` should not fail when "createdByRule" is a task created by a Task rule in a subproject.
- For composite included builds:
    - `gradle build` executes `:build` and `:x:build` in `A` and the root composite build.
    - `gradle :A::build` executes `:build` and `:x:build` in `A`
    - `gradle :A::doesNotExist` provides a useful error message
    - `gradle ::build` executes build in the root composite build only. 

#### Questions
- Is expanding this to `dependsOn` necessary? Configuration cost/ordering might get complicated.

```
something.dependsOn "::build"

// Needs to be equivalent to something like:

something.dependsOn "build"
something.dependsOn subprojects.collect { tasks["build"] }
```

## Scenarios (existing and new)

### Single-project build (no change in behavior)

A user may want to (from the root directory):
1. Run a task in a project.
    - _Use case_: Build my project.
    - `gradle <unqualified task name>`
    - `gradle :<task path>`

Behavior appears identical to the user with a task path and with an unqualified task name. 

### Multi-project build (no change in behavior)

A user may want to (from the root directory):
1. Run a task in a project.
    - _Use case_: Build my single project in a huge multi-project build.
    - When defined in the root and subproject: 
    - `gradle :<task path>`
    - `gradle -p projectDir <unqualified task name>` 
    - `gradle -p projectDir :<task path>`
    - When defined _only_ in the project:
    - All of the above 
    - `gradle <unqualified task name>`
2. Run a task in the root project.
    - _Use case_: Generate something that's build-specific.
    - When defined in the root and subproject: 
    - `gradle :<task path>` 
    - When defined _only_ in the root: 
    - All of the above 
    - `gradle <unqualified task name>` 
3. Run a task selector (does not include `buildSrc`).
    - _Use case_: Build everything and let Gradle handle the dependencies/parallelization.
    - `gradle <unqualified task name>`
4. Run a task selector in a subproject (does not include `buildSrc`).
    - _Use case_: Build all of my "web" projects that are organized under `:web:*`
    - `gradle -p projectDir <unqualified task name>`

### Single-project build with `buildSrc` (no change in behavior)

A user would want to:
1. Run a task in a project. 
    - _Use case_: Build my project.
    - `gradle <unqualified task name>`
    - `gradle :<task path>`
2. Run task in `buildSrc` only. 
    - _Use case_: Build the plugins that are used by my project.
    - `gradle -p buildSrc <unqualified task name>` 
    - `gradle -p buildSrc :<task path>` 
3. Run task in both the project and `buildSrc`. 
    - _Use case_: Generate IDE configuration for my project and `buildSrc` so I can edit both of them in one view.
    - This is not supported.  `buildSrc` is not a subproject, so this can be seen as the same situation as trying to run tasks in two entirely separate builds with some special (hardcoded) handling.

### Multi-project build with `buildSrc` (no change in behavior)

A user would want to:

1. Run a task in a project. (same as above)
2. Run a task in the root project. (same as above)
3. Run a task selector (does not include `buildSrc`). (same as above)
4. Run a task selector in a subproject (does not include `buildSrc`). (same as above)
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

settings.gradle
```
includeBuild('A/') {
    buildName = "A" // NEW (default to rootProject.name)
}
```

build.gradle
```
apply plugin: 'base'

task setupWorkspace
build.dependsOn setupWorkspace, gradle.includedBuild("A").task(":build")
```

A user would want to:

1. Run a task in the root composite build.
    - _Use case_: Setup my workspace for building.
    - `gradle setupWorkspace` or `gradle :setupWorkspace` (current behavior)
2. Run a task in the `A/` build. 
    - _Use case_: Build my project (`A/`). 
    - `gradle :A:build` (NEW: makes A look like a subproject in a multi-project build)
3. Run a task that is defined in the root composite build and the `A/` participant.
    - _Use case_: Clean everything.
    - `gradle clean` (NEW: implicitly does clean in composite and all participants)

### Aggregated Composite build (one multi-project participant build)

Participant is in `A/` with subprojects (x, y, z) and is included in a composite.

The composite build defines tasks too for convenience as described above.

See example above, but make `A/` a multi-project build.

A user would want to:

1. Run a task in the composite build.
    - _Use case_: Setup my workspace for building.
    - As above.
2. Run a task in a particular project in the `A/` build. 
    - _Use case_: Build my x project in `A/` build.
    - `gradle :A:x:build` (NEW: make composite build look like multi-project build)
3. Run a task in the root project of `A/`.
    - _Use case_: Generate something specific to the `A/` build.
    - `gradle :A:generate` (NEW: As above)
4. Run a task selector in  `A/`.
    - _Use case_: Build everything and let Gradle handle the dependencies/parallelization.
    - `gradle :A::build` (NEW: Treat :: as wildcard to mean "execute task in this project and all subprojects")`
4. Run a task selector in a subproject of `A/`.
    - _Use case_: Build all of my "web" projects that are organized under `:web:*`
    - `gradle :A:web::build` (NEW: Treat :: as wildcard to mean "execute task in this project and all subprojects")`
5. Run a task that is defined in the root composite build and the `A/` participant.
    - _Use case_: Clean everything.
    - As above.

### Composite build with composite participants (unsupported)

Support addressing tasks within a composite that is within another composite.

e.g., if `A/` consists of the composite of `A/buildSrc` and `A/` (root project). And `B/` consists of the composite of `B/buildSrc` and `B/` (root project).  

- How do I run `build` in `A/buildSrc` vs `B/buildSrc` and `A/` vs `B/`?
- Unsupported right now. This would need to work once `buildSrc` is an implicit composite.
