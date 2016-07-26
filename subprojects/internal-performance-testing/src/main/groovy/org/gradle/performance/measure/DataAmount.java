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
package org.gradle.performance.measure;

import java.math.BigDecimal;

public class DataAmount {
    public static final Units<DataAmount> BYTES = Units.base(DataAmount.class, "B");
    public static final Units<DataAmount> KILO_BYTES = BYTES.times(1024, "kB");
    public static final Units<DataAmount> MEGA_BYTES = KILO_BYTES.times(1024, "MB");
    public static final Units<DataAmount> GIGA_BYTES = MEGA_BYTES.times(1024, "GB");

    public static Amount<DataAmount> bytes(long value) {
        return Amount.valueOf(value, BYTES);
    }

    public static Amount<DataAmount> bytes(BigDecimal value) {
        return Amount.valueOf(value, BYTES);
    }

    public static Amount<DataAmount> kbytes(BigDecimal value) {
        return Amount.valueOf(value, KILO_BYTES);
    }

    public static Amount<DataAmount> mbytes(BigDecimal value) {
        return Amount.valueOf(value, MEGA_BYTES);
    }
}
