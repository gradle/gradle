
# Open issues

- When running with the daemon, use of EOT to cancel the build requires an extra 'enter' due to the line buffering performed by the daemon client

> In practice, this isn't too big of an issue because most shells intepret ctrl-d as closure of stdin, which doesn't suffer this buffering problem.

- Interactive cancellation on windows requires pressing enter (https://issues.gradle.org/browse/GRADLE-3311)
