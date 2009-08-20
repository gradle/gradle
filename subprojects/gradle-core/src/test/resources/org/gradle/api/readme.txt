ClasspathTester.dat is a .class file with s modified suffix, so that it is not interpreted as a classfile.
We need it for testing our buildscript classloading. We hsve for testing a special InputStreamClassloader, which
is going to load the ClasspathTester.dat into the JVM. This class has one method:

public int testMethod(), which return 16.