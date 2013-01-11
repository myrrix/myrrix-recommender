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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.servlet.MultipartConfigElement;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.CliFactory;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Server;
import org.apache.catalina.Wrapper;
import org.apache.catalina.authenticator.DigestAuthenticator;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.JasperListener;
import org.apache.catalina.core.JreMemoryLeakPreventionListener;
import org.apache.catalina.core.ThreadLocalLeakPreventionListener;
import org.apache.catalina.deploy.ErrorPage;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.deploy.SecurityCollection;
import org.apache.catalina.deploy.SecurityConstraint;
import org.apache.catalina.startup.Tomcat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.ClassUtils;
import net.myrrix.common.io.IOUtils;
import net.myrrix.common.log.MemoryHandler;
import net.myrrix.common.signal.SignalManager;
import net.myrrix.common.signal.SignalType;
import net.myrrix.online.io.ResourceRetriever;
import net.myrrix.web.servlets.AllItemIDsServlet;
import net.myrrix.web.servlets.AllUserIDsServlet;
import net.myrrix.web.servlets.BecauseServlet;
import net.myrrix.web.servlets.EstimateServlet;
import net.myrrix.web.servlets.IngestServlet;
import net.myrrix.web.servlets.LogServlet;
import net.myrrix.web.servlets.MostPopularItemsServlet;
import net.myrrix.web.servlets.PreferenceServlet;
import net.myrrix.web.servlets.ReadyServlet;
import net.myrrix.web.servlets.RecommendServlet;
import net.myrrix.web.servlets.RecommendToAnonymousServlet;
import net.myrrix.web.servlets.RecommendToManyServlet;
import net.myrrix.web.servlets.RefreshServlet;
import net.myrrix.web.servlets.SimilarityServlet;
import net.myrrix.web.servlets.SimilarityToItemServlet;

