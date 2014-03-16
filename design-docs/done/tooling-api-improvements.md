## Story: Tooling API client requests build model for old Gradle version (DONE)

This story adds support for the `GradleBuild` model for older target Gradle versions.

### Implementation

Change the implementations of `ConsumerConnection.run(type, parameters)` so that when asked for a `GradleBuild` model, they instead
request the `GradleProject` model and then convert it to a `DefaultGradleBuild` instance. See `ConnectionVersion4BackedConsumerConnection.doGetModel()`
for an example of this kind of thing.

For the `ModelBuilderBackedConsumerConnection` implementation, if the provider Gradle version supports the `GradleBuild` model (is >= 1.8-rc-1) then
forward to the provider, as it does now.

To implement this cleanly, one option might be to introduce some chain of model producers into the `ConsumerConnection` subclasses, so that each producer is
asked in turn whether it can produce the requested model. The last producer can delegate to the provider connection. Stop at the first producer that can
produce the model.

### Test cases

- For all Gradle versions, can request the `GradleBuild` model via `ProjectConnection`. This basically means removing the `@TargetGradleVersion` from
  the test case in `GradleBuildModelCrossVersionSpec`.

## Story: Deprecate support for Tooling API clients earlier than Gradle 1.2 (DONE)

When any of the following methods are called on the provider connection treat the client version as deprecated:

- `ConnectionVersion4.getModel()` and `executeBuild()`.
- `InternalConnection.getTheModel()`.
- `configureLogging(boolean)`.

Whenever an operation is invoked on the provider connection by a deprecated client version, the connection implementation should report to
the user that the client version is deprecated and support for it will be removed in Gradle 2.0.
The logging output should be received through the stream attached to `LongRunningOperation.setStandardOutput()`.

### Test cases

- Running a build generates a warning when using a client < 1.2, and does not generate a warning when using a client >= 1.2.
- Fetching a model generates a warning when using a client < 1.2, and does not generate a warning when using a client >= 1.2.

## Story: Deprecate support for Gradle versions earlier than Gradle 1.0-milestone-8 (DONE)

When the provider connection does not implement `InternalConnection` then treat the provider version as deprecated.

Whenever an operation is invoked on a deprecated provider version, the client implementation should report to the user that the provider
version is deprecated and support for it will be removed in Gradle 2.0.
The logging output should be received through the stream attached to `LongRunningOperation.setStandardOutput()`.

### Test cases

- Running a build generates a warning when using a provider version < 1.0-milestone-8, and does not generate a warning when using a provider version >= 1.0-milestone-8.
- Fetching a model generates a warning when using a provider version < 1.0-milestone-8, and does not generate a warning when using a provider version >= 1.0-milestone-8.
