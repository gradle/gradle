
This feature allows Gradle to execute tests from the current workspace remotely on a jenkins slave.

# Use cases

1. I have some code to commit and want to run an extensive Testing suite beforehand, to make sure that I do not break the CI build. There are a number of idle Jenkins slaves available, which I can use to run the build in parallel without committing to version control.
2. I want to run a set of GUI tests which capture the mouse. I can run these on Jenkins and keep on working while they complete.
3. A gradle build running on Jenkins can use this feature to distribute the Tests on demand to other Jenkins slaves, without creating extra Jobs for this.

# Implementation approach

Jenkins and Gradle need to be extended. The basic idea is that the Gradle-Process communicates with Jenkins via jenkins-remoting. In order to do this we need an Channel from the local Gradle process to the Jenkins-slave where the tests should run. When the channel is established, then Gradle will send the Test-Classes to the slave and execute them there. As soon as the Test is finished, the Test-Result is transported back to the Gradle process.

## Create initial channel

The basic idea here is that Gradle triggers a build which has a new BuildStep contributed by a Jenkins Plugin which connects a Channel to a JVM running on an computer witha certain ip and port. This information is provided by Gradle as a Build parameter when triggering the build. Gradle will then open a port and wait for the connection from Jenkins. Jenkins will assign the Job to a slave and then the build will connect to the Gradle Process.

## Execute Remote Tests

On the Gradle side we will implement Factory<TestClassProcessor> which will execute test-classes on Jenkins. We could do this either by pre-loading all classes in the test-classpath on the remote side and then starting a JVM with the corresponding Classpath or by using the auto-loading feature of the Channel.
It is probably a good idea to transfer the test-classpath directly to the slave via the Channel.
Then we would need to use this TestClassProcessor in some way in the Test-Task. One option would be to replace the DefaultTestExecutor by another implementation which uses the new TestClassProcessor. Other possibilities?

It would also be possible to implement WorkerProcess via the Jenkins-remoting Channel and create an own implementation of WorkerProcessBuilder. This would then be plugged into the DefaultTestExecutor.

## Send back test results

The Test-Results will be sent back via the Channel via the TestResultProcessor which is known to the TestClassProcessor.

## Configuration of the new Test-Task

It should be possible to run the tests on multiple slaves concurrently. This concurrency needs to be configured in the Test-Task. I think that we can reuse the RestartEveryNTestClassProcessor and MaxNParallelTestClassProcessor to implement this. On each slave one still wants to have the traditional forking options - i.e. execute tests in parallel in multiple jvms. So I believe we need to use RestartEveryNTestClassProcessor and MaxNParallelTestClassProcessor multiple times.

# Open issues

## How should disconnection from both sides of the Channel be handled?
## If the user cancels the Gradle-build, what should happen?
## If one of the Jobs in Jenkins is cancelled, what should happen?
## Implement WorkerProcess or TestClassProcessor
## How to integrate into the Test-Task