/**
 * <p>This is the runnable class which starts the Serving Layer and its Tomcat-based HTTP server. It is
 * started with {@link #call()} and can be shut down with {@link #close()}. This implementation is used
 * both in stand-alone local mode, and in a distributed mode cooperating with a Computation Layer.</p>
 *
 * <p>This program instantiates a Tomcat-based HTTP server exposing a REST-style API. It is available via
 * HTTP, or HTTPS as well if {@link RunnerConfiguration#getKeystoreFile()} is set. It can also be password
 * protected by setting {@link RunnerConfiguration#getUserName()} and
 * {@link RunnerConfiguration#getPassword()}.</p>
 *
 * <p>{@link Runner} is configured by {@link RunnerConfiguration} but when run as a command-line program,
 * it is configured via a set of analogous flags:</p>
 *
 * <ul>
 *   <li>{@code --localInputDir}: Optional. The local directory used for reading input, writing output, and storing
 *   user input and model files in local mode. It is used for staging input for upload in distributed mode.
 *   Defaults to the system temp directory.</li>
 *   <li>{@code --bucket}: Identifies the root directory of storage under which data is stored and computation takes
 *    place in distributed mode. Only applicable in distributed mode. Must be set with {@code --instanceID}.</li>
 *   <li>{@code --instanceID}: Uniquely identifies the recommender from others that may be run by the same
 *    organization. Only applicable in distributed mode. Must be set with {@code --bucket}.</li>
 *   <li>{@code --port}: Port on which to listen for HTTP requests. Defaults to 80. Note that the server must be run
 *   as the root user to access port 80.</li>
 *   <li>{@code --securePort}: Port on which to listen for HTTPS requests. Defaults to 443. Likewise note that
 *   using port 443 requires running as root.</li>
 *   <li>{@code --readOnly}: If set, disables methods and endpoints that add, remove or change data</li>
 *   <li>{@code --keystoreFile}: File containing the SSL key to use for HTTPS. Setting this flag
 *   enables HTTPS connections, and so requires that option {@code --keystorePassword} be set. In distributed
 *   mode, if not set, will attempt to load a keystore file from the distributed file system,
 *   at {@code sys/keystore.ks}</li>
 *   <li>{@code --keystorePassword}: Password for keystoreFile. Setting this flag enables HTTPS connections.</li>
 *   <li>{@code --userName}: If specified, the user name required to authenticate to the server using
 *   HTTP DIGEST authentication. Requires password to be set.</li>
 *   <li>{@code --password}: Password for HTTP DIGEST authentication. Requires userName to be set.</li>
 *   <li>{@code --consoleOnlyPassword}: Only apply username and password to admin / console pages.</li>
 *   <li>{@code --rescorerProviderClass}: Optional. Name of an implementation of
 *     {@code RescorerProvider} to use to rescore recommendations and similarities, if any. The class
 *     must be added to the server classpath. Or, in distributed mode, if not found in the classpath, it
 *     will be loaded from a JAR file found on the distributed file system at {@code sys/rescorer.jar}.</li>
 *   <li>{@code --allPartitions}: Optional, but must be set with {@code --partition}.
 *     Describes all partitions, when partitioning across Serving Layers
 *     by user. Each partition may have multiple replicas. When running in distibuted mode on Amazon EC2,
 *     may be specified as "auto", in which case it will
 *     attempt to discover partition members dynamically, searching for instances tagged with EC2 key
 *     "myrrix-partition" and whose value is a partition. (Port may be specified with EC2 tag "myrrix-port" if not
 *     the default of 80, and, instances may be uniquely associated to a bucket and instance with "myrrix-bucket" and
 *     "myrrix-instanceID" EC2 tags if needed.) Otherwise, replicas are specified as many Serving Layer
 *     "host:port" pairs, separated by commas, like "rep1:port1,rep2:port2,...".
 *     Finally, partitions are specified as multiple replicas separated by semicolon, like
 *     "part1rep1:port11,part1rep2:port12;part2rep1:port21,part2rep2:port22;...". Example:
 *     "foo:80,foo2:8080;bar:8080;baz2:80,baz3:80"</li>
 *   <li>{@code --partition}: Optional, but must be set with {@code --allPartitions}.
 *     The partition (0-based) that this is Serving Layer is serving.</li>
 *   <li>{@code --licenseFile}: (Optional in standalone mode). location of a license file named [subject].lic,
 *     where [subject] is the subject name authorized in the license. The license file should be valid at the
 *     time the app is run, and contain authorization to use the amount of parallelism (max simultaneous
 *     Hadoop workers) requested.</li>
 * </ul>
 *
 * <p>When run in local mode, the Serving Layer instance will compute a model locally and save it as the file
 * {@code model.bin} in the {@code --localInputDir} directory. It will be updated when the model is rebuilt.
 * If the file is present at startup, it will be read to restore the server state, rather than re-reading
 * CSV input in the directory and recomputing the model. Thus the file can be saved and restored as a
 * way of preserving and recalling the server's state of learning.</p>
 *
 * <p>Example of running in local mode:</p>
 *
 * <p>{@code java -jar myrrix-serving-x.y.jar --port 8080}</p>
 *
 * <p>(with an example of JVM tuning flags:)</p>
 *
 * <p>{@code java -Xmx1g -XX:NewRatio=12 -XX:+UseParallelOldGC -jar myrrix-serving-x.y.jar --port 8080}</p>
 *
 * <p>Finally, some more advanced tuning parameters are available. These are system parameters, set with
 * {@code -Dproperty=value}.</p>
 *
 * <ul>
 *   <li>{@code model.features}: The number of features used in building the underlying user-feature and
 *   item-feature matrices. Typical values are 30-100. Defaults to
 *   {@code MatrixFactorizer#DEFAULT_FEATURES}.</li>
 *   <li>{@code model.iterations}: The number of iterations used to refine the model. Typical values are 3-10.
 *   Defaults to {@code MatrixFactorizer#DEFAULT_ITERATIONS}.</li>
 *   <li>{@code model.als.lambda}: Controls the lambda overfitting parameter in the ALS algorithm.
 *    Typical values are near 0.1. Do not change this, in general. Defaults to
 *    {@code AlternatingLeastSquares#DEFAULT_LAMBDA}.</li>
 *   <li>{@code model.als.alpha}: Controls the alpha scaling parameter in the ALS algorithm.
 *    Typical values are near 40. Do not change this, in general. Defaults to
 *    {@code AlternatingLeastSquares#DEFAULT_ALPHA}.</li>
 * </ul>
 *
 * @author Sean Owen
 */
