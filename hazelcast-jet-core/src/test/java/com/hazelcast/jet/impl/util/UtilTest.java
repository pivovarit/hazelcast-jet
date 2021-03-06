/*
 * Copyright (c) 2008-2019, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.jet.impl.util;

import com.hazelcast.test.HazelcastParallelClassRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.hazelcast.jet.config.ProcessingGuarantee.AT_LEAST_ONCE;
import static com.hazelcast.jet.config.ProcessingGuarantee.EXACTLY_ONCE;
import static com.hazelcast.jet.config.ProcessingGuarantee.NONE;
import static com.hazelcast.jet.impl.util.Util.addClamped;
import static com.hazelcast.jet.impl.util.Util.addOrIncrementIndexInName;
import static com.hazelcast.jet.impl.util.Util.gcd;
import static com.hazelcast.jet.impl.util.Util.memoizeConcurrent;
import static com.hazelcast.jet.impl.util.Util.roundRobinPart;
import static com.hazelcast.jet.impl.util.Util.subtractClamped;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(HazelcastParallelClassRunner.class)
public class UtilTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void when_addClamped_then_doesNotOverflow() {
        // no overflow
        assertEquals(0, addClamped(0, 0));
        assertEquals(1, addClamped(1, 0));
        assertEquals(-1, addClamped(-1, 0));
        assertEquals(-1, addClamped(Long.MAX_VALUE, Long.MIN_VALUE));
        assertEquals(-1, addClamped(Long.MIN_VALUE, Long.MAX_VALUE));

        // overflow over MAX_VALUE
        assertEquals(Long.MAX_VALUE, addClamped(Long.MAX_VALUE, 1));
        assertEquals(Long.MAX_VALUE, addClamped(Long.MAX_VALUE, Long.MAX_VALUE));

        // overflow over MIN_VALUE
        assertEquals(Long.MIN_VALUE, addClamped(Long.MIN_VALUE, -1));
        assertEquals(Long.MIN_VALUE, addClamped(Long.MIN_VALUE, Long.MIN_VALUE));
    }

    @Test
    public void when_subtractClamped_then_doesNotOverflow() {
        // no overflow
        assertEquals(0, subtractClamped(0, 0));
        assertEquals(1, subtractClamped(1, 0));
        assertEquals(-1, subtractClamped(-1, 0));
        assertEquals(0, subtractClamped(Long.MAX_VALUE, Long.MAX_VALUE));
        assertEquals(0, subtractClamped(Long.MIN_VALUE, Long.MIN_VALUE));

        // overflow over MAX_VALUE
        assertEquals(Long.MAX_VALUE, subtractClamped(Long.MAX_VALUE, -1));
        assertEquals(Long.MAX_VALUE, subtractClamped(Long.MAX_VALUE, Long.MIN_VALUE));

        // overflow over MIN_VALUE
        assertEquals(Long.MIN_VALUE, subtractClamped(Long.MIN_VALUE, 1));
        assertEquals(Long.MIN_VALUE, subtractClamped(Long.MIN_VALUE, Long.MAX_VALUE));
    }

    @Test
    public void when_memoizeConcurrent_then_threadSafe() {
        final Object obj = new Object();
        Supplier<Object> supplier = new Supplier<Object>() {
            boolean supplied;

            @Override
            public Object get() {
                if (supplied) {
                    throw new IllegalStateException("Supplier was already called once.");
                }
                supplied = true;
                return obj;
            }
        };

        // does not fail 100% with non-concurrent memoize, but about 50% of the time.
        List<Object> list = Stream.generate(memoizeConcurrent(supplier)).limit(4).parallel().collect(Collectors.toList());
        assertTrue("Not all objects matched expected", list.stream().allMatch(o -> o.equals(obj)));
    }

    @Test(expected = NullPointerException.class)
    public void when_memoizeConcurrentWithNullSupplier_then_exception() {
        Supplier<Object> supplier = () -> null;
        memoizeConcurrent(supplier).get();
    }

    @Test
    public void test_calculateGcd2() {
        assertEquals(2, gcd(0L, 2L));
        assertEquals(1, gcd(1L, 2L));
        assertEquals(2, gcd(2L, 4L));
        assertEquals(2, gcd(-2L, 4L));
    }

    @Test
    public void test_calculateGcdN() {
        assertEquals(0, gcd());
        assertEquals(4, gcd(4, 4, 4));
        assertEquals(4, gcd(4, 8, 12));
        assertEquals(1, gcd(4, 8, 13));
    }

    @Test
    public void test_addIndexToName() {
        assertEquals("a-2", addOrIncrementIndexInName("a"));
        assertEquals("a-3", addOrIncrementIndexInName("a-2"));
        assertEquals("a-26", addOrIncrementIndexInName("a-25"));
        assertEquals("a-25x-2", addOrIncrementIndexInName("a-25x"));
        assertEquals("a-1351318168168168168168-2", addOrIncrementIndexInName("a-1351318168168168168168"));
        assertEquals("a-" + Integer.MAX_VALUE + "-2", addOrIncrementIndexInName("a-" + Integer.MAX_VALUE));
        assertEquals("a-0-2", addOrIncrementIndexInName("a-0"));
        assertEquals("a-1-2", addOrIncrementIndexInName("a-1"));
        assertEquals("a-1-3", addOrIncrementIndexInName("a-1-2"));
        assertEquals("a--1-2", addOrIncrementIndexInName("a--1"));
    }

    @Test
    public void test_roundRobinPart() {
        assertArrayEquals(new int[] {},
                roundRobinPart(0, 2, 0));
        assertArrayEquals(new int[] {0},
                roundRobinPart(1, 1, 0));
        assertArrayEquals(new int[] {0},
                roundRobinPart(1, 2, 0));
        assertArrayEquals(new int[] {},
                roundRobinPart(1, 2, 1));
        assertArrayEquals(new int[] {0, 1},
                roundRobinPart(2, 1, 0));
        assertArrayEquals(new int[] {0},
                roundRobinPart(2, 2, 0));
        assertArrayEquals(new int[] {1},
                roundRobinPart(2, 2, 1));
        assertArrayEquals(new int[] {0, 2},
                roundRobinPart(3, 2, 0));
        assertArrayEquals(new int[] {1},
                roundRobinPart(3, 2, 1));
    }

    @Test
    public void test_minGuarantee() {
        assertEquals(NONE, Util.min(NONE, AT_LEAST_ONCE));
        assertEquals(AT_LEAST_ONCE, Util.min(AT_LEAST_ONCE, EXACTLY_ONCE));
        assertEquals(NONE, Util.min(NONE, EXACTLY_ONCE));
        assertEquals(NONE, Util.min(NONE, NONE));
    }
}
