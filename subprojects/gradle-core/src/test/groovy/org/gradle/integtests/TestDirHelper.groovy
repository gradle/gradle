package org.gradle.integtests

class TestDirHelper {
    def TestFile baseDir

    def TestDirHelper(TestFile baseDir) {
        this.baseDir = baseDir
    }

    def apply(Closure cl) {
        cl.delegate = this
        cl.resolveStrategy = Closure.DELEGATE_FIRST
        cl()
    }

    def file(String name) {
        TestFile file = baseDir.file(name)
        file.write('some content')
        file
    }

    def methodMissing(String name, Object args) {
        if (args.length == 1 && args[0] instanceof Closure) {
            baseDir.file(name).create(args[0])
        }
        else {
            throw new MissingMethodException(name, getClass(), args)
        }
    }
}
