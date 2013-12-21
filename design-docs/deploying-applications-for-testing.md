
This specification describes a plan for improving support for deploying applications for the purposes of manual and automated testing.

# Use cases

The scope of this feature covers deploying one or more applications in order to test them.

This feature covers only deployment for testing, both manual and automated. Out of scope for this feature is any form of production deployment.
The general concepts introduced here should be able to be extended to handle production deployment in the future.

## Testing a web application

A web application implemented as a WAR runs in a web container. The web container must be started before the web application can be deployed to it. At the end of the
build, the web container should be stopped.

1. Start web container.
2. Build web application.
3. Deploy web application.
4. Run one or more test suites.
5. Undeploy the web application.
6. Stop web container.

Starting the web container and building the web application can happen concurrently.

The web container may run in-process or may be run as a separate process. The clean up steps may not be required for certain containers. For example,
stopping an in-process container will also undeploy the web application.

## Testing an Android application

An Android application runs in an emulator:

1. Start emulated device.
2. Install application into the emulator.
3. Install test application into the emulator.
4. Run the test suite.
5. Uninstall the test application and the application.
6. Repeat steps 2. - 5. for each variant of the application.
7. Stop emulated device.

Steps 2. - 5. cannot interleave across variants. For example, a debug and release build of a given application cannot be installed
in the same emulator instance at the same time. Installing one will replace the other. So, it is important that each variant is
installed, tested and uninstalled before proceeding to the next variant.

## Testing a web application that uses a database

Some web applications require a database instance to be set up with a certain schema and test data. The database server must be started and a
database instance created before the application can be started. At the end of the build, the database instance should be cleaned up.

1. Start database server.
2. Create database instance.
3. Create schema.
4. Populate test data.
5. Deploy web application.
6. Run one or more test suites.
7. Undeploy web application.
8. Remove database instance.
9. Stop database server.

Preparing the database instance can happen concurrently with building the web application and starting the web container. Cleaning up the database
instance can happen concurrently with cleaning up the web container (but not cleaning up the web application).

The cleanup steps may not be required for certain database implementations.

## Testing a web application that uses another web application

Some web applications use some web service, sometimes a real implementation and sometimes a mock implementation. The web service implementation must
be deployed before the application can be started. At the end of the build, the web service should be cleaned up.

1. Start web container.
2. Build the web service application.
3. Deploy the web service application.
4. Build the web application.
5. Deploy the web service application.
6. Run one or more test suites.
7. Undeploy the web application.
8. Undeploy the web service application.
9. Stop the web container.

Building and deploying the web service application can happen concurrently with building the web application.

## Further use cases

* Testing web applications that are not implemented as a WAR.
* Testing other types of applications, such as a native service.
* Web container should be installed before being started.
* Web container or database instance is not managed by the build.

# Implementation plan

The core of the implementation is to introduce the concept of deployments. A deployment is some executable thing running at some location. A deployment may provide zero or more services,
and may depend on zero or more other services. In this way, a graph of deployments is defined.

At build time, the deployment dependency graph can be taken and translated into a task graph.

## Story: Allow the deployments required by a task to be declared

1. Add a `Deployment` interface. This specifies two properties: the set of tasks required to set up the deployment, and the set of tasks required to
   clean up the deployment.
2. Add a `@Requires` annotation. This can be attached to any task property whose value can be converted to a collection of `Deployment` instances.
3. Add some way to create a default `Deployment` implementation.
4. When adding a task to the task graph:
    1. Validate that each task property marked with `@Requires` has a non-null value, unless marked with `@Optional`
    2. Convert the values of all task properties marked with `@Requires` to a set of `Deployment` instances.
    3. Add the set up tasks of each deployment to the task graph, and add a dependency from the task to each set up task.
    4. Add the clean up tasks of each deployment to the task graph, and add a dependency from each cleanup task to the task.
5. Add `requires` properties to `Test` and `Exec` task types.

To convert an arbitrary value to a set of `Deployment` instances:

1. If the value is a collection or array, recursively convert the elements of the collection and add to the result.
2. If the value is a closure or Callable, recursively convert the result of the value's `call()` method and add to the result.
3. If the value is a `Deployment`, add value to the result.
4. If the value is a map, create a default `Deployment` implementation and configure it using the map.
5. If the value is `null`, use an empty collection.

More conversions can be added later. For example, the Jetty plugin might add some way to take a WAR and adapt it to a deployment that
runs the web application in an embedded Jetty container.

### User visible changes

To deploy a web application to an in-process Jetty container while integration tests are running:

    apply plugin: 'war'
    apply plugin: 'jetty'

    jettyRunWar {
        daemon = true
    }

    def deployment = project.deployment(setup: jettyRunWar, cleanup: jettyStop)

    task integTest(type: Test) {
        requires deployment
        systemProperty 'web-app-url', "http://localhost:${jettyRunWar.httpPort}/${jettyRunWar.contextPath}"
    }

Running `gradle integTest` will run `jettyRunWar`, `integTest` and `jettyStop` in this order. There are no constraints on when the dependencies of these
tasks will be executed.

## Story: Declaring mutually exclusive deployments

Some deployments cannot run at the same time. For example, two web applications cannot be deployed at the same context path in the same web container. Or
two Android applications with the same package identifier cannot be installed in the same device (emulator).

1. Add an opaque endpoint property to `Deployment`.
2. Modify task scheduling so that the setup and cleanup tasks of deployments with the same endpoint are not interleaved. One option is to schedule
   the setup tasks immediately before the tasks that require them, and the cleanup task immediately after.

### User visible changes

To test two variants of an Android application (this code would end up in a plugin, rather than a build script):

    def debugApp = project.deployment(setup: installDebug, cleanup: uninstallDebug, endpoint: android.packageName)
    def releaseApp = project.deployment(setup: installRelease, cleanup: uninstallRelease, endpoint: android.packageName)

    task testDebug(type: AndroidTest) {
        requires debugApp
    }

    task testRelease(type: AndroidTest) {
        requires releaseApp
    }

    check {
        dependsOn testDebug, testRelease
    }

Running `gradle check` would run `installDebug`, `testDebug`, `uninstallDebug` and then `installRelease`, `testRelease`, `uninstallRelease` in this order.

## Story: Clean up deployments on failure

A deployment has been set up, then the clean up tasks for that deployment should be run, regardless of whether the tasks that use the deployment are
executed successfully or not.

The dependencies of a clean up task should be executed, and the clean up task should not be executed if any of its dependencies cannot be executed,
similar to when `--continue` is being used.

### User visible changes

Given the web app example above, running `gradle integTest` will run `jettyStop` regardless of whether the `integTest` task succeeds or fails.

## Story: Deployments are not set up when the tasks that require them are up-to-date

When a task requires a deployment, and that task is up-to-date, then skip the deployment's set up tasks and clean up tasks and their dependencies.

### User visible changes

Given the web app example above, running `gradle integTest` will not run `war`, `jettyRunWar`, or `jettyStop` when the `integTest` task is up-to-date.

## Story: Deployments that require other deployments

Allow a `Deployment` subtype to

### User visible changes

## Story: Jetty plugin provides web deployments

### User visible changes

## Story: Add task graph primitives as public features

### User visible changes

## Story: Deployment inputs are treated as inputs of tasks that use the deployment

### User visible changes

# Open issues

