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
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import com.google.common.io.Closeables;
import com.google.common.io.Files;
import org.apache.mahout.common.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.ClassUtils;
import net.myrrix.common.ReloadingReference;
import net.myrrix.common.io.IOUtils;
import net.myrrix.common.MyrrixRecommender;
import net.myrrix.common.PartitionsUtils;
import net.myrrix.common.log.MemoryHandler;
import net.myrrix.online.RescorerProvider;
import net.myrrix.online.ServerRecommender;
import net.myrrix.online.io.ResourceRetriever;
import net.myrrix.online.partition.PartitionLoader;
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
  public static final String LOG_HANDLER = KEY_PREFIX + ".LOG_HANDLER";
  public static final String LOCAL_INPUT_DIR_KEY = KEY_PREFIX + ".LOCAL_INPUT_DIR";
  public static final String PORT_KEY = KEY_PREFIX + ".PORT";
  public static final String BUCKET_KEY = KEY_PREFIX + ".BUCKET";
  public static final String INSTANCE_ID_KEY = KEY_PREFIX + ".INSTANCE_ID";
  public static final String RESCORER_PROVIDER_CLASS_KEY = KEY_PREFIX + ".RESCORER_PROVIDER_CLASS";
  public static final String ALL_PARTITIONS_SPEC_KEY = KEY_PREFIX + ".ALL_PARTITIONS_SPEC";
  public static final String PARTITION_KEY = KEY_PREFIX + ".PARTITION";

  private File tempDirToDelete;

  @Override
  public void contextInitialized(ServletContextEvent event) {
    log.info("Initializing Myrrix in servlet context...");
    ServletContext context = event.getServletContext();

    MemoryHandler.setSensibleLogFormat();
    Handler logHandler = null;
    for (Handler handler : java.util.logging.Logger.getLogger("").getHandlers()) {
      if (handler instanceof MemoryHandler) {
        logHandler = handler;
        break;
      }
    }
    if (logHandler == null) {
      // Not previously configured by command line, make a new one
      logHandler = new MemoryHandler();
      java.util.logging.Logger.getLogger("").addHandler(logHandler);
    }
    context.setAttribute(LOG_HANDLER, logHandler);

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

    int partition;
    String partitionString = getAttributeOrParam(context, PARTITION_KEY);
    if (partitionString == null) {
      partition = 0;
      log.info("No partition specified, so it is implicitly partition #{}", partition);
    } else {
      partition = Integer.parseInt(partitionString);
      log.info("Running as partition #{}", partition);
    }
    context.setAttribute(AbstractMyrrixServlet.PARTITION_KEY, partition);

    final String bucket = getAttributeOrParam(context, BUCKET_KEY);

    RescorerProvider rescorerProvider;
    try {
      rescorerProvider = loadRescorerProvider(context, bucket);
    } catch (IOException ioe) {
      throw new IllegalStateException(ioe);
    } catch (ClassNotFoundException cnfe) {
      throw new IllegalStateException(cnfe);
    }
    if (rescorerProvider != null) {
      context.setAttribute(AbstractMyrrixServlet.RESCORER_PROVIDER_KEY, rescorerProvider);
    }

    final String portString = getAttributeOrParam(context, PORT_KEY);
    final String instanceID = getAttributeOrParam(context, INSTANCE_ID_KEY);
    final String allPartitionsSpecString = getAttributeOrParam(context, ALL_PARTITIONS_SPEC_KEY);

    ReloadingReference<List<List<Pair<String,Integer>>>> allPartitionsReference = null;
    if (allPartitionsSpecString != null) {
      allPartitionsReference =
          new ReloadingReference<List<List<Pair<String,Integer>>>>(new Callable<List<List<Pair<String,Integer>>>>() {
            @Override
            public List<List<Pair<String, Integer>>> call() {
              if (RunnerConfiguration.AUTO_PARTITION_SPEC.equals(allPartitionsSpecString)) {
                int port = Integer.parseInt(portString);
                PartitionLoader loader =
                    ClassUtils.loadInstanceOf("net.myrrix.online.partition.PartitionLoaderImpl", PartitionLoader.class);
                List<List<Pair<String, Integer>>> newPartitions = loader.loadPartitions(port, bucket, instanceID);
                log.info("Latest partitions: {}", newPartitions);
                return newPartitions;
              }
              return PartitionsUtils.parseAllPartitions(allPartitionsSpecString);
            }
          }, 10, TimeUnit.MINUTES);

      // "Tickle" it to pre-load and check for errors
      allPartitionsReference.get();
      context.setAttribute(AbstractMyrrixServlet.ALL_PARTITIONS_REF_KEY, allPartitionsReference);
    }

    MyrrixRecommender recommender =
        new ServerRecommender(bucket, instanceID, localInputDir, partition, allPartitionsReference);
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

  private static RescorerProvider loadRescorerProvider(ServletContext context, String bucket)
      throws IOException, ClassNotFoundException {
    String rescorerProviderClassName = getAttributeOrParam(context, RESCORER_PROVIDER_CLASS_KEY);
    if (rescorerProviderClassName == null) {
      return null;
    }

    log.info("Using RescorerProvider class {}", rescorerProviderClassName);
    if (ClassUtils.classExists(rescorerProviderClassName)) {
      log.info("Found class in local classpath");
      return ClassUtils.loadInstanceOf(rescorerProviderClassName, RescorerProvider.class);
    }

    log.info("Class doesn't exist in local classpath");
    ResourceRetriever resourceRetriever =
        ClassUtils.loadInstanceOf("net.myrrix.online.io.DelegateResourceRetriever", ResourceRetriever.class);
    resourceRetriever.init(bucket);
    File tempResourceFile = resourceRetriever.getRescorerJar();
    if (tempResourceFile == null) {
      log.info("No external rescorer JAR is available in this implementation");
      throw new ClassNotFoundException(rescorerProviderClassName);
    }

    log.info("Loading {} from {}, copied from remote JAR at key {}",
             rescorerProviderClassName, tempResourceFile, tempResourceFile);
    RescorerProvider rescorerProvider = ClassUtils.loadFromRemote(rescorerProviderClassName,
                                                                  RescorerProvider.class,
                                                                  tempResourceFile.toURI().toURL());
    if (!tempResourceFile.delete()) {
      log.info("Could not delete {}", tempResourceFile);
    }
    return rescorerProvider;
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
