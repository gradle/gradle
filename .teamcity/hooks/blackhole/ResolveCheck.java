/*
 * Copyright 2026 the original author or authors.
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

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Checks that the JVM cannot resolve the blocked host but can resolve the allowed one.
 *
 * Usage: java ResolveCheck.java BLOCKED_HOST ALLOWED_HOST
 */
public class ResolveCheck {
    public static void main(String[] args) throws Exception {
        try {
            InetAddress address = InetAddress.getByName(args[0]);
            System.out.println("JVM still resolves " + args[0] + " to " + address.getHostAddress());
            System.exit(1);
        } catch (UnknownHostException expected) {
            System.out.println("JVM cannot resolve " + args[0]);
        }
        System.out.println("JVM resolves " + args[1] + " to " + InetAddress.getByName(args[1]).getHostAddress());
    }
}
