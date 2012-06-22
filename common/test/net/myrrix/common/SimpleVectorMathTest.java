/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.myrrix.common;

import org.junit.Test;

public final class SimpleVectorMathTest extends MyrrixTest {
  
  private static final float[] VEC1 = {-1.0f, 2.5f, 3.0f};
  private static final float[] VEC2 = {1.5f, -1.5f, 0.0f};

  @Test
  public void testDot() {
    assertEquals(-5.25, SimpleVectorMath.dot(VEC1, VEC2), EPSILON);
  }

  @Test
  public void testNorm() {
    assertEquals(4.03112887414928, SimpleVectorMath.norm(VEC1), EPSILON);
    assertEquals(2.12132034355964, SimpleVectorMath.norm(VEC2), EPSILON);
  }

  @Test
  public void testCosine() {
    assertEquals(-0.61394061351492, SimpleVectorMath.cosineMeasure(VEC1, VEC2), EPSILON);
  }

  @Test
  public void testCorrelation() {
    assertEquals(-0.802955068546966, SimpleVectorMath.correlation(VEC1, VEC2), EPSILON);
  }

}
