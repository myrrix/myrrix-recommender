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
import java.util.regex.Pattern;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.common.net.HostAndPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.ClassUtils;
import net.myrrix.common.ReloadingReference;
import net.myrrix.common.io.IOUtils;
import net.myrrix.common.MyrrixRecommender;
import net.myrrix.common.PartitionsUtils;
import net.myrrix.common.log.MemoryHandler;
import net.myrrix.online.AbstractRescorerProvider;
import net.myrrix.online.ClientThread;
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
 * @since 1.0
 */
public final class InitListener implements ServletContextListener {

  private static final Logger log = LoggerFactory.getLogger(InitListener.class);

  private static final String KEY_PREFIX = InitListener.class.getName();
  public static final String LOG_HANDLER = KEY_PREFIX + ".LOG_HANDLER";
  public static final String LOCAL_INPUT_DIR_KEY = KEY_PREFIX + ".LOCAL_INPUT_DIR";
  public static final String PORT_KEY = KEY_PREFIX + ".PORT";
  public static final String READ_ONLY_KEY = KEY_PREFIX + ".READ_ONLY";
  public static final String BUCKET_KEY = KEY_PREFIX + ".BUCKET";
  public static final String INSTANCE_ID_KEY = KEY_PREFIX + ".INSTANCE_ID";
  public static final String RESCORER_PROVIDER_CLASS_KEY = KEY_PREFIX + ".RESCORER_PROVIDER_CLASS";
  public static final String CLIENT_THREAD_CLASS_KEY = KEY_PREFIX + ".CLIENT_THREAD_CLASS";  
  public static final String ALL_PARTITIONS_SPEC_KEY = KEY_PREFIX + ".ALL_PARTITIONS_SPEC";
  public static final String PARTITION_KEY = KEY_PREFIX + ".PARTITION";
  public static final String LICENSE_FILE_KEY = KEY_PREFIX + ".LICENSE_FILE";

  private static final Pattern COMMA = Pattern.compile(",");

  private File tempDirToDelete;
  private ClientThread clientThread;

  @Override
  public void contextInitialized(ServletContextEvent event) {
    log.info("Initializing Myrrix in servlet context...");
    ServletContext context = event.getServletContext();

    configureLogging(context);
    
    configureTempDir(context);
    
    File localInputDir = configureLocalInputDir(context);
    int partition = configurePartition(context);
    
    String bucket = getAttributeOrParam(context, BUCKET_KEY);
    String instanceID = getAttributeOrParam(context, INSTANCE_ID_KEY);
    
    configureRescorerProvider(context, bucket, instanceID);
    
    ReloadingReference<List<List<HostAndPort>>> allPartitionsReference = 
        configureAllPartitionsReference(context, bucket, instanceID);
    
    File licenseFile = (File) context.getAttribute(LICENSE_FILE_KEY);
    MyrrixRecommender recommender =
        new ServerRecommender(bucket, instanceID, localInputDir, partition, allPartitionsReference, licenseFile);
    context.setAttribute(AbstractMyrrixServlet.RECOMMENDER_KEY, recommender);
    
    configureClientThread(context, bucket, instanceID, recommender);

    log.info("Myrrix is initialized");
  }

  private static void configureLogging(ServletContext context) {
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
  }

  /**
   * This is a possible workaround for Tomcat on Windows, not creating the temp dir it allocates?
   */
  private static void configureTempDir(ServletContext context) {
    File tempDir = (File) context.getAttribute(ServletContext.TEMPDIR);
    Preconditions.checkNotNull(tempDir, "Servlet container didn't set %s", ServletContext.TEMPDIR);
    if (!tempDir.exists()) {
      log.warn("{} was set to {} but it did not exist", ServletContext.TEMPDIR, tempDir);
      if (!tempDir.mkdirs()) {
        log.warn("Failed to create dir {}", tempDir);
      }
    } else if (tempDir.isFile()) {
      log.warn("{} is a file, not directory", tempDir);
    }
  }
  
