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

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
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
public final class CommonsMathMatrixInverter implements MatrixInverter {
  
  private static final Logger log = LoggerFactory.getLogger(CommonsMathMatrixInverter.class);

  private static final Field RDIAG_FIELD = ClassUtils.loadField(QRDecomposition.class, "rDiag");
  
  @Override
  public RealMatrix invert(RealMatrix M) {
    QRDecomposition decomposition = new QRDecomposition(M, SINGULARITY_THRESHOLD);
    DecompositionSolver solver = decomposition.getSolver();
    RealMatrix inverse;
    try {
      inverse = solver.getInverse();
    } catch (SingularMatrixException sme) {
      log.warn("{} x {} matrix is near-singular (threshold {}); add more data or decrease the value of model.features",
               M.getRowDimension(), M.getColumnDimension(), SINGULARITY_THRESHOLD);
      double[] rDiag;
      try {
        rDiag = (double[]) RDIAG_FIELD.get(decomposition);
      } catch (IllegalAccessException ignored) {
        log.warn("Can't read QR decomposition fields to suggest dimensionality");
        throw sme;
      }
      log.info("QR decomposition diagonal: {}", Arrays.toString(rDiag));
      for (int i = 0; i < rDiag.length; i++) {
        if (FastMath.abs(rDiag[i]) <= SINGULARITY_THRESHOLD) {
          log.warn("Suggested value of -Dmodel.features is less than {}", i);
          break;
        }
      }
      throw sme;
    }
    return new Array2DRowRealMatrix(inverse.getData());
  }

  @Override
  public boolean isInvertible(RealMatrix M) {
    QRDecomposition decomposition = new QRDecomposition(M, SINGULARITY_THRESHOLD);
    DecompositionSolver solver = decomposition.getSolver();
    try {
      solver.getInverse();
      return true;
    } catch (SingularMatrixException ignored) {
      return false;
    }
  }  

}