public final class Runner implements Callable<Boolean>, Closeable {

  private static final Logger log = LoggerFactory.getLogger(Runner.class);

  private static final int[] ERROR_PAGE_STATUSES = {
      HttpServletResponse.SC_BAD_REQUEST,
      HttpServletResponse.SC_UNAUTHORIZED,
      HttpServletResponse.SC_NOT_FOUND,
      HttpServletResponse.SC_METHOD_NOT_ALLOWED,
      HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
      HttpServletResponse.SC_SERVICE_UNAVAILABLE,
  };

  private final RunnerConfiguration config;
  private Tomcat tomcat;
  private final File noSuchBaseDir;
  private boolean closed;

  /**
   * Creates a new instance with the given configuration.
   */
  public Runner(RunnerConfiguration config) {
    Preconditions.checkNotNull(config);
    this.config = config;
    this.noSuchBaseDir = Files.createTempDir();
    this.noSuchBaseDir.deleteOnExit();
  }

  /**
   * @return the underlying {@link Tomcat} server that is being configured and run inside this instance.
   */
  public Tomcat getTomcat() {
    return tomcat;
  }

  public static void main(String[] args) throws Exception {

    RunnerConfiguration config;
    try {
      RunnerArgs runnerArgs = CliFactory.parseArguments(RunnerArgs.class, args);
      config = buildConfiguration(runnerArgs);
    } catch (ArgumentValidationException ave) {
      printHelp(ave.getMessage());
      return;
    }

    final Runner runner = new Runner(config);
    runner.call();

    SignalManager.register(new Runnable() {
        @Override
        public void run() {
          runner.close();
        }
      }, SignalType.INT, SignalType.TERM);

    runner.await();
    runner.close();
  }

  private static RunnerConfiguration buildConfiguration(RunnerArgs runnerArgs) {

    RunnerConfiguration config = new RunnerConfiguration();

    config.setPort(runnerArgs.getPort());
    config.setSecurePort(runnerArgs.getSecurePort());
    config.setReadOnly(runnerArgs.isReadOnly());
    config.setLocalInputDir(runnerArgs.getLocalInputDir());

    boolean instanceIDSet = runnerArgs.getInstanceID() != null;
    boolean bucketSet = runnerArgs.getBucket() != null;
    if (instanceIDSet != bucketSet) {
      throw new ArgumentValidationException("Must set both --instanceID and --bucket together");
    }
    if (instanceIDSet && bucketSet) {
      config.setInstanceID(runnerArgs.getInstanceID());
      config.setBucket(runnerArgs.getBucket());
    }

    config.setUserName(runnerArgs.getUserName());
    config.setPassword(runnerArgs.getPassword());
    config.setConsoleOnlyPassword(runnerArgs.isConsoleOnlyPassword());
    config.setKeystoreFile(runnerArgs.getKeystoreFile());
    config.setKeystorePassword(runnerArgs.getKeystorePassword());
    config.setRescorerProviderClassName(runnerArgs.getRescorerProviderClass());

    boolean hasPartition = runnerArgs.getPartition() != null;
    boolean hasAllPartitions = runnerArgs.getAllPartitions() != null;
    if (hasPartition != hasAllPartitions) {
      throw new ArgumentValidationException("Must set --partition and --allPartitions together");
    }

    if (hasPartition && hasAllPartitions) {
      config.setAllPartitionsSpecification(runnerArgs.getAllPartitions());
      config.setPartition(runnerArgs.getPartition());
    }

    config.setLicenseFile(runnerArgs.getLicenseFile());

    return config;
  }

