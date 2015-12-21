# Publishing and deployment of play applications

- [x] Developer builds Play application distribution
- [ ] Developer publishes Play application to a binary repository
- [ ] Bug: incorrect Play distribution created for multiproject with non-unique subproject names
- [ ] Developer packages Play distributions in different formats
- [ ] Developer controls layout of Play distribution
- [ ] Build author can describe software distributions as buildable items

## Stories

### Story: Developer builds Play application distribution

Introduce some lifecycle tasks to allow the developer to package up the Play application. For example, the developer may run `gradle stage` to stage the local application, or `gradle dist` to create a standalone distribution.

- Build distribution image and zips, as per `play stage` and `play dist`
- Default distributions are added for each play binary
- Developer can add additional content to a default distribution:
```
model {
    distributions {
        playBinary {
            contents {
                from "docs"
            }
        }
    }
}
```


#### Test cases
- Default distributions are added for each play binary
- Stage and Zip tasks are added for each distribution
- Lifecycle tasks for "stage" and "dist" are added to aggregate all distribution tasks
- Distribution base name defaults to distribution name, which defaults to play binary name
- Distribution archive name defaults to "[distribution-base-name]-[project.version].zip"
- Public assets are packaged in a separate jar with a classifier of "assets".
- Default distribution zip contains:
    - all content under a directory named "[distribution-base-name]-[project.version]"
    - jar and assets jar in "lib".
    - all runtime libraries in "lib".
    - application script and batch file in "bin".
    - application.conf and any secondary routes files in "conf".
    - any README file provided in conventional location "${projectDir}/README"
- content added to the distribution is also included in the zip
- application script and batch file will successfully run play:
    - can access a public asset
    - can access a custom route

### Bug: Incorrect Play distribution created for multiproject with non-unique subproject names

Given a multi-project build with multiple 'leaf' projects that have the same name, when using those projects as runtime dependencies (`playRun` dependencies), the generated jar names can collide when building the distribution archive.

When building the classpath for running the Play application from Gradle, the classpath uses the path to the project instead of the path to the jar under the staging area. 

#### Implementation

- When copying dependencies into the staging area, 
    - Copy all non-project dependencies without any name mangling.
    - Copy all project dependencies with the name ${path-to-project}-main.jar where path-to-project is the Project's path with : changed to . (e.g., sub1:dependency turns into sub1.dependency).

#### Test coverage
- Recreate bug with multi-project build with sub1:dependency and sub2:dependency and a root Play application.  Dependent projects can be simple "old" Java projects.  Test should look for uniquely named project dependencies in `playBinary/lib`. Each subproject can publish to a unique group:name. 

### Story: Developer packages Play distributions in different formats

Initial version of the Play distribution plugin only added staging and Zip distribution tasks. 

Add support for Tar distributions. Detailed configuration will have to be performed at the task-level for now. Later stories will make the concept of a distribution more like the relationship between Component/Variant. 

#### Test coverage
- Tar distribution follows layout/details as above (Zip/staging)
- Both Tar and Zip distributions are created when building `dist`
- Tar task can be configured to use GZIP compression.

### Story: Developer controls layout of Play distribution

Distributions put all of their contents inside a root directory (usually based on the name of the distribution or project). Provide a configuration option to change this layout to a "flat" layout.  See includeBaseDirectory for Maven's assembly plugin.

#### Test coverage
- Inspect contents of Play distribution with base directory and without.

### Story: Build author can describe software distributions as buildable items

- Introduce the concept of a distribution as a type of `ComponentSpec` (`DistributionSpec`)
- PlayApplicationSpec has a `DistributionSpec`
- For each variant produced for the PlayApplicationSpec, a `DistributionSpec` will produce one or more `DistributionBinary`.
- A `DistributionBinary` can be a directory, Zip archive, Tar archive, etc.
- Instead of configuring a `Tar` task for distribution `foo` to use GZIP, a build author would instead configure a `TarDistributionBinarySpec`.

Other details TBD. 

#### Open Issues
- A Play distribution zip, by default, contains a shared/docs directory with the scaladocs for the application.  We'll need
a scaladoc task wired in to duplicate this functionality.
- How do we de-duplicate a project dependency that uses a non-default configuration? (e.g., project(path: "sub1", configuration: "custom"))
