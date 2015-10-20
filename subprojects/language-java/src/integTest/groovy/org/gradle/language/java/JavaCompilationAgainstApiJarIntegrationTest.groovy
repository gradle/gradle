/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.language.java

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.LeaksFileHandles
import spock.lang.Ignore

@LeaksFileHandles
class JavaCompilationAgainstApiJarIntegrationTest extends AbstractIntegrationSpec {
    void applyJavaPlugin(File buildFile) {
        buildFile << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}
'''
    }

    void updateFile(String path, String contents) {
        // add a small delay in order to avoid FS synchronization issues
        // the errors often look like this:
        // bad class file: /home/cchampeau/DEV/PROJECTS/GITHUB/gradle/subprojects/language-java/build/tmp/test files/JavaCompilationAgainstApiJarIntegrationTest/consuming_source_is...s_changes/qgrf/build/jars/myLibApiJar/myLib.jar(com/acme/Person.class)
        // unable to access file: corrupted zip file
        // and the reason is unclear now.
        // TODO: investigate this issue
        sleep(1000)
        file(path).write(contents)
    }

    def "fails compilation if trying to compile a non-API class"() {
        given: "a library that declares a public API"
        applyJavaPlugin(buildFile)
        buildFile << '''
model {
    components {
        myLib(JvmLibrarySpec) {
            api {
                exports 'com.acme'
            }
        }
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'myLib'
                    }
                }
            }
        }
    }
}
'''
        file('src/myLib/java/com/acme/Person.java') << '''package com.acme;

public class Person {}
'''
        file('src/myLib/java/internal/PersonInternal.java') << '''package internal;
import com.acme.Person;

public class PersonInternal extends Person {}
'''

        and: "another library trying to consume a non-API class"
        file('src/main/java/com/acme/TestApp.java') << '''package com.acme;

import internal.PersonInternal;

public class TestApp {
    private PersonInternal person;
}

'''

        expect:
        succeeds ':myLibJar'

        and:
        fails ':mainJar'
    }

    def "consuming source is recompiled when API class changes"() {
        given: "a library that declares a public API"
        applyJavaPlugin(buildFile)
        buildFile << '''
model {
    components {
        myLib(JvmLibrarySpec) {
            api {
                exports 'com.acme'
            }
        }
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'myLib'
                    }
                }
            }
        }
    }
}
'''
        file('src/myLib/java/com/acme/Person.java') << '''package com.acme;

public class Person {}
'''
        file('src/myLib/java/internal/PersonInternal.java') << '''package internal;
import com.acme.Person;

public class PersonInternal extends Person {}
'''

        and:
        file('src/main/java/com/acme/TestApp.java') << '''package com.acme;

import com.acme.Person;

public class TestApp {
    private Person person;
}

'''

        expect:
        succeeds ':myLibJar'

        and:
        succeeds ':mainJar'

        when:
        updateFile('src/myLib/java/com/acme/Person.java', '''package com.acme;

public class Person {
    public String name;
}
''')
        then:
        succeeds ':mainJar'

        and:
        executedAndNotSkipped(':compileMyLibJarMyLibJava')
        executedAndNotSkipped(':compileMainJarMainJava')
    }

    def "consuming source is not recompiled when non-API class changes"() {
        given: "a library that declares a public API"
        applyJavaPlugin(buildFile)
        buildFile << '''
model {
    components {
        myLib(JvmLibrarySpec) {
            api {
                exports 'com.acme'
            }
        }
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'myLib'
                    }
                }
            }
        }
    }
}
'''
        file('src/myLib/java/com/acme/Person.java') << '''package com.acme;

public class Person {}
'''
        file('src/myLib/java/internal/PersonInternal.java') << '''package internal;
import com.acme.Person;

public class PersonInternal extends Person {}
'''

        and:
        file('src/main/java/com/acme/TestApp.java') << '''package com.acme;

import com.acme.Person;

