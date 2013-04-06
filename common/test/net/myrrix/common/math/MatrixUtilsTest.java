/*
 * Copyright Myrrix Ltd
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

package net.myrrix.common.math;

import org.apache.commons.math3.linear.RealMatrix;
import org.junit.Test;

import net.myrrix.common.MyrrixTest;
import net.myrrix.common.collection.FastByIDFloatMap;
import net.myrrix.common.collection.FastByIDMap;

/**
 * Tests {@link MatrixUtils}.
 * 
 * @author Sean Owen
 */
public final class MatrixUtilsTest extends MyrrixTest {

  @Test
  public void testAddTo() {
    FastByIDMap<FastByIDFloatMap> byRow = new FastByIDMap<FastByIDFloatMap>();
    assertNull(byRow.get(0L));
    assertNull(byRow.get(1L));
    assertNull(byRow.get(4L));
    FastByIDMap<FastByIDFloatMap> byCol = new FastByIDMap<FastByIDFloatMap>();
    MatrixUtils.addTo(0L, 0L, -1.0f, byRow, byCol);
    MatrixUtils.addTo(4L, 1L, 2.0f, byRow, byCol);
    assertEquals(-1.0f, byRow.get(0L).get(0L));
    assertEquals(-1.0f, byCol.get(0L).get(0L));
    assertNull(byRow.get(1L));
    assertEquals(2.0f, byRow.get(4L).get(1L));
    assertEquals(2.0f, byCol.get(1L).get(4L));
    assertNaN(byRow.get(4L).get(0L));
  }

  @Test
  public void testRemove() {
    FastByIDMap<FastByIDFloatMap> byRow = new FastByIDMap<FastByIDFloatMap>();
    FastByIDMap<FastByIDFloatMap> byCol = new FastByIDMap<FastByIDFloatMap>();
    MatrixUtils.addTo(0L, 0L, -1.0f, byRow, byCol);
    MatrixUtils.addTo(4L, 1L, 2.0f, byRow, byCol);
    MatrixUtils.remove(0L, 0L, byRow, byCol);
    assertNull(byRow.get(0L));
    assertEquals(2.0f, byRow.get(4L).get(1L));
    assertEquals(2.0f, byCol.get(1L).get(4L));
  }

  @Test
  public void testTransposeTimesSelf() {
    FastByIDMap<float[]> M = new FastByIDMap<float[]>();
    M.put(1L, new float[] {4.0f, -1.0f, -5.0f});
    M.put(2L, new float[] {2.0f, 0.0f, 3.0f});
    RealMatrix MTM = MatrixUtils.transposeTimesSelf(M);
    assertArrayEquals(new double[]{20.0, -4.0, -14.0}, MTM.getRow(0));
    assertArrayEquals(new double[]{-4.0, 1.0, 5.0}, MTM.getRow(1));
    assertArrayEquals(new double[]{-14.0, 5.0, 34.0}, MTM.getRow(2));
  }

}
