/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.binarycompatibility

import org.junit.Test


class AnonymousClassesFilteringTest : AbstractBinaryCompatibilityTest() {

    @Test
    fun `anonymous classes are excluded (java)`() {

        checkBinaryCompatibleJava(
            v1 = """
                public class Source {
                    public void some() {}
                }
            """,
            v2 = """
                public class Source {
                    public void some() {
                        Runnable anon = new Runnable() {
                            @Override
                            public void run() {}
                        };
                        anon.run();
                    }
                }
            """
        ) {
            assertEmptyReport()
        }
    }

    @Test
    fun `anonymous classes are excluded (kotlin)`() {

        checkBinaryCompatibleKotlin(
            v1 = """
                fun some() {}
            """,
            v2 = """
                fun some() {
                    val anon = object : Runnable {
                        override fun run() {}
                    }
                    anon.run()
                }
            """
        ) {
            assertEmptyReport()
        }
    }
}
