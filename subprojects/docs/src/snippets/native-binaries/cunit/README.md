## CUnit Sample Limitations

Currently, the Gradle model for Platform does not allow us to differentiate between different c-runtime, ABI or other binary variants.
This means that it is not possible to differentiate between a prebuilt library binary compatible with VS2010 vs VS2013.

As such, this sample will only work without modification on Windows with Visual Studio 2010. Uncomment the relevant line in the
build script to enable building with Visual Studio 2013, Cygwin or MinGW.
