# What is this?

The spec is about improving the general logging functionally in Gradle.

# Why?

* Many User/Customer/Traning attendees asked for more fine grained logging functionality like known from well known logging frameworks like log4j / logback, ...
* It's hard to trace specific logging messages as we currently just support global log levels and not fine grained (e.g. package scoped) log levels
* Adding custom logging appender is not supported

# Related Issues in the Gradle issue tracker

- [GRADLE-1196: Support for putting debug build log output to file](http://issues.gradle.org/browse/GRADLE-1196)
- [GRADLE-2658: Cannot set log level for a specific task](http://issues.gradle.org/browse/GRADLE-2658)
- [GRADLE-1384: Desire ability to have better control of logging, especially for Ivy](http://issues.gradle.org/browse/GRADLE-1384)
- [GRADLE-2818: Change logging level of a task](http://issues.gradle.org/browse/GRADLE-2818)

# Use Cases

* As a Gradle User I want to configure the gradle different log levels for core and my plugin code
* As a Gradle User I want to redirect specific log messages (e.g. to a file)
* My Gradle Build should be integrated with the logging infrastructure I want to configure
* As a Gradle User I want to set different logging configurations for different environments (e.g. on CI Server, on Dev box)  


# Stories

## Allow pluging in custom logging framework

The current Gradle logging infrastructure is based on slf4j which is hidden from the user. Allow pluging in a custom logging configuration + logging framework using the slf4j mechanism (adding slf4j adapter to classpath) seems the straight forward solution. Furthermore people are used to that mechanism.

##Implementation Plan

TODO look into the current gradle logging for coming up with a detailed plan)


