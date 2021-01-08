
This project contains the Build Scan performance test suite. For information about the test infrastructure, see [performance](../performance) and [internal-performance-testing](../internal-performance-testing)

Note that when running these tests locally, they will use the version of the build scan plugin in `../incoming/plugin.json`.  On CI, this file is populated as an artifact dependency from an upstream build.  To run locally, you will need to set this value whatever version of the build scan plugin you want to test with (whatever value is in source control is likely quite out of date).  Note also that when this value changes, you will need to clean and recreate the test projects in `templates.gradle`.
