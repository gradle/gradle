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

import com.google.common.base.Function;

public final class Tuple<L, R> {

    public final L left;
    public final R right;

    private Tuple(L left, R right) {
        this.left = left;
        this.right = right;
    }

    public L getLeft() {
        return left;
    }

    public R getRight() {
        return right;
    }

    public L left() {
        return left;
    }

    public R right() {
        return right;
    }

    public static <L, R> Tuple<L, R> of(L left, R right) {
        return new Tuple<L, R>(left, right);
    }

    public <T> Tuple<T, Tuple<L, R>> pushLeft(T t) {
        return of(t, this);
    }

    public <T> Tuple<Tuple<L, R>, T> pushRight(T t) {
        return of(this, t);
    }

    public <T> Tuple<Tuple<T, L>, R> nestLeft(T t) {
        return of(of(t, left), right);
    }

    public <T> Tuple<L, Tuple<T, R>> nestRight(T t) {
        return of(left, of(t, right));
    }

    public <T> Tuple<T, R> mapLeft(Function<? super L, ? extends T> function) throws Exception {
        return of(function.apply(left), right);
    }

    public <T> Tuple<L, T> mapRight(Function<? super R, ? extends T> function) throws Exception {
        return of(left, function.apply(right));
    }

    public <T> T map(Function<? super Tuple<L, R>, ? extends T> function) throws Exception {
        return function.apply(this);
    }

    public static <L, T extends Tuple<L, ?>> Function<T, L> unpackLeft() {
        return new Function<T, L>() {
            @Override
            public L apply(T tuple) {
                return tuple.left;
            }
        };
    }

    public static <R, T extends Tuple<?, R>> Function<T, R> unpackRight() {
        return new Function<T, R>() {
            @Override
            public R apply(T tuple) {
                return tuple.right;
            }
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Tuple<?, ?> tuple = (Tuple<?, ?>) o;

        return !(left != null ? !left.equals(tuple.left) : tuple.left != null) && !(right != null ? !right.equals(tuple.right) : tuple.right != null);
    }

    @Override
    public int hashCode() {
        int result = left != null ? left.hashCode() : 0;
        result = 31 * result + (right != null ? right.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Tuple[" + left + "," + right + ']';
    }

}