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

package org.gradle.internal;

import javax.annotation.Nullable;

public final class Pair<L, R> {

    @Nullable
    public final L left;

    @Nullable
    public final R right;

    private Pair(@Nullable L left, @Nullable R right) {
        this.left = left;
        this.right = right;
    }

    @Nullable
    public L getLeft() {
        return left;
    }

    @Nullable
    public R getRight() {
        return right;
    }

    @Nullable
    public L left() {
        return left;
    }

    @Nullable
    public R right() {
        return right;
    }

    public static <L, R> Pair<L, R> of(@Nullable L left, @Nullable R right) {
        return new Pair<L, R>(left, right);
    }

    public <T> Pair<T, Pair<L, R>> pushLeft(T t) {
        return of(t, this);
    }

    public <T> Pair<Pair<L, R>, T> pushRight(T t) {
        return of(this, t);
    }

    public <T> Pair<Pair<T, L>, R> nestLeft(T t) {
        return of(of(t, left), right);
    }

    public <T> Pair<L, Pair<T, R>> nestRight(T t) {
        return of(left, of(t, right));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Pair<?, ?> pair = (Pair<?, ?>) o;

        return !(left != null ? !left.equals(pair.left) : pair.left != null) && !(right != null ? !right.equals(pair.right) : pair.right != null);
    }

    @Override
    public int hashCode() {
        int result = left != null ? left.hashCode() : 0;
        result = 31 * result + (right != null ? right.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Pair[" + left + "," + right + ']';
    }

}
