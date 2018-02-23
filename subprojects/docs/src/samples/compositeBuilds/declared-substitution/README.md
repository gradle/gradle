# Declaring the dependencies substituted by an included build

By default, Gradle will configure each included build in order to determine the dependencies it can provide. The algorithm for doing this is very simple: Gradle will inspect the `group` and `name` for the projects in the included build, and substitute project dependencies for any matching external dependency.

In `dependencySubstitution` terms, the default substitutions are:
```
dependencySubstitution {
    ... for each project in included build ...
    substitute module("${project.group}:${project.name}") with project(":${project.name}")
}
```

### Declaring substitutions for included build

There are times when the default substitutions determined by Gradle are not sufficient, or they are not wanted in a particular composite. For these cases it is possible to explicitly declare the substitutions for an included build.

Take for example a single-project build 'unpublished', that produces a java utility library but does not declare a value for the `group` attribute:

build.gradle:

```
apply plugin: 'java'
```

When this build is included in a composite, it will attempt to substitute for the dependency module `undefined:unpublished` ('undefined' is the default value for `project.group`, and 'unpublished' is the root project name). Clearly this isn't going to be very useful in a composite build.

In order to use the `unpublished` library as-is in a composite build, the composing build can explicitly declare the substitutions that it provides.

```
includeBuild('../anonymous-library') {
    dependencySubstitution {
        substitute module('org.sample:number-utils') with project(':')
    }
}
```

With this configuration, the `my-app` composite build will substitute any dependency on `org.sample:number-utils` with a dependency on the root project of `anonymous-library`.
