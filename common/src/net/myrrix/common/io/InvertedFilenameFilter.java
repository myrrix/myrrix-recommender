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

package net.myrrix.common.io;

import java.io.File;
import java.io.FilenameFilter;

/**
 * Accepts only the files that are not accepted by another given {@link FilenameFilter}.
 */
public final class InvertedFilenameFilter implements FilenameFilter {

  private final FilenameFilter delegate;

  public InvertedFilenameFilter(FilenameFilter delegate) {
    this.delegate = delegate;
  }

  @Override
  public boolean accept(File file, String s) {
    return !delegate.accept(file, s);
  }

}
