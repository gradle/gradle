# CI Pipeline Configuration

## Open & import the project

In your IDEA, `File` - `Open`, select `.teamcity/pom.xml`, `import as project`, and you'll have a Maven project.

## Project structure

Mostly a standard Maven project structure. The entry point `settings.kts` defines the TeamCity project.

There are 4 subprojects in the TeamCity project hierarchy: 
* `Check` for Gradle builds
* `Promotion` for releasing Gradle versions
* `Util` for miscellaneous utilities
* `Performance` for ad-hoc performance test execution

## Develop and verify

After you make a change, you can run `mvn clean teamcity-configs:generate` to generate and verify the generated TeamCity configuration XMLs.

You also need to run `mvn clean verify` with Java 21 before committing changes.

If you have ktlint errors, you can automatically fix them by running `./mvnw ktlint:format`.

## How the configuration works

We use Kotlin portable DSL to store TeamCity configuration, which makes it easy to create a new pipeline based on a specific branch.
We have multiple pipelines: 
* `master` for the next Gradle version
* `release` for the current to be released Gradle version
* Possibly multiple `/release\dx/` for backporting to older releases
* and `xperimental` for testing changes to the DSL that can't be tested in a PR to `master`

If testing your changes in a PR does not work, coordinate with the BT Developer Productivity team and test your changes by pushing them to the `xperimental` branch:
```shell
$> git push --force origin HEAD:refs/heads/xperimental
```

Wait for TeamCity to re-generate the `Xperimental` project and test any changes you may need.
