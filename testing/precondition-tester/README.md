# Gradle / precondition Tester

This project collections information about where test preconditions can be satisfied.
We can use this information (i.e. by looking at the test results in Develocity) to see if we have any preconditions or combinations of preconditions, which are never satisfied &ndash; a situation which we otherwise would hardly notice.

This project originates from a discussion about the [@Requires](https://github.com/gradle/gradle/blob/94ebe9eca6b9baf8c53a6033009298ec671de812/subprojects/internal-testing/src/main/groovy/org/gradle/util/Requires.java), which can define preconditions for tests.

Preconditions can define and enforce conditions like:
- The test is running on Windows (`@Requires(UnitTestPreconditions.Windows)`)
- The test is running on JDK 8 compatible JDKs (`@Requires(UnitTestPreconditions.Jdk8OrLater)`)
- Arbitrary combinations of above (`@Requires([UnitTestPreconditions.Windows, UnitTestPreconditions.Jdk8OrLater])`)

The issue is that we _ignore_ tests, where the precondition is not satisfied.
Albeit being the correct solution, this can cause problems where a test is _not running anywhere_ and we don't know anything about it.

For example, a `RunsOnPDP11` precondition, which we won't satisfy anywhere as we don't run tests on such hardware, will always be ignored.

Multiple occasions happened when engineers were caught by surprise finding tests not running anywhere (e.g., tests depending on non-LTS, cleaned up JDK distributions). This project is the result of an initiative to [redesign the [recondition] system](https://github.com/gradle/gradle/pull/22885), where we aim to precisely track if all preconditions are satisfied