public class TestApp {
    private Person person;
}

'''

        expect:
        succeeds ':myLibJar'

        and:
        succeeds ':mainJar'

        when:
        updateFile('src/myLib/java/internal/PersonInternal.java', '''package internal;
import com.acme.Person;

public class PersonInternal extends Person {
    private String name;
}
''')
        then:
        succeeds ':mainJar'

        and:
        executedAndNotSkipped(':compileMyLibJarMyLibJava')
        skipped(':compileMainJarMainJava')
    }

    def "consuming source is not recompiled when comment is changed in API class"() {
        given: "a library that declares a public API"
        applyJavaPlugin(buildFile)
        buildFile << '''
model {
    components {
        myLib(JvmLibrarySpec) {
            api {
                exports 'com.acme'
            }
        }
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'myLib'
                    }
                }
            }
        }
    }
}
'''
        file('src/myLib/java/com/acme/Person.java') << '''package com.acme;

public class Person {
    private String name;
    public String toString() { return name; }
}
'''

        and:
        file('src/main/java/com/acme/TestApp.java') << '''package com.acme;

import com.acme.Person;

public class TestApp {
    private Person person;
}

'''

        expect:
        succeeds ':myLibJar'

        and:
        succeeds ':mainJar'

        when:
        updateFile('src/myLib/java/com/acme/Person.java', '''package com.acme;

public class Person {
    private String name;

    // this is a comment that will introduce a line number change
    // so the .class files are going to be different
    public String toString() { return name; }
}
''')
        then:
        succeeds ':mainJar'

        and:
        executedAndNotSkipped(':compileMyLibJarMyLibJava')
        skipped(':compileMainJarMainJava')
    }

    def "consuming source is not recompiled when method body of API class changes"() {
        given: "a library that declares a public API"
        applyJavaPlugin(buildFile)
        buildFile << '''
model {
    components {
        myLib(JvmLibrarySpec) {
            api {
                exports 'com.acme'
            }
        }
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'myLib'
                    }
                }
            }
        }
    }
}
'''
        file('src/myLib/java/com/acme/Person.java') << '''package com.acme;

public class Person {
    private String name;
    public String toString() { return name; }
}
'''

        and:
        file('src/main/java/com/acme/TestApp.java') << '''package com.acme;

import com.acme.Person;

public class TestApp {
    private Person person;
}

'''

        expect:
        succeeds ':myLibJar'

        and:
        succeeds ':mainJar'

        when:
        updateFile('src/myLib/java/com/acme/Person.java', '''package com.acme;

public class Person {
    private String name;
    public String toString() { return "Name: "+name; }
}
''')
        then:
        succeeds ':mainJar'

        and:
        executedAndNotSkipped(':compileMyLibJarMyLibJava')
        skipped(':compileMainJarMainJava')
    }

    @Ignore("Requires a better definition of what ABI means")
    def "consuming source is not recompiled when overriding a method from a superclass"() {
        given: "a library that declares a public API"
        applyJavaPlugin(buildFile)
        buildFile << '''
model {
    components {
        myLib(JvmLibrarySpec) {
            api {
                exports 'com.acme'
            }
        }
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'myLib'
                    }
                }
            }
        }
    }
}
'''
        file('src/myLib/java/com/acme/Person.java') << '''package com.acme;

public class Person {
    private String name;
}
'''

        and:
        file('src/main/java/com/acme/TestApp.java') << '''package com.acme;

import com.acme.Person;

public class TestApp {
    private Person person;
}

'''

        expect:
        succeeds ':myLibJar'

        and:
        succeeds ':mainJar'

        when:
        updateFile('src/myLib/java/com/acme/Person.java', '''package com.acme;

public class Person {
    private String name;
    public String toString() { return "Name: "+name; }
}
''')
        then:
        succeeds ':mainJar'

        and:
        executedAndNotSkipped(':compileMyLibJarMyLibJava')
        skipped(':compileMainJarMainJava')
    }

    def "consuming source is recompiled when signature of API class changes"() {
        given: "a library that declares a public API"
        applyJavaPlugin(buildFile)
        buildFile << '''
