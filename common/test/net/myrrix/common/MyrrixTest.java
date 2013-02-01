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

import java.io.File;

import com.google.common.io.Files;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;

import net.myrrix.common.io.IOUtils;
import net.myrrix.common.log.MemoryHandler;
import net.myrrix.common.random.RandomManager;

public abstract class MyrrixTest extends Assert {

  private static final float FLOAT_EPSILON = 1.0e-6f;
  private static final double DOUBLE_EPSILON = 1.0e-12;

  private File testTempDir;

  public static void assertEquals(float expected, float actual) {
    Assert.assertEquals(expected, actual, FLOAT_EPSILON);
  }

  public static void assertEquals(String message, float expected, float actual) {
    Assert.assertEquals(message, expected, actual, FLOAT_EPSILON);
  }

  public static void assertArrayEquals(float[] expecteds, float[] actuals) {
    Assert.assertArrayEquals(expecteds, actuals, FLOAT_EPSILON);
  }

  @SuppressWarnings("deprecation")
  public static void assertEquals(double expected, double actual) {
    Assert.assertEquals(expected, actual, DOUBLE_EPSILON);
  }

  @SuppressWarnings("deprecation")
  public static void assertEquals(String message, double expected, double actual) {
    Assert.assertEquals(message, expected, actual, DOUBLE_EPSILON);
  }

  public static void assertArrayEquals(double[] expecteds, double[] actuals) {
    Assert.assertArrayEquals(expecteds, actuals, DOUBLE_EPSILON);
  }

  public static void assertArrayEquals(float[] expecteds, double[] actuals) {
    assertArrayEquals(expecteds, doubleToFloatArray(actuals));
  }
  
  protected static float[] doubleToFloatArray(double[] d) {
    float[] f = new float[d.length];
    for (int i = 0; i < d.length; i++) {
      f[i] = (float) d[i];
    }
    return f;
  }

  @BeforeClass
  public static void setUpClass() {
    MemoryHandler.setSensibleLogFormat();    
  }
  
  @Before
  public void setUp() throws Exception {
    testTempDir = null;
    RandomManager.useTestSeed();
  }

  @After
  public void tearDown() throws Exception {
    IOUtils.deleteRecursively(testTempDir);
  }

  protected static void assertNaN(double d) {
    assertTrue("Expected NaN but got " + d, Double.isNaN(d));
  }

  protected static void assertNaN(float f) {
    assertTrue("Expected NaN but got " + f, Float.isNaN(f));
  }

  protected final synchronized File getTestTempDir() {
    if (testTempDir == null) {
      testTempDir = Files.createTempDir();
      testTempDir.deleteOnExit();
    }
    return testTempDir;
  }

}
