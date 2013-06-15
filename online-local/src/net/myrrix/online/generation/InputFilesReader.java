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

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import com.google.common.base.Splitter;
import com.google.common.io.PatternFilenameFilter;
import org.apache.commons.math3.util.FastMath;
import org.apache.mahout.cf.taste.model.IDMigrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.LangUtils;
import net.myrrix.common.OneWayMigrator;
import net.myrrix.common.collection.FastByIDFloatMap;
import net.myrrix.common.collection.FastByIDMap;
import net.myrrix.common.collection.FastIDSet;
import net.myrrix.common.io.InvertedFilenameFilter;
import net.myrrix.common.iterator.FileLineIterable;
import net.myrrix.common.math.MatrixUtils;

/**
 * Reads input files into the "R" matrix representation.
 * 
 * @author Sean Owen
 */
final class InputFilesReader {
  
  private static final Logger log = LoggerFactory.getLogger(InputFilesReader.class);
  
  private static final Splitter COMMA = Splitter.on(',').trimResults();
  
  /**
   * Values with absolute value less than this in the input are considered 0.
   * Values are generally assumed to be > 1, actually,
   * and usually not negative, though they need not be.
   */
  private static final float ZERO_THRESHOLD =
      Float.parseFloat(System.getProperty("model.decay.zeroThreshold", "0.0001"));

  private InputFilesReader() {
  }

  static void readInputFiles(FastByIDMap<FastIDSet> knownItemIDs,
                             FastByIDMap<FastByIDFloatMap> rbyRow,
                             FastByIDMap<FastByIDFloatMap> rbyColumn,
                             FastIDSet itemTagIDs,
                             FastIDSet userTagIDs,
                             File inputDir) throws IOException {

    FilenameFilter csvFilter = new PatternFilenameFilter(".+\\.csv(\\.(zip|gz))?");

    File[] otherFiles = inputDir.listFiles(new InvertedFilenameFilter(csvFilter));
    if (otherFiles != null) {
      for (File otherFile : otherFiles) {
        log.info("Skipping file {}", otherFile.getName());
      }
    }

    File[] inputFiles = inputDir.listFiles(csvFilter);
    if (inputFiles == null) {
      log.info("No input files in {}", inputDir);
      return;
    }
    Arrays.sort(inputFiles, ByLastModifiedComparator.INSTANCE);

    IDMigrator hash = new OneWayMigrator();

    int lines = 0;
    int badLines = 0;
    for (File inputFile : inputFiles) {
      log.info("Reading {}", inputFile);
      for (String line : new FileLineIterable(inputFile)) {
        
        if (badLines > 100) { // Crude check
          throw new IOException("Too many bad lines; aborting");
        }
        
        lines++;
        
        if (line.isEmpty() || line.charAt(0) == '#') {
          continue;
        }
        
        Iterator<String> it = COMMA.split(line).iterator();

        long userID;
        boolean userIsTag;
        long itemID;
        boolean itemIsTag;
        float value;
        try {
          
          String userIDString = it.next();
          userIsTag = userIDString.startsWith("\"");
          if (userIsTag) {
            userID = hash.toLongID(userIDString.substring(1, userIDString.length() - 1));
          } else {
            userID = Long.parseLong(userIDString);
          }
          
          String itemIDString = it.next();
          itemIsTag = itemIDString.startsWith("\"");
          if (itemIsTag) {
            itemID = hash.toLongID(itemIDString.substring(1, itemIDString.length() - 1));
          } else {
            itemID = Long.parseLong(itemIDString);            
          }
          
          if (it.hasNext()) {
            String valueToken = it.next();
            value = valueToken.isEmpty() ? Float.NaN : LangUtils.parseFloat(valueToken);
          } else {
            value = 1.0f;
          }

        } catch (NoSuchElementException ignored) {
          log.warn("Ignoring line with too few columns: '{}'", line);
          badLines++;
          continue;
        } catch (IllegalArgumentException iae) { // includes NumberFormatException
          if (lines == 1) {
            log.info("Ignoring header line: '{}'", line);
          } else {
            log.warn("Ignoring unparseable line: '{}'", line);
            badLines++;
          }
          continue;
        }

        if (userIsTag && itemIsTag) {
          log.warn("Two tags not allowed: '{}'", line);
          badLines++;
          continue;
        }

        if (userIsTag) {
          itemTagIDs.add(userID);
        }
        
        if (itemIsTag) {
          userTagIDs.add(itemID);
        }
        
        if (Float.isNaN(value)) {
          // Remove, not set
          MatrixUtils.remove(userID, itemID, rbyRow, rbyColumn);
        } else {
          MatrixUtils.addTo(userID, itemID, value, rbyRow, rbyColumn);
        }

        if (knownItemIDs != null) {
          FastIDSet itemIDs = knownItemIDs.get(userID);
          if (Float.isNaN(value)) {
            // Remove, not set
            if (itemIDs != null) {
              itemIDs.remove(itemID);
              if (itemIDs.isEmpty()) {
                knownItemIDs.remove(userID);
              }
            }
          } else {
            if (itemIDs == null) {
              itemIDs = new FastIDSet();
              knownItemIDs.put(userID, itemIDs);
            }
            itemIDs.add(itemID);
          }
        }

        if (lines % 1000000 == 0) {
          log.info("Finished {} lines", lines);
        }
      }
    }
    
    log.info("Pruning near-zero entries");
    removeSmall(rbyRow);
    removeSmall(rbyColumn);    
  }
  
  private static void removeSmall(FastByIDMap<FastByIDFloatMap> matrix) {
    for (FastByIDMap.MapEntry<FastByIDFloatMap> entry : matrix.entrySet()) {
      for (Iterator<FastByIDFloatMap.MapEntry> it = entry.getValue().entrySet().iterator(); it.hasNext();) {
        FastByIDFloatMap.MapEntry entry2 = it.next();
        if (FastMath.abs(entry2.getValue()) < ZERO_THRESHOLD) {
          it.remove();
        }
      }
    }
  }

}
