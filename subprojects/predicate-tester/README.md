# Gradle / Predicate Tester

## What is this project do?

This project collections information about test predicates used by testing if they can be satisfied.
We can use this information (as it lands in Gradle Enterprise) to see if we have any predicates, which are never satisfied &ndash; a situation which we otherwise would hardly notice.

## Why is this project doing that?

This project originates from a discussion about the [@Requires](https://github.com/gradle/gradle/blob/94ebe9eca6b9baf8c53a6033009298ec671de812/subprojects/internal-testing/src/main/groovy/org/gradle/util/Requires.java) file, which can define predicates for tests.
E.g.
- The test is running on Windows
- The test is running on JDK 8
- Arbitrary combinations of above

The issue is that we _ignore_ tests. Albeit being the correct solution, can cause problems where a test is _not running anywhere_ and we don't know anything about it.
For example, a `RunsOnPDP11` predicate, which we won't satisfy anywhere as we don't run tests on such hardware, will always be ignored.

Multiple occasions happened when engineers were caught by surprise finding tests not running anywhere (e.g., tests depending on non-LTS, cleaned up JDK distributions).

Therefore, we redesigned the predicate system, and created this project to track the usages.

