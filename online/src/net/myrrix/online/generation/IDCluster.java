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

package net.myrrix.online.generation;

import com.google.common.base.Preconditions;

import net.myrrix.common.collection.FastIDSet;
import net.myrrix.common.math.SimpleVectorMath;

public final class IDCluster {

  private final FastIDSet members;
  private final float[] centroid;
  private final double centroidNorm;

  public IDCluster(FastIDSet members, float[] centroid) {
    Preconditions.checkNotNull(members);
    Preconditions.checkNotNull(centroid);
    this.members = members;
    this.centroid = centroid;
    this.centroidNorm = SimpleVectorMath.norm(centroid);
  }

  public FastIDSet getMembers() {
    return members;
  }

  public float[] getCentroid() {
    return centroid;
  }
  
  public double getCentroidNorm() {
    return centroidNorm;
  }

}
