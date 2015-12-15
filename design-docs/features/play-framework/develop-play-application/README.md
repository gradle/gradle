# Build author develops Play application in IDE

- [x] Developer views build failure message in Play application
- [ ] IDE imported Play project has all sources configured for module
- [ ] IDE imported Play project has correct external dependencies configured
- [ ] IDE imported Play project has correct local component dependencies configured
- [ ] IDE imported Play project has correct source and target JVM configured
- [ ] Developer views Java and Scala compilation failure in Play application
- [ ] Developer views Asset compilation failures in Play application
- [ ] Developer views build failure stack trace in Play application

## Open Issues

* Consider the case of `otherTask` that depends on `runPlay`. If `otherTask` fails, should the Play application report the build failure? (Yes)
* In the case of multi-project builds, if you have multiple Play applications that are configured so they can run at the same time (different ports), if one project fails to build, should we report that as failures in all of the other applications? (Yes)

## Stories

### Story: Developer views build failure message in Play application

Adapt a generic build failure exception to a `PlayException` that renders the exception message.

#### Test Coverage

* After the Play application has started once successfully in continuous mode (`gradle -t runPlay`), when the source is changed in a way to cause a compilation error:
   * ~~`runPlay` should not run again~~
   * ~~Application should return an error page with build failed exception message~~
   * ~~Fixing compilation error should return application into "good" state~~
   * ~~Check Java, Scala, Asset, Route and Twirl compilation failures.~~

### Story: Developer views Java and Scala compilation failure in Play application

Adapt compilation failures so that the failure and content of the failing file is displayed in the Play application.

### Story: Developer views Asset compilation failures in Play application

Failures in CoffeeScript compilation are rendered with content of the failing file.
This mechanism will be generally applicable to custom asset compilation tasks.

### Story: Developer views build failure stack trace in Play application

### Story: Developer imports Play application project into IDE (* multiple stories *)

## Future stories and features

### Developer runs Scala interactive console

Allow the Scala interactive console to be launched from the command-line.

- Build the project's main classes and make them visible via the console
- Add support for client-side execution of actions
- Model the Scala console as a client-side action
- Remove console decoration prior to starting the Scala console


