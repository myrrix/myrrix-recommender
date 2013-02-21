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

package net.myrrix.online.candidate;

import com.google.common.base.Preconditions;

import net.myrrix.common.ClassUtils;
import net.myrrix.common.collection.FastByIDMap;

/**
 * <p>This class helps choose which {@link CandidateFilter} to apply to the recommendation process.
 * If the "model.candidateFilter.customClass" system property is set, then this class will be loaded and used.
 * See notes in {@link CandidateFilter} about how the class must be implemented.</p>
 * 
 * <p>Otherwise, if "model.lsh.sampleRatio" is set to a value less than 1, then {@link LocationSensitiveHash} 
 * will be used. It is a somewhat special case, a built-in type of filter.</p>
 * 
 * <p>Otherwise an implementation that does no filtering will be returned.</p>
 * 
 * @author Sean Owen
 */
public final class CandidateFilterFactory {

  private CandidateFilterFactory() {
  }

  /**
   * @return an implementation of {@link CandidateFilter} chosen per above. It will be non-null.
   */
  public static CandidateFilter buildCandidateFilter(FastByIDMap<float[]> Y) {
    Preconditions.checkNotNull(Y);
    if (!Y.isEmpty()) {
      String candidateFilterCustomClassString = System.getProperty("model.candidateFilter.customClass");
      if (candidateFilterCustomClassString != null) {
        return ClassUtils.loadInstanceOf(candidateFilterCustomClassString,
                                         CandidateFilter.class,
                                         new Class<?>[]{FastByIDMap.class},
                                         new Object[]{Y});
      }
      // LSH is a bit of a special case, handled here
      if (LocationSensitiveHash.LSH_SAMPLE_RATIO < 1.0) {
        return new LocationSensitiveHash(Y);
      }
    }
    return new IdentityCandidateFilter(Y);    
  }
  
}
