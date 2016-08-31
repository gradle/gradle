# Defining and using a composite build

This sample shows how 2 Gradle builds that are normally developed separately and combined using binary integration can be wired together into a composite build with source integration. The `my-utils` multiproject build produces 2 different java libraries, and the `my-app` build produces an executable using functions from those libraries.

Note that the `my-app` build does not have direct dependencies on `my-utils`. Instead, it declares module dependencies on the libraries produced by `my-utils`:

```
dependencies {
    compile "org.sample:number-utils:1.0"
    compile "org.sample:string-utils:1.0"
}
```

In the following examples, we follow the workflow involved in making a change to `my-utils` and testing it out in `my-app`.

### Without a composite build

Without using a composite build, there are a number of steps involved to make a change to the library project and test it in the application. 

1. Firstly, we'll need a local repository in which to share artifacts. You can choose to use `mavenLocal()` for this purpose, or define a local repository for sharing artifacts. Either way, this will involve making a change to your build scripts specifically for the purposes of integrating these builds locally.
2. Change the sources of `Number.java`, possibly fixing a bug or making an optimization.
3. Publish the `number-utils` library from `my-utils` to the local repository:

```
cd my-utils
gradle uploadArchives
```

4. Run the `my-app` application, resolving from the local repository:
```
cd ../my-app
gradle run
```

### Using command-line composite build

When using a composite build, no shared repository is required for the builds, and no changes need to be made to the build scripts.

1. Change the sources of `Number.java`
2. Run the `my-app` application, including the `my-utils` build.

```
cd my-app
gradle --include-build ../my-utils run
```

Using *dependency substitution*, the module dependencies on the util libraries are replaced by project dependencies on `my-utils`.

### Convering `my-app` to a composite build

It's possible to make the above arrangement persistent, by making `my-app` a composite build that includes `my-utils`. 

```
cd my-app
echo "includeBuild '../my-utils'" >> settings.gradle
gradle run
```

With this configuration, the module dependencies from `my-app` to `my-utils` will always be substituted with project dependencies.

While simple, this approach has the downside of modifying the `my-app` build.

### Using separate composite build

It is also possible to create a separate composite build that includes both the `my-app` and `my-utils` builds. This approach allows any number of build composites can be defined and persisted for the same set of builds.

```
rootProject.name='my-composite'

includeBuild '../my-app'
includeBuild '../my-utils'
```

Note that it is not yet possible to execute tasks in an included build from the command line. Instead, the build user must execute tasks defined in the composite build, and these tasks must declare dependencies on tasks in the included builds.

```
task run {
    dependsOn gradle.includedBuild('my-app').task(':run')
}
```

```
cd composite
gradle run
```

We are working on a mechanism to permit tasks in included builds to be referred to directly on the command line. Stay tuned!

### Make a bugfix for an external library

```
git clone http://git-wip-us.apache.org/repos/asf/commons-lang.git
gradle -P commons-lang init
cd composite
gradle --include-build '../commons-lang' run
```