model {
    components {
        myLib(JvmLibrarySpec) {
            api {
                exports 'com.acme'
            }
        }
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'myLib'
                    }
                }
            }
        }
    }
}
'''
        file('src/myLib/java/com/acme/Person.java') << '''package com.acme;

public class Person {
    private String name;
    public void sayHello() {
        System.out.println("Hello, "+name);
    }
}
'''

        and:
        file('src/main/java/com/acme/TestApp.java') << '''package com.acme;

import com.acme.Person;

public class TestApp {
    private Person person;
}

'''

        expect:
        succeeds ':myLibJar'

        and:
        succeeds ':mainJar'

        when:
        updateFile('src/myLib/java/com/acme/Person.java', '''package com.acme;

public class Person {
    private String name;
    public void sayHello(String greeting) {
        System.out.println(greeting + ", " + name);
    }
}
''')
        then:
        succeeds ':mainJar'

        and:
        executedAndNotSkipped(':compileMyLibJarMyLibJava')
        executedAndNotSkipped(':compileMainJarMainJava')
    }

    def "consuming source is not recompiled when signature of API class doesn't change"() {
        given: "a library that declares a public API"
        applyJavaPlugin(buildFile)
        buildFile << '''
model {
    components {
        myLib(JvmLibrarySpec) {
            api {
                exports 'com.acme'
            }
        }
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'myLib'
                    }
                }
            }
        }
    }
}
'''
        file('src/myLib/java/com/acme/Person.java') << '''package com.acme;

public class Person {
    private String name;
    public void sayHello() {
        System.out.println(greeting());
    }

    private String greeting() {
        return "Hello, "+name;
    }
}
'''

        and:
        file('src/main/java/com/acme/TestApp.java') << '''package com.acme;

import com.acme.Person;

public class TestApp {
    private Person person;
}

'''

        expect:
        succeeds ':myLibJar'

        and:
        succeeds ':mainJar'

        when:
        updateFile('src/myLib/java/com/acme/Person.java', '''package com.acme;

public class Person {
    private String name;
    public void sayHello() {
        System.out.println("Hello, " + name);
    }
}
''')
        then:
        succeeds ':mainJar'

        and:
        executedAndNotSkipped(':compileMyLibJarMyLibJava')
        skipped(':compileMainJarMainJava')
    }

    @Ignore("This can randomly pass, we need to make it guaranteed in stub generation")
    def "consuming source is not recompiled when order of public methods of API class changes"() {
        given: "a library that declares a public API"
        applyJavaPlugin(buildFile)
        buildFile << '''
model {
    components {
        myLib(JvmLibrarySpec) {
            api {
                exports 'com.acme'
            }
        }
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'myLib'
                    }
                }
            }
        }
    }
}
'''
        file('src/myLib/java/com/acme/Person.java') << '''package com.acme;

public class Person {
    private String name;
    public void sayHello() {
        System.out.println(greeting());
    }

    public String greeting() {
        return "Hello, "+name;
    }
}
'''

        and:
        file('src/main/java/com/acme/TestApp.java') << '''package com.acme;

import com.acme.Person;

public class TestApp {
    private Person person;
}

'''

        expect:
        succeeds ':myLibJar'

        and:
        succeeds ':mainJar'

        when:
        updateFile('src/myLib/java/com/acme/Person.java', '''package com.acme;

public class Person {
    private String name;

    public String greeting() {
        return "Hello, "+name;
    }

    public void sayHello() {
        System.out.println(greeting());
    }
}
''')
        then:
        succeeds ':mainJar'

        and:
        executedAndNotSkipped(':compileMyLibJarMyLibJava')
        skipped(':compileMainJarMainJava')
    }

}