  @Override
  public Boolean call() throws IOException {

    MemoryHandler.setSensibleLogFormat();
    java.util.logging.Logger.getLogger("").addHandler(new MemoryHandler());

    Tomcat tomcat = new Tomcat();
    Connector connector = makeConnector();
    configureTomcat(tomcat, connector);
    configureEngine(tomcat.getEngine());
    configureServer(tomcat.getServer());
    configureHost(tomcat.getHost());
    Context context = makeContext(tomcat, noSuchBaseDir, connector.getPort());

    addServlet(context, new RecommendServlet(), "/recommend/*");
    addServlet(context, new RecommendToManyServlet(), "/recommendToMany/*");
    addServlet(context, new RecommendToAnonymousServlet(), "/recommendToAnonymous/*");
    addServlet(context, new SimilarityServlet(), "/similarity/*");
    addServlet(context, new SimilarityToItemServlet(), "/similarityToItem/*");
    addServlet(context, new EstimateServlet(), "/estimate/*");
    addServlet(context, new BecauseServlet(), "/because/*");
    addServlet(context, new ReadyServlet(), "/ready/*");
    addServlet(context, new AllUserIDsServlet(), "/user/allIDs/*");
    addServlet(context, new AllItemIDsServlet(), "/item/allIDs/*");
    addServlet(context, new MostPopularItemsServlet(), "/mostPopularItems/*");

    if (!config.isReadOnly()) {
      addServlet(context, new PreferenceServlet(), "/pref/*");
      Wrapper ingestWrapper = addServlet(context, new IngestServlet(), "/ingest/*");
      ingestWrapper.setMultipartConfigElement(new MultipartConfigElement("/tmp"));
      addServlet(context, new RefreshServlet(), "/refresh/*");
    }

    addServlet(context, new index_jspx(), "/index.jspx");
    addServlet(context, new status_jspx(), "/status.jspx");
    addServlet(context, new error_jspx(), "/error.jspx");
    addServlet(context, new som_jspx(), "/som.jspx");
    addServlet(context, new LogServlet(), "/log.txt");

    try {
      tomcat.start();
    } catch (LifecycleException le) {
      throw new IOException(le);
    }
    this.tomcat = tomcat;
    return Boolean.TRUE;
  }

  /**
   * Blocks and waits until the server shuts down.
   */
  public void await() {
    tomcat.getServer().await();
  }

  @Override
  public synchronized void close() {
    if (!closed) {
      closed = true;
      if (tomcat != null) {
        try {
          tomcat.stop();
          tomcat.destroy();
        } catch (LifecycleException le) {
          log.warn("Unexpected error while stopping", le);
        }
        if (!IOUtils.deleteRecursively(noSuchBaseDir)) {
          log.info("Could not delete {}", noSuchBaseDir);
        }
      }
    }
  }

  private static void printHelp(String message) {
    System.out.println();
    System.out.println("Myrrix Serving Layer. Copyright Myrrix Ltd, except for included ");
    System.out.println("third-party open source software. Full details of licensing at http://myrrix.com/legal/");
    System.out.println();
    if (message != null) {
      System.out.println(message);
      System.out.println();
    }
  }

  private void configureTomcat(Tomcat tomcat, Connector connector) {
    tomcat.setBaseDir(noSuchBaseDir.getAbsolutePath());
    tomcat.setConnector(connector);
    tomcat.getService().addConnector(connector);
  }

  private void configureEngine(Engine engine) {
    String userName = config.getUserName();
    String password = config.getPassword();
    if (userName != null && password != null) {
      InMemoryRealm realm = new InMemoryRealm();
      realm.addUser(userName, password);
      engine.setRealm(realm);
    }
  }

  private static void configureServer(Server server) {
    //server.addLifecycleListener(new SecurityListener());
    //server.addLifecycleListener(new AprLifecycleListener());
    LifecycleListener jasperListener = new JasperListener();
    server.addLifecycleListener(jasperListener);
    jasperListener.lifecycleEvent(new LifecycleEvent(server, Lifecycle.BEFORE_INIT_EVENT, null));
    server.addLifecycleListener(new JreMemoryLeakPreventionListener());
    //server.addLifecycleListener(new GlobalResourcesLifecycleListener());
    server.addLifecycleListener(new ThreadLocalLeakPreventionListener());
  }

