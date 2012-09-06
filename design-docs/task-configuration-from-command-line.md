command line options & task configuration

# State of things

State of things now: There's an internal @CommandLineOption(options='theOption', description='some description') annotation that:

1. makes 'theOption' usable on command line via --theOption
2. configures the boolean setter 'setTheOption' with true (this setter is annotated with @CommandLineOption)
3. is pretty much limited to implict tasks only because it only configures if single task is selected.
This means that if multiple tasks with same name are selected for execution (name-matching execution) then this option will not configure any of the tasks.
It might have been implemented this way to avoid extensive reflecting on many types to find method annotated with @CommandLineOption.

# Implementation plan

Here's what is needed for the 'dependencyInsightReport' that renders the 'inverted' dependency tree(s) for given dependency (more about that - see the the spec about improving dependency reporting).
This report needs some support on the command line.

1. Make the command line option can carry the String value. E.g. remove the current limitation of only 'boolean' fields supported.
2. Work well if if name-matching selection returns multiple tasks from different projects. I assume we want to configure them all.
3. Make sure the performance does not regress when we start reflecting on types more.
4. Handle the case when the option is missing the value.

# Potential further steps

1. Figure out what to do if multiple tasks of different types are scheduled for execution *and* there are clashing command line options.
For example, 'foo' option that requires a string value in one task type but is a boolean flag in some other task type.
This is not a blocker because we have very little command line options, yet.
2. Decide on precedence order if task is configured from the command line and in the build script. Add coverage, etc.
3. Figure out how the options are documented, wheter it is a 'tasks' view, etc. Currently it will be user guide / dsl reference.
4. Nice error reporting for misspelled options.
5. Nice error reporting for options that are required but not provided.

Finally, we do plan to have some generic support for task properties configurable from the command line.
The implementation for the dependency report should be first steps into that direction.
However, we should approach incrementally, not overcook it initially and make sure that the first client
for the feature (dependency insight report) is happy.