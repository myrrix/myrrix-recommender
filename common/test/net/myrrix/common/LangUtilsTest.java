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

}