  private static void configureHost(Host host) {
    host.setAutoDeploy(false);
  }

  private Connector makeConnector() throws IOException {
    Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
    File keystoreFile = config.getKeystoreFile();
    String keystorePassword = config.getKeystorePassword();
    if (keystoreFile == null && keystorePassword == null) {
      // HTTP connector
      connector.setPort(config.getPort());
      connector.setSecure(false);
      connector.setScheme("http");

    } else {

      if (keystoreFile == null || !keystoreFile.exists()) {
        log.info("Keystore file not found; trying to load remote keystore file if applicable");
        ResourceRetriever resourceRetriever =
            ClassUtils.loadInstanceOf("net.myrrix.online.io.DelegateResourceRetriever", ResourceRetriever.class);
        resourceRetriever.init(config.getBucket());
        keystoreFile = resourceRetriever.getKeystoreFile();
        if (keystoreFile == null) {
          throw new FileNotFoundException();
        }
      }

      // HTTPS connector
      connector.setPort(config.getSecurePort());
      connector.setSecure(true);
      connector.setScheme("https");
      connector.setAttribute("SSLEnabled", "true");
      connector.setAttribute("sslProtocol", "TLS");
      connector.setAttribute("clientAuth", "false");
      connector.setAttribute("keystoreFile", keystoreFile.getAbsoluteFile());
      connector.setAttribute("keystorePass", keystorePassword);
    }

    // Keep quiet about the server type
    connector.setXpoweredBy(false);
    connector.setAttribute("server", "Myrrix");

    // Basic tuning params:
    connector.setAttribute("maxThreads", 400);
    connector.setAttribute("acceptCount", 50);
    //connector.setAttribute("connectionTimeout", 2000);
    connector.setAttribute("maxKeepAliveRequests", 100);

    // Avoid running out of ephemeral ports under heavy load?
    connector.setAttribute("socket.soReuseAddress", true);

    return connector;
  }

  private Context makeContext(Tomcat tomcat, File noSuchBaseDir, int port) throws IOException {

    File contextPath = new File(noSuchBaseDir, "context");
    if (!contextPath.mkdirs()) {
      throw new IOException("Could not create " + contextPath);
    }

    Context context = tomcat.addContext("", contextPath.getAbsolutePath());
    context.addApplicationListener(InitListener.class.getName());
    context.setWebappVersion("3.0");
    context.addWelcomeFile("index.jspx");
    addErrorPages(context);

    ServletContext servletContext = context.getServletContext();
    servletContext.setAttribute(InitListener.INSTANCE_ID_KEY, config.getInstanceID());
    servletContext.setAttribute(InitListener.BUCKET_KEY, config.getBucket());
    servletContext.setAttribute(InitListener.RESCORER_PROVIDER_CLASS_KEY, config.getRescorerProviderClassName());
    servletContext.setAttribute(InitListener.LOCAL_INPUT_DIR_KEY, config.getLocalInputDir());
    servletContext.setAttribute(InitListener.PORT_KEY, port);
    servletContext.setAttribute(InitListener.READ_ONLY_KEY, config.isReadOnly());
    servletContext.setAttribute(InitListener.ALL_PARTITIONS_SPEC_KEY, config.getAllPartitionsSpecification());
    servletContext.setAttribute(InitListener.PARTITION_KEY, config.getPartition());
    servletContext.setAttribute(InitListener.LICENSE_FILE_KEY, config.getLicenseFile());

    boolean needHTTPS = config.getKeystoreFile() != null;
    boolean needAuthentication = config.getUserName() != null;

    if (needHTTPS || needAuthentication) {

      SecurityConstraint securityConstraint = new SecurityConstraint();
      SecurityCollection securityCollection = new SecurityCollection("Protected Resources");
      if (config.isConsoleOnlyPassword()) {
        securityCollection.addPattern("/index.jspx");
      } else {
        securityCollection.addPattern("/*");
      }
      securityConstraint.addCollection(securityCollection);

      if (needHTTPS) {
        securityConstraint.setUserConstraint("CONFIDENTIAL");
      }

      if (needAuthentication) {

        LoginConfig loginConfig = new LoginConfig();
        loginConfig.setAuthMethod("DIGEST");
        loginConfig.setRealmName(InMemoryRealm.NAME);
        context.setLoginConfig(loginConfig);

        securityConstraint.addAuthRole(InMemoryRealm.AUTH_ROLE);

        enableHttpDigestHack();

        context.addSecurityRole(InMemoryRealm.AUTH_ROLE);
        DigestAuthenticator authenticator = new DigestAuthenticator();
        authenticator.setNonceValidity(10 * 1000L); // Shorten from 5 minutes to 10 seconds
        authenticator.setNonceCacheSize(20000); // Increase from 1000 to 20000
        context.getPipeline().addValve(authenticator);
      }

      context.addConstraint(securityConstraint);
    }

    context.setCookies(false);

    return context;
  }

