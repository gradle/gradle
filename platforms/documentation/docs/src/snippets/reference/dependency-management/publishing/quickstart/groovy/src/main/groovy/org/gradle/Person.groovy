package org.gradle

class Person {
    String name

    def Person() {
        getClass().getResourceAsStream('/resource.txt').withStream {InputStream str ->
            name = str.text.trim()
        }
        getClass().getResourceAsStream('/script.groovy').withStream {InputStream str ->
            def shell = new GroovyShell()
            shell.person = this
            shell.evaluate(str.text)
        }
    }
}
