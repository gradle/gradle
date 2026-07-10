# Package org.gradle.internal.cc.impl.barrier

This package provides support of integrating `ConfigurationTimeBarrier` functionality into Vintage (non-CC) mode.
The barrier management here should not be used with CC enabled, because it may interfere with CC's own 
management routines.

Ideally, this all should move away as we fold the vintage mode into CC more and more.
