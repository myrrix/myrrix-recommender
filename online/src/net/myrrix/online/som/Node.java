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

package net.myrrix.online.som;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;
import org.apache.mahout.common.Pair;

/**
 * Represents a node in the final self-organizing map, which has both a set of members and a "center"
 * representing the node's corresponding position in the input feature space. It also includes
 * a projection of the center into 3D space, which aids display of the node. For example a 2D
 * display of the node grid can color nodes by construing the 3D projection as red, green and blue
 * components.
 *
 * @author Sean Owen
 */
public final class Node {

  private List<Pair<Double,Long>> assignedIDs;
  private final float[] center;
  private final float[] projection3D;

  Node(float[] initialCenter) {
    center = initialCenter;
    projection3D = new float[3];
  }

  /**
   * @return {@link Pair}s of input points assigned to this node, given as point's ID and a score
   *  indicating how close it is to the center (here, cosine similarity)
   */
  public List<Pair<Double,Long>> getAssignedIDs() {
    return assignedIDs == null ? Collections.<Pair<Double,Long>>emptyList() : assignedIDs;
  }

  void addAssignedID(Pair<Double,Long> assignedID) {
    if (assignedIDs == null) {
      assignedIDs = Lists.newArrayListWithCapacity(1);
    }
    assignedIDs.add(assignedID);
  }

  void clearAssignedIDs() {
    assignedIDs = null;
  }

  /**
   * @return point in input space that this node corresponds to
   */
  public float[] getCenter() {
    return center;
  }

  /**
   * @return random projection of {@link #getCenter()} into the unit 3D square (every element in [0,1]).
   */
  public float[] getProjection3D() {
    return projection3D;
  }

}
