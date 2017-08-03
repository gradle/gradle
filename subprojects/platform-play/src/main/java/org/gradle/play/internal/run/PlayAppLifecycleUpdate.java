/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.internal.run;

import java.io.Serializable;
import java.net.InetSocketAddress;

abstract class PlayAppLifecycleUpdate implements Serializable {
    static PlayAppLifecycleUpdate stopped() {
        return new PlayAppStop();
    }

    static PlayAppLifecycleUpdate running(InetSocketAddress address) {
        return new PlayAppStart(address);
    }

    static PlayAppLifecycleUpdate failed(Exception exception) {
        return new PlayAppStart(exception);
    }

    static PlayAppLifecycleUpdate reloadRequested() {
        return new PlayAppReload(true);
    }

    static PlayAppLifecycleUpdate reloadCompleted() {
        return new PlayAppReload(false);
    }
}
