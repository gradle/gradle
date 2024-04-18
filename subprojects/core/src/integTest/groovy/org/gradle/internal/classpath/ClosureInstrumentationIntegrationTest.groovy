/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.classpath

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class ClosureInstrumentationIntegrationTest extends AbstractIntegrationSpec {
    @Issue("https://github.com/gradle/gradle/issues/28389")
    def "can instrument abstract closure classes"() {
        given:
        createDir("buildSrc") {
            dir("src/main/java") {
                file("MyBaseClosure.java") << """
                    import ${Closure.name};
                    public abstract class MyBaseClosure extends Closure<String> {
                        protected MyBaseClosure(Object owner) {
                            super(owner);
                        }

                        protected final void setDelegateBase(Object delegate) {
                            super.setDelegate(delegate);
                        }

                        @Override
                        public abstract void setDelegate(Object delegate);

                        public abstract String doCall(String argument);
                    }
                """

                file("ReverseClosure.java") << """
                    public class ReverseClosure extends MyBaseClosure {
                        protected ReverseClosure(Object owner) {
                            super(owner);
                        }

                        @Override
                        public void setDelegate(Object delegate) {
                            setDelegateBase(delegate);
                        }

                        @Override
                        public String doCall(String argument) {
                            return new StringBuilder(argument).reverse().toString();
                        }
                    }
                """
            }
            file("settings.gradle") << "\n"
        }

        buildFile("""
            task("hello") {
                doLast {
                    def cl = new ReverseClosure(this)
                    println("closure = " + cl("hello"))
                }
            }
        """)

        when:
        run("hello")

        then:
        outputContains("closure = olleh")
    }
}
