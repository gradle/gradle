# Using composite builds in a hierarchical multirepo project

This sample demonstrates how composite builds can be used to develop a hierarchical project that is composed from different Git repositories. Instead of Gradle subprojects in a multiproject build, this example uses separate Gradle builds included into a composite.

In addition to the benefits of a multirepo architecture, using a Gradle composite build in this way allows a developer to easily choose which modules to develop as source dependencies and which to integrate via a binary repository.

### Running `multirepo-app` with dependencies from included builds

In the first instance, all of the required dependencies are present as builds in the `modules` directory. In a real-world example, these could well be clones of different Git repositories. 

In order to avoid hard-coding the included builds to load, the `settings.gradle` file in `multirepo-app` loads each of these builds dynamically:

```
file('modules').listFiles().each { File moduleBuild ->
    includeBuild moduleBuild
}
```

When the `multirepo-app` build is executed, these module builds are used to generate the dependent artifacts:

```
gradle run
```

And the 'dependencies' report shows the dependency substitution in action:

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

As long as the modules are available in a binary repository, the `multirepo-app` build will continue to work even if you don't have some modules available locally. In this case Gradle will use a binary dependency downloaded from a repository instead.

#### Preparing the binary repository

To demonstrate this functionality, we first need to publish each module to a binary repository. In this case we use a local file repository for this purpose:

```
gradle :publishDeps
```

The `publishDeps` creates and uploads the artifacts for each included build. It is defined in `multirepo-app` as follows:

```
task publishDeps {
    dependsOn gradle.includedBuilds*.task(':uploadArchives')
}
```

#### Removing the local module source

With module artifacts available in a repository, we can now remove the module sources from the build. Since the composite is configured to automatically load available modules, this is as easy as deleting one or more module directories.

```
rm -r modules/string-utils
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
git clone http://git-wip-us.apache.org/repos/asf/commons-lang.git modules/commons-lang --branch master --depth 1
gradle --project-dir modules/commons-lang --no-search-upward init
gradle run
```

You can see the external transitive dependency `commons-lang` being replaced with the local project dependency by running:

```
gradle dependencies --configuration compile
```
