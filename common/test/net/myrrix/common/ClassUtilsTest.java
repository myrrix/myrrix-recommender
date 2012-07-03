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

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public final class ClassUtilsTest extends MyrrixTest {

  @Test
  public void testInstantiate() {
    Set<?> set = ClassUtils.loadInstanceOf(HashSet.class.getName(), Set.class);
    assertTrue(set instanceof HashSet);
  }

  @Test
  public void testInstantiateWithArgs() {
    Number n = ClassUtils.loadInstanceOf(Integer.class.getName(),
                                         Number.class,
                                         new Class<?>[] {int.class},
                                         new Object[] {3});
    assertEquals(3, n.intValue());
  }

}