  private File configureLocalInputDir(ServletContext context) {
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
    return localInputDir;
  }
  
  private static int configurePartition(ServletContext context) {
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
    return partition;
  }
  
  private static void configureRescorerProvider(ServletContext context, String bucket, String instanceID) {
    RescorerProvider rescorerProvider;
    try {
      rescorerProvider = loadRescorerProvider(context, bucket, instanceID);
    } catch (IOException ioe) {
      throw new IllegalStateException(ioe);
    } catch (ClassNotFoundException cnfe) {
      throw new IllegalStateException(cnfe);
    }
    if (rescorerProvider != null) {
      context.setAttribute(AbstractMyrrixServlet.RESCORER_PROVIDER_KEY, rescorerProvider);
    }
  }
  
  private static ReloadingReference<List<List<HostAndPort>>> configureAllPartitionsReference(ServletContext context, 
                                                                                             final String bucket, 
                                                                                             final String instanceID) {
    boolean readOnly = Boolean.parseBoolean(getAttributeOrParam(context, READ_ONLY_KEY));
    context.setAttribute(AbstractMyrrixServlet.READ_ONLY_KEY, readOnly);

    final String portString = getAttributeOrParam(context, PORT_KEY);
    final String allPartitionsSpecString = getAttributeOrParam(context, ALL_PARTITIONS_SPEC_KEY);

    ReloadingReference<List<List<HostAndPort>>> allPartitionsReference = null;
    if (allPartitionsSpecString != null) {
      allPartitionsReference =
          new ReloadingReference<List<List<HostAndPort>>>(new Callable<List<List<HostAndPort>>>() {
            @Override
            public List<List<HostAndPort>> call() {
              if (RunnerConfiguration.AUTO_PARTITION_SPEC.equals(allPartitionsSpecString)) {
                int port = Integer.parseInt(portString);
                PartitionLoader loader =
                    ClassUtils.loadInstanceOf("net.myrrix.online.partition.PartitionLoaderImpl", 
                                              PartitionLoader.class);
                List<List<HostAndPort>> newPartitions = loader.loadPartitions(port, bucket, instanceID);
                log.debug("Latest partitions: {}", newPartitions);
                return newPartitions;
              }
              return PartitionsUtils.parseAllPartitions(allPartitionsSpecString);
            }
          }, 10, TimeUnit.MINUTES);

      // "Tickle" it to pre-load and check for errors
      allPartitionsReference.get();
      context.setAttribute(AbstractMyrrixServlet.ALL_PARTITIONS_REF_KEY, allPartitionsReference);
    }
    return allPartitionsReference;
  }
  
  private void configureClientThread(ServletContext context, 
                                     String bucket,
                                     String instanceID,
                                     MyrrixRecommender recommender) {
    ClientThread theThread;
    try {
      theThread = loadClientThreadClass(context, bucket, instanceID);
    } catch (IOException ioe) {
      throw new IllegalStateException(ioe);
    } catch (ClassNotFoundException cnfe) {
      throw new IllegalStateException(cnfe);
    }
    if (theThread != null) {
      theThread.setRecommender(recommender);        
      clientThread = theThread;
      new Thread(theThread, "MyrrixClientThread").start();
    }
  }

  private static String getAttributeOrParam(ServletContext context, String key) {
    Object valueObject = context.getAttribute(key);
    String valueString = valueObject == null ? null : valueObject.toString();
    if (valueString == null) {
      valueString = context.getInitParameter(key);
    }
    return valueString;
  }

