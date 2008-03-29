/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.wrapper

import groovy.mock.interceptor.MockFor

/**
 * @author Hans Dockter
 */
class InstallMainTest extends GroovyTestCase {

    // todo: Mockers do not work for Java. We have to find something better.

    void testMain() {
//        MockFor wrapperMocker = new MockFor(Install)
//        wrapperMocker.demand.createDist() {String urlRoot, String distName, File rootDir ->
//            assertEquals('file://path', urlRoot)
//            assertEquals('gradle-1.0', distName)
//            assertEquals(new File('gradle'), rootDir)
//        }
//        wrapperMocker.use() {
//            InstallMain.main([] as String[])
//        }
    }
}
