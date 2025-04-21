# Navigation Bar Level Colorization - Gradle Contribution
By hitman49

## Overview
This contribution introduces navigation bar level colorization to improve visual distinction between different operation levels in Gradle's console output. The feature helps users better understand build hierarchy by applying distinct colors to different operation levels.

## Features
- Configurable colorization modes (ON/OFF/AUTO)
- Distinct color scheme for different build operation levels
- Terminal capability detection
- Integration with existing console output system
- Comprehensive test coverage

## Implementation Details

### New Components
- `NavigationBarColorization` enum for controlling colorization behavior
- Navigation bar color management in `BuildStatusRenderer`
- Color scheme configuration in `NavigationBarColors`
- Extended `LoggingConfiguration` interface with colorization settings

### Configuration Options
Users can control the feature through:
```properties
# In gradle.properties
org.gradle.console.navigation.colors=on|auto|off
```
```shell
# Via command line
gradle build --console-navigation-colors=on
```

### Color Scheme
- Root level: Cyan
- First level: Green
- Second level: Yellow
- Third level: Magenta
- Fourth level+: Blue

## Technical Implementation

### Key Components

1. **API Components** (`platforms/core-runtime/logging-api/src/main/java/...`):
   - `NavigationBarColorization.java`: Enum defining colorization modes (OFF, AUTO, ON)
   - `LoggingConfiguration.java`: Interface extended with navigation bar colorization settings

2. **Core Implementation** (`platforms/core-runtime/logging/src/main/java/...`):
   - `DefaultLoggingConfiguration.java`: Implementation of the colorization settings
   - `BuildStatusRenderer.java`: Main rendering logic for colored navigation bars
   - `NavigationBarColors.java`: Color management and text colorization utilities

3. **Testing** (`platforms/core-runtime/logging/src/integTest/groovy/...`):
   - `BuildStatusRendererIntegrationTest.groovy`: Integration tests for the feature

4. **Documentation** (`platforms/documentation/docs/src/docs/userguide/running-builds/console.adoc`):
   - User guide section explaining the feature and its configuration

## Testing
Added integration tests covering:
- Color application across different operation levels
- Console output mode compatibility
- Colorization setting behavior
- Level tracking and maintenance

## Documentation
Added user guide section in `console.adoc` covering:
- Feature overview
- Configuration options
- Default color scheme
- System requirements

## Requirements
- Gradle 8.7 or later
- Terminal with ANSI color support (for `auto` mode)
- Rich console output enabled

## Example Output
```
> Configure project :app
> compileJava        [Cyan]
  > processResources [Green]
    > copy          [Yellow]
```

## Breaking Changes
None. The feature is opt-in and defaults to AUTO mode, maintaining backward compatibility.

## Related Issues
Fixes #87: Re-color navigation bar levels

## Contributing
This contribution follows Gradle's contribution guidelines and includes:
- Complete implementation
- Comprehensive test coverage
- User documentation
- No breaking changes
- Backward compatibility

## License
This contribution is licensed under the Apache License, Version 2.0. 