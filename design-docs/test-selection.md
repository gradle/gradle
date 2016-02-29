
Ability to control a set of tests to be executed via configuration and command line options and DSL.

# Motivation
* In order to be able to detect, verify flaky tests it should be possible to exclude some tests from the testsuite without making changes in the code.

# Design
There are four types of filters available: include/exclude for categories and individual filters.
All filters could be provided via command line options or via Gradle DSL (`build.gradle`).

## Comparison with other tools

TBD: double check capabilities of tools/frameworks listed in the table.

| Framework        | Includes           | Excludes | Regexps in includes/excludes | Categories | Ability to include/exclude multiple tests (categories) | Notes |
| ------------- |:-------------:|   -----:|-----:|-----:|-----:|--:|
| Javascript frameworks     | All the considered frameworks doesn't support any filtering (all the tests in projects are executed during every run). ||||||
| Maven                     | Yes | Yes | Yes | Yes | Yes | [Stackoverflow: How can I skip test in some projects via command line options?](http://stackoverflow.com/questions/9123075/maven-how-can-i-skip-test-in-some-projects-via-command-line-options), [Stackoverflow: How to exclude all JUnit4 tests with a given category using Maven surefire?](http://stackoverflow.com/questions/14132174/how-to-exclude-all-junit4-tests-with-a-given-category-using-maven-surefire) |
| SBT                       | Yes ([Scalatest](http://stackoverflow.com/questions/11159953/scalatest-in-sbt-is-there-a-way-to-run-a-single-test-without-tags)) | No | Yes (For classes only?) | Yes | Yes | [SBT: Testing](http://www.scala-sbt.org/0.13.5/docs/Detailed-Topics/Testing.html) |
| RSpec (Ruby)               | Yes:  [RSpec filtering](http://www.relishapp.com/rspec/rspec-core/v/3-4/docs/filtering), [RSpec Command line](http://www.relishapp.com/rspec/rspec-core/v/3-4/docs/command-line/pattern-option) |||   [Yes](http://www.relishapp.com/rspec/rspec-core/v/3-4/docs/command-line/tag-option) | Yes | [PR 1671 Exclude Pattern](https://github.com/rspec/rspec-core/pull/1671) |

## Gradle implementation
### Command line options

* `--include-tests` - pattern of test names which needs to be included in run. By default all the tests are included.
* `--exclude-tests` - allows to specify a test name or pattern for tests to be excluded.
* _Exclude filters_ are applied to the set of tests which were selected for execution by _include filters_
* It's possible to specify full test name or pattern (`com.FooTest.*in*`, `***.foo`)
* It's possible to pass multiple include/exclude parameters simultaneously: `--include-tests *.foo.* -include-tests *.bar.* --exclude-tests ***.fooBar`
* scanForTestClasses


* Rules provided via command line have higher priority over rules in build file.


### Gradle DSL
```
test {

    filter {
        includeTests "*UiCheck"   // Currently: includeTestsMatching
        includeTests "*DaoCheck"
        excludeTests "*Account*Check"

        includeCategories 'org.gradle.junit.CategoryA'
        excludeCategories 'org.gradle.junit.CategoryB'
    }
}
```

# Implementation plan
## Stories
### Add ability to exclude tests
* Ability to specify tests to exclude via command line (`--exclude-tests` parameter)
* Ability to specify tests to exclude via DSL

### Add `--include-tests` option
* Deprecate `--tests`
* Deprecate -Dtest.single

### Add support of multiple command line parameters for include/exclude values

## Testing

## Won't be implemented

# Open issues