  private static RescorerProvider loadRescorerProvider(ServletContext context, String bucket, String instanceID)
      throws IOException, ClassNotFoundException {
    String rescorerProviderClassNames = getAttributeOrParam(context, RESCORER_PROVIDER_CLASS_KEY);
    if (rescorerProviderClassNames == null) {
      return null;
    }

    log.info("Using RescorerProvider class(es) {}", rescorerProviderClassNames);
    boolean allClassesFound = true;
    for (String rescorerProviderClassName : COMMA.split(rescorerProviderClassNames)) {
      if (!ClassUtils.classExists(rescorerProviderClassName)) {
        allClassesFound = false;
        break;
      }
    }
    if (allClassesFound) {
      log.info("Found class(es) in local classpath");
      return AbstractRescorerProvider.loadRescorerProviders(rescorerProviderClassNames, null);
    }

    log.info("Class doesn't exist in local classpath");
    ResourceRetriever resourceRetriever =
        ClassUtils.loadInstanceOf("net.myrrix.online.io.DelegateResourceRetriever", ResourceRetriever.class);
    resourceRetriever.init(bucket);
    File tempResourceFile = resourceRetriever.getRescorerJar(instanceID);
    if (tempResourceFile == null) {
      log.info("No external rescorer JAR is available in this implementation");
      throw new ClassNotFoundException(rescorerProviderClassNames);
    }

    log.info("Loading class(es) {} from {}, copied from remote JAR at key {}",
             rescorerProviderClassNames, tempResourceFile, tempResourceFile);
    RescorerProvider rescorerProvider = 
        AbstractRescorerProvider.loadRescorerProviders(rescorerProviderClassNames, 
                                                       tempResourceFile.toURI().toURL());

    if (!tempResourceFile.delete()) {
      log.info("Could not delete {}", tempResourceFile);
    }
    return rescorerProvider;
  }
  
  private static ClientThread loadClientThreadClass(ServletContext context, String bucket, String instanceID) 
      throws IOException, ClassNotFoundException {
    String clientThreadClassName = getAttributeOrParam(context, CLIENT_THREAD_CLASS_KEY);
    if (clientThreadClassName == null) {
      return null;
    }
    
    log.info("Using Runnable/Closeable client thread class {}", clientThreadClassName);
    if (ClassUtils.classExists(clientThreadClassName)) {
      log.info("Found class on local classpath");
      return ClassUtils.loadInstanceOf(clientThreadClassName, ClientThread.class);
    }
    
    log.info("Class doesn't exist in local classpath");
    
    ResourceRetriever resourceRetriever =
        ClassUtils.loadInstanceOf("net.myrrix.online.io.DelegateResourceRetriever", ResourceRetriever.class);
    resourceRetriever.init(bucket);
    File tempResourceFile = resourceRetriever.getClientThreadJar(instanceID);
    if (tempResourceFile == null) {
      log.info("No external client thread JAR is available in this implementation");
      throw new ClassNotFoundException(clientThreadClassName);
    }
    
    log.info("Loading class {} from {}, copied from remote JAR at key {}",
             clientThreadClassName, tempResourceFile, tempResourceFile);
    
    ClientThread clientThreadRunnable = 
        ClassUtils.loadFromRemote(clientThreadClassName, ClientThread.class, tempResourceFile.toURI().toURL());

    if (!tempResourceFile.delete()) {
      log.info("Could not delete {}", tempResourceFile);
    }
    return clientThreadRunnable;
  }

  @Override
  public void contextDestroyed(ServletContextEvent event) {
    log.info("Uninitializing Myrrix in servlet context...");
    
    ClientThread theClientThread = clientThread;
    if (theClientThread != null) {
      try {
        theClientThread.close();
      } catch (IOException e) {
        log.warn("Error while closing client thread", e);
      }
    }
    
    ServletContext context = event.getServletContext();
    Closeable recommender = (Closeable) context.getAttribute(AbstractMyrrixServlet.RECOMMENDER_KEY);
    if (recommender != null) {
      try {
        recommender.close();
      } catch (IOException e) {
        log.warn("Unexpected error while closing", e);
      }
    }
    IOUtils.deleteRecursively(tempDirToDelete);
    log.info("Myrrix is uninitialized");
  }

}