  private static Wrapper addServlet(Context context, Servlet servlet, String path) {
    String name = servlet.getClass().getSimpleName();
    Wrapper servletWrapper = Tomcat.addServlet(context, name, servlet);
    servletWrapper.setLoadOnStartup(1);
    context.addServletMapping(path, name);
    return servletWrapper;
  }

  private static void addErrorPages(Context context) {
    for (int errorCode : ERROR_PAGE_STATUSES) {
      ErrorPage errorPage = new ErrorPage();
      errorPage.setErrorCode(errorCode);
      errorPage.setLocation("/error.jspx");
      context.addErrorPage(errorPage);
    }
    ErrorPage errorPage = new ErrorPage();
    errorPage.setExceptionType(Throwable.class.getName());
    errorPage.setLocation("/error.jspx");
    context.addErrorPage(errorPage);
  }

  /**
   * See https://issues.apache.org/bugzilla/show_bug.cgi?id=54060
   * TODO hopefully remove this one day
   */
  private static void enableHttpDigestHack() {
    try {
      Class<?> httpParserClass = Class.forName("org.apache.tomcat.util.http.parser.HttpParser");
      Field fieldTypeField = httpParserClass.getDeclaredField("FIELD_TYPE_TOKEN_OR_QUOTED_STRING");
      fieldTypeField.setAccessible(true);
      Integer FIELD_TYPE_TOKEN_OR_QUOTED_STRING = (Integer) fieldTypeField.get(null);

      Field fieldTypesField = httpParserClass.getDeclaredField("fieldTypes");
      fieldTypesField.setAccessible(true);
      Map<String,Integer> fieldTypes = (Map<String,Integer>) fieldTypesField.get(null);

      // Clients are sending DIGEST responses with algorithm="MD5" instead of algorithm=MD5, which
      // is evidently correct by RFC 2617. Or qop="auth" instead of the correct qop=auth.
      // Tomcat 7.0.30+ got stricter. This change makes it lenient again.
      // The JVM, curl, and Safari seem to send these headers incorrectly!

      fieldTypes.put("algorithm", FIELD_TYPE_TOKEN_OR_QUOTED_STRING);
      fieldTypes.put("qop", FIELD_TYPE_TOKEN_OR_QUOTED_STRING);

    } catch (ClassNotFoundException e) {
      log.warn("HTTP DIGEST auth workaround for Tomcat 7 isn't working here; maybe we're not in Tomcat? {}",
               e.toString());
    } catch (NoSuchFieldException e) {
      log.warn("HTTP DIGEST auth workaround for Tomcat 7 isn't working here; maybe we're not in Tomcat? {}",
               e.toString());
    } catch (IllegalAccessException e) {
      log.warn("HTTP DIGEST auth workaround for Tomcat 7 isn't working here; maybe we're not in Tomcat? {}",
               e.toString());
    }
  }

}
