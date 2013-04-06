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

package net.myrrix.common.math;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.QRDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularMatrixException;
import org.apache.commons.math3.util.FastMath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.ClassUtils;

/**
 * An implementation based on {@link QRDecomposition} from Commons Math.
 * 
 * @author Sean Owen
 */
public final class CommonsMathLinearSystemSolver implements LinearSystemSolver {
  
  private static final Logger log = LoggerFactory.getLogger(CommonsMathLinearSystemSolver.class);

  private static final Field RDIAG_FIELD = ClassUtils.loadField(QRDecomposition.class, "rDiag");
  
  @Override
  public Solver getSolver(RealMatrix M) {
    if (M == null) {
      return null;
    }
    QRDecomposition decomposition = new QRDecomposition(M, SINGULARITY_THRESHOLD);
    DecompositionSolver solver = decomposition.getSolver();
    if (!solver.isNonSingular()) {
      log.warn("{} x {} matrix is near-singular (threshold {}); add more data or decrease the value of model.features",
                     M.getRowDimension(), M.getColumnDimension(), SINGULARITY_THRESHOLD);
      double[] rDiag;
      try {
        rDiag = (double[]) RDIAG_FIELD.get(decomposition);
      } catch (IllegalAccessException iae) {
        log.warn("Can't read QR decomposition fields to suggest dimensionality");
        throw new IllegalStateException(iae);
      }
      log.info("QR decomposition diagonal: {}", Arrays.toString(rDiag));
      for (int i = 0; i < rDiag.length; i++) {
        if (FastMath.abs(rDiag[i]) <= SINGULARITY_THRESHOLD) {
          log.warn("Suggested value of -Dmodel.features is less than {}", i);
          break;
        }
      }
      throw new SingularMatrixException();
    }
    return new CommonsMathSolver(solver);
  }  

  @Override
  public boolean isNonSingular(RealMatrix M) {
    QRDecomposition decomposition = new QRDecomposition(M, SINGULARITY_THRESHOLD);
    DecompositionSolver solver = decomposition.getSolver();
    return solver.isNonSingular();
  }  

}
