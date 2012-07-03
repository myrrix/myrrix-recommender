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

import java.util.List;

import org.apache.mahout.common.Pair;
import org.junit.Test;

public final class PartitionsUtilsTest extends MyrrixTest {

  @Test
  public void testParse() {

    List<List<Pair<String,Integer>>> partitions =
        PartitionsUtils.parseAllPartitions("foo:80,foo2:8080;bar:8080;baz2:80,baz3:80");

    assertEquals(3, partitions.size());

    List<Pair<String,Integer>> partition0 = partitions.get(0);
    List<Pair<String,Integer>> partition1 = partitions.get(1);
    List<Pair<String,Integer>> partition2 = partitions.get(2);

    assertEquals(2, partition0.size());
    assertEquals(1, partition1.size());
    assertEquals(2, partition2.size());

    assertEquals("foo", partition0.get(0).getFirst());
    assertEquals(80, (int) partition0.get(0).getSecond());
    assertEquals("foo2", partition0.get(1).getFirst());
    assertEquals(8080, (int) partition0.get(1).getSecond());

    assertEquals("bar", partition1.get(0).getFirst());
    assertEquals(8080, (int) partition1.get(0).getSecond());

    assertEquals("baz2", partition2.get(0).getFirst());
    assertEquals(80, (int) partition2.get(0).getSecond());
    assertEquals("baz3", partition2.get(1).getFirst());
    assertEquals(80, (int) partition2.get(1).getSecond());
  }

}
