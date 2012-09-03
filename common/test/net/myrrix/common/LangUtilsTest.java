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

package net.myrrix.common;

import org.junit.Test;

public final class LangUtilsTest extends MyrrixTest {

  @Test(expected = IllegalArgumentException.class)
  public void testDoubleNaN() {
    LangUtils.parseDouble("NaN");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testFloatNaN() {
    LangUtils.parseFloat("NaN");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testDoubleInf() {
    LangUtils.parseDouble("Infinity");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testFloatInf() {
    LangUtils.parseFloat("Infinity");
  }

  @Test
  public void testDouble() {
    assertEquals(3.1, LangUtils.parseDouble("3.1"));
  }

  @Test
  public void testFloat() {
    assertEquals(3.1f, LangUtils.parseFloat("3.1"));
  }

  @Test
  public void testMod() {
    assertEquals(0, LangUtils.mod(0, 1));
    assertEquals(0, LangUtils.mod(1, 1));
    assertEquals(0, LangUtils.mod(2, 1));

    assertEquals(1, LangUtils.mod(-1, 2));
    assertEquals(0, LangUtils.mod(0, 2));
    assertEquals(1, LangUtils.mod(1, 2));

    assertEquals(3, LangUtils.mod(7, 4));
    assertEquals(3, LangUtils.mod(-12, 5));

    assertEquals(0, LangUtils.mod(Long.MIN_VALUE, 64));
    assertEquals(0, LangUtils.mod(0, 64));
    assertEquals(63, LangUtils.mod(Long.MAX_VALUE, 64));
  }

}
