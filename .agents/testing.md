# Testing

## Test Framework

Tests are written in [Spock](https://spockframework.org/spock/docs/).
The project uses a combination of unit tests and integration tests.
Integration tests run an entire Gradle build with specific build files and check its effects (effectively end-to-end tests).

## Running Tests

When running tests, unless the changes have a wide impact, it's best to target specific tests by using the `--tests` argument.

### Unit tests
```
./gradlew :<subproject>:test
```
For example: `./gradlew :launcher:test`

### Integration tests
```
./gradlew :<subproject>:forkingIntegTest
```
and
```
./gradlew :<subproject>:configCacheIntegTest
```
It is important to verify that any test written also works when configuration cache is turned on.

#### Testing with a different Java version
```
./gradlew :<subproject>:<integTestTask> -PtestJavaVersion=21
```

## Writing Tests

### Prefer integration tests
Write integration tests that exercise a real Gradle build and verify external state (e.g., produced files).
Avoid testing internal state in integration tests — use unit tests for that.

### Unit tests for isolated logic
Use unit tests when there are many input combinations that would be impractical to cover with integration tests.

### Look at existing tests first
Before writing a new test, look at existing tests covering similar functionality for patterns and structure.

### Keep tests simple
- Don't over-engineer tests with deeply-nested helpers
- Repetition is acceptable if it improves readability
- Use [data-driven tables](https://spockframework.org/spock/docs/2.3/data_driven_testing.html#data-tables) for multiple scenarios

### Helpers and traits
Reusable test helpers are in `testing/internal-integ-testing/src/main/groovy/org/gradle/`.
Use them where appropriate but don't over-abstract.

### Don't assert inside build scripts under test
Assertions in Gradle build scripts under test can be silently skipped and give poor error messages.
Instead, print data via stdout and verify output in the test, or test via build operations.

### Link tests to GitHub issues
Use `@spock.lang.Issue` to link tests to bugs:
```groovy
@Issue("https://github.com/gradle/gradle/issues/8840")
def "can use exec in settings"() { ... }
```
