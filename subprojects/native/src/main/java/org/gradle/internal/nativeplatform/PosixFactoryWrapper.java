/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.nativeplatform;

import org.jruby.ext.posix.JavaPOSIX;
import org.jruby.ext.posix.POSIX;

/**
 * Created by IntelliJ IDEA. User: Rene Date: 05.03.12 Time: 22:58 To change this template use File | Settings | File Templates.
 */
public class PosixFactoryWrapper {
    public static POSIX wrap(POSIX posix) {

        if(posix instanceof JavaPOSIX){
            return new WrappedJavaPOSIX(posix);
        }else{
            return posix;
        }
    }
}
