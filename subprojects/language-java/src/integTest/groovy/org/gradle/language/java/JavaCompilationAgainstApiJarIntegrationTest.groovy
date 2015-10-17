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

class JavaCompilationAgainstApiJarIntegrationTest extends AbstractIntegrationSpec {
    void applyJavaPlugin(File buildFile) {
        buildFile << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}
'''
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
        sleep(1000)
        file('src/myLib/java/com/acme/Person.java').write '''package com.acme;

public class Person {
    public String name;
}
'''
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
        sleep(1000)
        file('src/myLib/java/internal/PersonInternal.java').write '''package internal;
import com.acme.Person;

public class PersonInternal extends Person {
    private String name;
}
'''
        then:
        succeeds ':mainJar'

        and:
        executedAndNotSkipped(':compileMyLibJarMyLibJava')
        skipped(':compileMainJarMainJava')
    }

}
