# Hierarchical composite build sample

This sample demonstrates how an existing monolithic multi-project build can be split into a composite, with sub-projects being separate builds. This permits the developer to choose which submodules will be wired in via source integration and which via a binary repository.

### Running `my-app` with dependencies from included builds

In the first instance, all of the required dependencies are present as builds in the `submodules` directory. The `settings.gradle` file in `my-app` includes each of these builds dynamically:

```
file('submodules').listFiles().each { File submoduleBuild ->
    includeBuild submoduleBuild
}
```

When the `my-app` build is executed, these submodule builds are used to generate the dependent artifacts:

```
gradle run
```

The 'dependencies' report shows the dependency substitution in action:

```
gradle dependencies --configuration compile
```

>>>

```
compile - Dependencies for source set 'main'.
+--- org.sample:number-utils:1.0 -> project :number-utils
\--- org.sample:string-utils:1.0 -> project :string-utils
     \--- org.apache.commons:commons-lang3:3.4
```

### Switching to use binary dependency

In order to avoid building a submodule on every build execution, we can switch to using a binary dependency for that submodule. To do this, we first need to publish the submodule so that it can be consumed from a repository:

```
gradle :publishDeps
```

The `publishDeps` creates and uploads the artifacts for each included build. It is defined in `my-app` as follows:

```
task publishDeps {
    dependsOn gradle.includedBuilds*.task(':uploadArchives')
}
```

Now that the submodule artifacts are available in a repository, we can remove the submodule from the build. Because the composite is configured to automatically load available submodules, this is as easy as deleting the submodule directory.

```
rm -r submodules/string-utils
gradle run
```

Note that the `number-utils` dependency is still satisfied by the included build, while the `string-utils` dependency is now resolved from the repository.

The 'dependencies' report shows the dependency substitution in action:

```
gradle dependencies --configuration compile
```

>>>

```
compile - Dependencies for source set 'main'.
+--- org.sample:number-utils:1.0 -> project :number-utils
\--- org.sample:string-utils:1.0
     \--- org.apache.commons:commons-lang3:3.4
```

### Including an external library as a submodule

The power of this configuration can be demonstrated by adding the external 'commons-lang' build directly to the composite.


```
git clone http://git-wip-us.apache.org/repos/asf/commons-lang.git submodules/commons-lang
gradle --project-dir submodules/commons-lang --no-search-upward init
gradle run
```
