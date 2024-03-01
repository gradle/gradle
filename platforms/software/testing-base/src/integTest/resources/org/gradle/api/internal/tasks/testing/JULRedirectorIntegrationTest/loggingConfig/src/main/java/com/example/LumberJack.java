/*
 * Copyright 2016 the original author or authors.
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

package com.example;

import java.util.logging.Logger;

public class LumberJack {
    private static final Logger ROOT = Logger.getLogger("");
    private static final Logger OAK = Logger.getLogger("oak");
    private static final Logger ELM = Logger.getLogger("elm");
    private static final Logger ELMER = Logger.getLogger("elm.er");

    public void sing() {
        ROOT.fine("Oh, I'm a lumberjack, and I'm okay.");
        OAK.fine("I sleep all night and I work all day.");
        ELM.fine("He's a lumberjack, and He's okay.");
        ELMER.fine("He sleeps all night and he works all day.");
    }
}
