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

package net.myrrix.online.factorizer;

import org.apache.commons.math3.linear.RealMatrix;
import org.junit.Test;

import net.myrrix.common.MyrrixTest;
import net.myrrix.common.collection.FastByIDFloatMap;
import net.myrrix.common.collection.FastByIDMap;

public final class MatrixUtilsTest extends MyrrixTest {

  @Test
  public void testAddTo() {
    FastByIDMap<FastByIDFloatMap> byRow = new FastByIDMap<FastByIDFloatMap>();
    FastByIDMap<FastByIDFloatMap> byCol = new FastByIDMap<FastByIDFloatMap>();
    assertNull(byRow.get(0L));
    assertNull(byRow.get(1L));
    assertNull(byRow.get(4L));
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
  public void testPseudoInverse() {
    FastByIDMap<float[]> M = new FastByIDMap<float[]>();
    M.put(0L, new float[] {1.0f, 4.0f, 0.0f});
    M.put(2L, new float[] {-1.0f, -2.0f, 3.0f});
    M.put(3L, new float[] {0.0f, 0.0f, 7.0f});
    FastByIDMap<float[]> Minv = MatrixUtils.getPseudoInverse(M);
    assertArrayEquals(new float[] {     -1.0f,        0.5f,       0.0f}, Minv.get(0L));
    assertArrayEquals(new float[] {     -2.0f,        0.5f,       0.0f}, Minv.get(2L));
    assertArrayEquals(new float[] {0.8571429f, -0.2142857f, 0.1428571f}, Minv.get(3L));
  }

  @Test
  public void testTransposeTimesSelf() {
    FastByIDMap<float[]> M = new FastByIDMap<float[]>();
    M.put(1L, new float[] {4.0f, -1.0f, -5.0f});
    M.put(2L, new float[] {2.0f, 0.0f, 3.0f});
    RealMatrix MTM = MatrixUtils.transposeTimesSelf(M);
    assertArrayEquals(MTM.getRow(0), new double[] { 20.0, -4.0, -14.0});
    assertArrayEquals(MTM.getRow(1), new double[] { -4.0,  1.0,   5.0});
    assertArrayEquals(MTM.getRow(2), new double[] {-14.0,  5.0,  34.0});
  }

}
