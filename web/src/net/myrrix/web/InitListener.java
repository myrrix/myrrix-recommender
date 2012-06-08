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

package net.myrrix.web;

import java.io.Closeable;
import java.io.File;
import java.util.List;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import com.google.common.io.Closeables;
import com.google.common.io.Files;
import org.apache.mahout.common.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.ClassUtils;
import net.myrrix.common.IOUtils;
import net.myrrix.common.MyrrixRecommender;
import net.myrrix.common.PartitionsUtils;
import net.myrrix.online.RescorerProvider;
import net.myrrix.online.ServerRecommender;
import net.myrrix.web.servlets.AbstractMyrrixServlet;

/**
 * <p>This servlet lifecycle listener makes sure that the shared {@link MyrrixRecommender} instance
 * is initialized at startup, along with related objects, and shut down when the container is destroyed.</p>
 *
 * @author Sean Owen
 */
public final class InitListener implements ServletContextListener {

  private static final Logger log = LoggerFactory.getLogger(InitListener.class);

  private static final String KEY_PREFIX = InitListener.class.getName();
  public static final String LOCAL_INPUT_DIR_KEY = KEY_PREFIX + ".LOCAL_INPUT_DIR";
  public static final String INSTANCE_ID_KEY = KEY_PREFIX + ".INSTANCE_ID";
  public static final String RESCORER_PROVIDER_CLASS_KEY = KEY_PREFIX + ".RESCORER_PROVIDER_CLASS";
  public static final String ALL_PARTITIONS_SPEC_KEY = KEY_PREFIX + ".ALL_PARTITIONS_SPEC";
  public static final String PARTITION_KEY = KEY_PREFIX + ".PARTITION";

  private File tempDirToDelete;

  @Override
  public void contextInitialized(ServletContextEvent event) {
    log.info("Initializing Myrrix in servlet context...");
    ServletContext context = event.getServletContext();

    String localInputDirName = getAttributeOrParam(context, LOCAL_INPUT_DIR_KEY);
    File localInputDir;
    if (localInputDirName == null) {
      localInputDir = Files.createTempDir();
      localInputDir.deleteOnExit();
      tempDirToDelete = localInputDir;
    } else {
      localInputDir = new File(localInputDirName);
      if (!localInputDir.exists()) {
        boolean madeDirs = localInputDir.mkdirs();
        if (!madeDirs) {
          log.warn("Failed to create local input dir {}", localInputDir);
        }
      }
      tempDirToDelete = null;
    }
    context.setAttribute(AbstractMyrrixServlet.LOCAL_INPUT_DIR_KEY, localInputDir.getAbsolutePath());

    String recommenderProviderClassName = getAttributeOrParam(context, RESCORER_PROVIDER_CLASS_KEY);
    if (recommenderProviderClassName != null) {
      log.info("Using RescorerProvider {}", recommenderProviderClassName);
      RescorerProvider rescorerProvider =
          ClassUtils.loadInstanceOf(recommenderProviderClassName, RescorerProvider.class);
      context.setAttribute(AbstractMyrrixServlet.RESCORER_PROVIDER_KEY, rescorerProvider);
    }

    int numPartitions = 0;
    String allPartitionsSpecString = getAttributeOrParam(context, ALL_PARTITIONS_SPEC_KEY);
    if (allPartitionsSpecString != null) {
      List<List<Pair<String,Integer>>> allPartitions =
          PartitionsUtils.parseAllPartitions(allPartitionsSpecString);
      log.info("Parsed partitions {}", allPartitions);
      numPartitions = allPartitions.size();
      log.info("{} total partitions", numPartitions);
      context.setAttribute(AbstractMyrrixServlet.ALL_PARTITIONS_KEY, allPartitions);
    }

    int partition = 0;
    String partitionString = getAttributeOrParam(context, PARTITION_KEY);
    if (partitionString != null) {
      partition = Integer.parseInt(partitionString);
      log.info("Running as partition {}", partition);
      context.setAttribute(AbstractMyrrixServlet.PARTITION_KEY, partition);
    }

    long instanceID = Long.parseLong(getAttributeOrParam(context, INSTANCE_ID_KEY));

    MyrrixRecommender recommender = new ServerRecommender(instanceID, localInputDir, partition, numPartitions);
    context.setAttribute(AbstractMyrrixServlet.RECOMMENDER_KEY, recommender);

    log.info("Myrrix is initialized");
  }

  private static String getAttributeOrParam(ServletContext context, String key) {
    Object valueObject = context.getAttribute(key);
    String valueString = valueObject == null ? null : valueObject.toString();
    if (valueString == null) {
      valueString = context.getInitParameter(key);
    }
    return valueString;
  }

  @Override
  public void contextDestroyed(ServletContextEvent event) {
    log.info("Uninitializing Myrrix in servlet context...");
    ServletContext context = event.getServletContext();
    Closeable recommender = (Closeable) context.getAttribute(AbstractMyrrixServlet.RECOMMENDER_KEY);
    if (recommender != null) {
      Closeables.closeQuietly(recommender);
    }
    IOUtils.deleteRecursively(tempDirToDelete);
    log.info("Myrrix is uninitialized");
  }

}
