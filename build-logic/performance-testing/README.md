# Performance Testing Plugin

## Generated JUnit4 XML report parser code

Classes in the [gradlebuild.performance.junit4](src/main/groovy/gradlebuild/performance/junit4) package were generated using
IntelliJ IDEA based on an XSD for the JUnit 4 XML report files from Jenkins
https://github.com/junit-team/junit5/issues/2625#issuecomment-850316355

The `SecureUnmarshaller` is a utility class to make sure the XML parsing is not vulnerable to the usual XML processing attacks.
