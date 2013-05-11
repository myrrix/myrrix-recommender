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

//import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
//import org.jblas.DoubleMatrix;
//import org.jblas.SimpleBlas;
//import org.jblas.exceptions.LapackException;

/**
 * <p>Implementation based on <a href="http://www.netlib.org/blas/">BLAS</a>, wrapped via 
 * <a href="http://en.wikipedia.org/wiki/Java_Native_Interface">JNI</a> and 
 * <a href="http://mikiobraun.github.io/jblas/">jblas</a>.</p>
 * 
 * <p>Not yet enabled.</p>
 * 
 * @author Sean Owen
 * @since 1.0
 */
public final class JBlasLinearSystemSolver implements LinearSystemSolver {

  @Override
  public Solver getSolver(RealMatrix M) {  
    /*
    DoubleMatrix dm = convertToJBlas(M);
    DoubleMatrix dmInv;
    try {
      dmInv = solveSymmetric(dm, DoubleMatrix.eye(dm.rows));        
    } catch (LapackException le) {
      throw new SingularMatrixSolverException(le);
    }
    return new InverseBasedSolver(new Array2DRowRealMatrix(dmInv.toArray2()));
     */
    throw new UnsupportedOperationException();
  }  
  
  /*
  private static DoubleMatrix solveSymmetric(DoubleMatrix A, DoubleMatrix B) {
    SimpleBlas.sysv('U', A, new int[B.rows], B);
    return B;
  }
  
  private static DoubleMatrix convertToJBlas(RealMatrix M) {
    double[][] Mdata = M.getData();
    double[] linearizedData = new double[Mdata.length * Mdata[0].length];
    int offset = 0;
    for (double[] MRow : Mdata) {
      System.arraycopy(MRow, 0, linearizedData, offset, MRow.length);
      offset += MRow.length;
    }
    return new DoubleMatrix(M.getRowDimension(), M.getColumnDimension(), linearizedData);
  }
   */

  @Override
  public boolean isNonSingular(RealMatrix M) {
    try {
      getSolver(M);
    } catch (SolverException ignored) {
      return false;
    }
    return true;
  }  

}
