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

package net.myrrix.client;

import org.apache.mahout.cf.taste.model.IDMigrator;
import org.junit.Test;

import net.myrrix.client.translating.OneWayMigrator;
import net.myrrix.common.MyrrixTest;

public final class OneWayMigratorTest extends MyrrixTest {

  @Test
  public void testForward() throws Exception {
    IDMigrator migrator = new OneWayMigrator();
    assertEquals(4060265690780417169L, migrator.toLongID("foobar"));
    assertEquals(-3162216497309240828L, migrator.toLongID(""));
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testBackward() throws Exception {
    IDMigrator migrator = new OneWayMigrator();
    migrator.toStringID(0L);
  }

}
