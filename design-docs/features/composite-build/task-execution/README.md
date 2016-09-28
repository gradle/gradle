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

A user would want to:
1. Run a task in the composite build.
    _Use case_: Setup my workspace for building.
    - `gradle ???` 
2. Run a task in the `A/` build. 
    _Use case_: Build my project.
    - `gradle ???` 
3. Run a task that is defined in the "aggregate" build and the `A/` participant.
    _Use case_: Clean everything.
    - `gradle ???` 

### Aggregated Composite build (one multi-project participant build)

Participant is in `A/` with subprojects (x, y, z) and is included in a composite.

The composite build defines tasks too for convenience as described above.

A user would want to:
1. Run a task in the composite build.
    _Use case_: Setup my workspace for building.
    - `gradle ???` 
2. Run a task in a particular project in the `A/` build. 
    _Use case_: Build my single project in a huge multi-project build.
    - `gradle ???` 
3. Run a task in the root project of `A/`.
    _Use case_: Generate something specific to the `A/` build.
    - `gradle ???` 
4. Run a task with an unqualified name in all projects in `A/`.
    _Use case_: Build everything and let Gradle handle the dependencies/parallelization.
    - `gradle ???` 
4. Run a task with an unqualified name in all projects under a subproject in `A/`.
    _Use case_: Build all of my "web" projects that are organized under `:web:*`
    - `gradle ???` 
5. Run a task that is defined in the "aggregate" build and the `A/` participant.
    _Use case_: Clean everything.
    - `gradle ???` 

### "Primary" Multi-project composite build with "provider" participant builds

Participant is in `A/` and is included in a composite.

The composite build is the "primary" build and consumes artifacts from `A/` usually as external dependencies.

A user would want to:
1. Be able to do all of the things from "Multi-project build" above as if `A/` did not exist in the composite.
    _Use case_: Build my project, but substitute 3rd party dependencies from `A/` to handle non-upstreamed patches (for example).
    - `gradle ???` 
2. Run a task in a particular project in the `A/` build. 
    _Use case_: Build my single project in a huge multi-project build.
    - `gradle ???` 
3. Run a task in the root project of `A/`.
    _Use case_: Generate something specific to the `A/` build.
    - `gradle ???` 
4. Run a task with an unqualified name in all projects in `A/`.
    _Use case_: Build everything.
    - `gradle ???` 
4. Run a task with an unqualified name in all projects under a subproject in `A/`.
    _Use case_: Build all of my "web" projects that are organized under `:web:*`
    - `gradle ???` 
5. Run a task that is defined in the "primary" build and the `A/` participant.
    _Use case_: Clean everything or build everything and let Gradle handle the dependencies/parallelization.
    - `gradle ???` 

### Composite build with composite participants

Support addressing tasks within a composite that is within another composite.

e.g., if `A/` consists of the composite of `A/buildSrc` and `A/` (root project). And `B/` consists of the composite of `B/buildSrc` and `B/` (root project).  

- How do I run `build` in `A/buildSrc` vs `B/buildSrc` and `A/` vs `B/`?
- Unsupported right now. This would need to work once `buildSrc` is an implicit composite.

## Open questions 

- Does `-x` to exclude a task follow these same rules?
- When someone types a task path or name wrong, what does the error look like?
- What does the presentation of `tasks` look like for composite builds?
