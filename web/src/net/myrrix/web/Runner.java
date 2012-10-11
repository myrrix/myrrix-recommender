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
import java.util.concurrent.Callable;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
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
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.log.MemoryHandler;
import net.myrrix.web.servlets.AllItemIDsServlet;
import net.myrrix.web.servlets.AllUserIDsServlet;
import net.myrrix.web.servlets.BecauseServlet;
import net.myrrix.web.servlets.EstimateServlet;
import net.myrrix.web.servlets.IngestServlet;
import net.myrrix.web.servlets.LogServlet;
import net.myrrix.web.servlets.PreferenceServlet;
import net.myrrix.web.servlets.ReadyServlet;
import net.myrrix.web.servlets.RecommendServlet;
import net.myrrix.web.servlets.RecommendToAnonymousServlet;
import net.myrrix.web.servlets.RecommendToManyServlet;
import net.myrrix.web.servlets.RefreshServlet;
import net.myrrix.web.servlets.SimilarityServlet;

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
 *   <li>{@code --keystoreFile}: File containing the SSL key to use for HTTPS. Note that setting this flag
 *   enables HTTPS connections, and so requires that options keystorePassword be set.</li>
 *   <li>{@code --keystorePassword}: Password for keystoreFile.</li>
 *   <li>{@code --userName}: If specified, the user name required to authenticate to the server using
 *   HTTP DIGEST authentication. Requires password to be set.</li>
 *   <li>{@code --password}: Password for HTTP DIGEST authentication. Requires userName to be set.</li>
 *   <li>{@code --consoleOnlyPassword}: Only apply username and password to admin / console pages.</li>
 *   <li>{@code --rescorerProviderClass}: Optional. Name of an implementation of
 *     {@code RescorerProvider} to use to rescore recommendations and similarities, if any. The class
 *     must be added to the server classpath.</li>
 *   <li>{@code --allPartitions}: Optional, but must be set with {@code --partition}.
 *     Describes all partitions, when partitioning across Serving Layers
 *     by user. Each partition may have multiple replicas. Serving Layers are specified as "host:port".
 *     Replicas are specified as many Serving Layers, separated by commas, like "rep1:port1,rep2:port2,...".
 *     Finally, partitions are specified as multiple replicas separated by semicolon, like
 *     "part1rep1:port11,part1rep2:port12;part2rep1:port21,part2rep2:port22;...". Example:
 *     "foo:80,foo2:8080;bar:8080;baz2:80,baz3:80"</li>
 *   <li>{@code --partition}: Optional, but must be set with {@code --allPartitions}.
 *     The partition (0-based) that this is Serving Layer is serving.</li>
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
 * <p>{@code java -jar myrrix-serving-x.y.jar --port=8080}</p>
 *
 * <p>(with an example of JVM tuning flags:)</p>
 *
 * <p>{@code java -jar myrrix-serving-x.y.jar -server -da -dsa -d64 -Xmx1g -XX:NewRatio=12
 *  -XX:+UseParallelGC -XX:+UseParallelOldGC --port=8080}</p>
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

  private static final String PORT_FLAG = "port";
  private static final String SECURE_PORT_FLAG = "securePort";
  private static final String LOCAL_INPUT_DIR_FLAG = "localInputDir";
  private static final String INSTANCE_ID_FLAG = "instanceID";
  private static final String BUCKET_FLAG = "bucket";
  private static final String USER_NAME_FLAG = "userName";
  private static final String CONSOLE_ONLY_PASSWORD_FLAG = "consoleOnlyPassword";
  private static final String PASSWORD_FLAG = "password";
  private static final String KEYSTORE_FILE_FLAG = "keystoreFile";
  private static final String KEYSTORE_PASSWORD_FLAG = "keystorePassword";
  private static final String RESCORER_PROVIDER_CLASS_FLAG = "rescorerProviderClass";
  private static final String PARTITION_FLAG = "partition";
  private static final String ALL_PARTITIONS_FLAG = "allPartitions";

  private static final int[] ERROR_PAGE_STATUSES = {
      HttpServletResponse.SC_BAD_REQUEST,
      HttpServletResponse.SC_NOT_FOUND,
      HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
      HttpServletResponse.SC_SERVICE_UNAVAILABLE,
  };

  private final RunnerConfiguration config;
  private Tomcat tomcat;
  private final File noSuchBaseDir;

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

    Options options = buildOptions();
    CommandLineParser parser = new PosixParser();

    RunnerConfiguration config;
    CommandLine commandLine;
    try {
      commandLine = parser.parse(options, args);
      config = buildConfiguration(commandLine);
    } catch (MissingOptionException moe) {
      printHelp(options);
      return;
    }

    if (commandLine.getArgs().length > 0) {
      printHelp(options);
      return;
    }

    Runner runner = new Runner(config);
    runner.call();
    runner.await();
    runner.close();
  }

  private static RunnerConfiguration buildConfiguration(CommandLine commandLine) throws ParseException {

    RunnerConfiguration config = new RunnerConfiguration();

    if (commandLine.hasOption(PORT_FLAG)) {
      config.setPort(Integer.parseInt(commandLine.getOptionValue(PORT_FLAG)));
    }
    if (commandLine.hasOption(SECURE_PORT_FLAG)) {
      config.setSecurePort(Integer.parseInt(commandLine.getOptionValue(SECURE_PORT_FLAG)));
    }

    if (commandLine.hasOption(LOCAL_INPUT_DIR_FLAG)) {
      config.setLocalInputDir(new File(commandLine.getOptionValue(LOCAL_INPUT_DIR_FLAG)));
    }
    boolean instanceIDSet = commandLine.hasOption(INSTANCE_ID_FLAG);
    boolean bucketSet = commandLine.hasOption(BUCKET_FLAG);
    if (instanceIDSet != bucketSet) {
      throw new MissingOptionException("Must set both --instanceID and --bucket together");
    }
    if (instanceIDSet && bucketSet) {
      config.setInstanceID(Long.parseLong(commandLine.getOptionValue(INSTANCE_ID_FLAG)));
      config.setBucket(commandLine.getOptionValue(BUCKET_FLAG));
    }

    if (commandLine.hasOption(USER_NAME_FLAG)) {
      config.setUserName(commandLine.getOptionValue(USER_NAME_FLAG));
    }
    if (commandLine.hasOption(PASSWORD_FLAG)) {
      config.setPassword(commandLine.getOptionValue(PASSWORD_FLAG));
    }

    config.setConsoleOnlyPassword(commandLine.hasOption(CONSOLE_ONLY_PASSWORD_FLAG));

    if (commandLine.hasOption(KEYSTORE_FILE_FLAG)) {
      config.setKeystoreFile(new File(commandLine.getOptionValue(KEYSTORE_FILE_FLAG)));
    }
    if (commandLine.hasOption(KEYSTORE_PASSWORD_FLAG)) {
      config.setKeystorePassword(commandLine.getOptionValue(KEYSTORE_PASSWORD_FLAG));
    }

    if (commandLine.hasOption(RESCORER_PROVIDER_CLASS_FLAG)) {
      config.setRescorerProviderClassName(commandLine.getOptionValue(RESCORER_PROVIDER_CLASS_FLAG));
    }

    boolean hasPartition = commandLine.hasOption(PARTITION_FLAG);
    boolean hasAllPartitions = commandLine.hasOption(ALL_PARTITIONS_FLAG);
    if (hasPartition != hasAllPartitions) {
      throw new MissingOptionException("Must set --partition and --allPartitions together");
    }

    if (hasPartition && hasAllPartitions) {
      config.setAllPartitionsSpecification(commandLine.getOptionValue(ALL_PARTITIONS_FLAG));
      config.setPartition(Integer.valueOf(commandLine.getOptionValue(PARTITION_FLAG)));
    }

    return config;
  }

  private static Options buildOptions() {
    Options options = new Options();
    addOption(options, "Working directory for input and intermediate files", LOCAL_INPUT_DIR_FLAG, true);
    addOption(options, "Bucket storing data to access", BUCKET_FLAG, true);
    addOption(options, "Instance ID to access", INSTANCE_ID_FLAG, true);
    addOption(options, "HTTP port number", PORT_FLAG, true);
    addOption(options, "HTTPS port number", SECURE_PORT_FLAG, true);
    addOption(options, "User name needed to authenticate to this instance", USER_NAME_FLAG, true);
    addOption(options, "Password to authenticate to this instance", PASSWORD_FLAG, true);
    addOption(options, "User name and password only apply to admin and console resources",
              CONSOLE_ONLY_PASSWORD_FLAG, false);
    addOption(options, "Test SSL certificate keystore to accept", KEYSTORE_FILE_FLAG, true);
    addOption(options, "Password for keystoreFile", KEYSTORE_PASSWORD_FLAG, true);
    addOption(options, "RescorerProvider implementation class", RESCORER_PROVIDER_CLASS_FLAG, true);
    addOption(options, "All partitions, as comma-separated host:port (e.g. foo1:8080,foo2:80,bar1:8081)",
              ALL_PARTITIONS_FLAG, true);
    addOption(options, "Server's partition number (0-based)", PARTITION_FLAG, true);
    return options;
  }

  private static void addOption(Options options, String description, String longOpt, boolean hasArg) {
    OptionBuilder.hasArg(hasArg);
    OptionBuilder.withDescription(description);
    OptionBuilder.withLongOpt(longOpt);
    options.addOption(OptionBuilder.create());
  }

  @Override
  public Boolean call() throws IOException {

    java.util.logging.Logger.getLogger("").addHandler(new MemoryHandler());

    Tomcat tomcat = new Tomcat();
    Connector connector = makeConnector();
    configureTomcat(tomcat, connector);
    configureEngine(tomcat.getEngine());
    configureServer(tomcat.getServer());
    configureHost(tomcat.getHost());
    Context context = makeContext(tomcat, noSuchBaseDir);

    addServlet(context, new PreferenceServlet(), "/pref/*");
    addServlet(context, new IngestServlet(), "/ingest/*");
    addServlet(context, new RecommendServlet(), "/recommend/*");
    addServlet(context, new RecommendToManyServlet(), "/recommendToMany/*");
    addServlet(context, new RecommendToAnonymousServlet(), "/recommendToAnonymous/*");
    addServlet(context, new SimilarityServlet(), "/similarity/*");
    addServlet(context, new EstimateServlet(), "/estimate/*");
    addServlet(context, new BecauseServlet(), "/because/*");
    addServlet(context, new RefreshServlet(), "/refresh/*");
    addServlet(context, new ReadyServlet(), "/ready/*");
    addServlet(context, new AllUserIDsServlet(), "/user/allIDs/*");
    addServlet(context, new AllItemIDsServlet(), "/item/allIDs/*");
    addServlet(context, new index_jspx(), "/index.jspx");
    addServlet(context, new status_jspx(), "/status.jspx");
    addServlet(context, new error_jspx(), "/error.jspx");
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
  public void close() {
    try {
      tomcat.stop();
      tomcat.destroy();
    } catch (LifecycleException le) {
      log.warn("Unexpected error while stopping", le);
    }
    noSuchBaseDir.delete();
  }

  private static void printHelp(Options options) {
    System.out.println("Myrrix Serving Layer. Copyright 2012 Myrrix Ltd, except for included ");
    System.out.println("third-party open source software. Full details of licensing at http://myrrix.com/legal/");
    System.out.println();
    new HelpFormatter().printHelp(Runner.class.getSimpleName() + " [flags]", options);
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

  private Connector makeConnector() {
    Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
    if (config.getKeystoreFile() == null) {
      // HTTP connector
      connector.setPort(config.getPort());
      connector.setSecure(false);
      connector.setScheme("http");

    } else {
      // HTTPS connector
      connector.setPort(config.getSecurePort());
      connector.setSecure(true);
      connector.setScheme("https");
      connector.setAttribute("SSLEnabled", "true");
      connector.setAttribute("sslProtocol", "TLS");
      connector.setAttribute("clientAuth", "false");
      connector.setAttribute("keystoreFile", config.getKeystoreFile());
      connector.setAttribute("keystorePass", config.getKeystorePassword());

    }

    // Keep quiet about the server type
    connector.setXpoweredBy(false);
    connector.setAttribute("server", "Myrrix");

    // Basic tuning params:
    connector.setAttribute("maxThreads", 400);
    connector.setAttribute("acceptCount", 50);
    connector.setAttribute("connectionTimeout", 2000);
    connector.setAttribute("maxKeepAliveRequests", 100);

    // Avoid running out of ephemeral ports under heavy load?
    connector.setAttribute("socket.soReuseAddress", true);

    return connector;
  }

  private Context makeContext(Tomcat tomcat, File noSuchBaseDir) throws IOException {

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
    servletContext.setAttribute(InitListener.ALL_PARTITIONS_SPEC_KEY, config.getAllPartitionsSpecification());
    servletContext.setAttribute(InitListener.PARTITION_KEY, config.getPartition());

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

        context.addSecurityRole(InMemoryRealm.AUTH_ROLE);
        DigestAuthenticator authenticator = new DigestAuthenticator();
        authenticator.setNonceValidity(10 * 1000L); // Shorten from 5 minutes to 10 seconds
        authenticator.setNonceCacheSize(20000); // Increase from 1000 to 20000
        context.getPipeline().addValve(authenticator);
      }

      context.addConstraint(securityConstraint);
    }

    return context;
  }

  private static void addServlet(Context context, Servlet servlet, String path) {
    String name = servlet.getClass().getSimpleName();
    Tomcat.addServlet(context, name, servlet);
    context.addServletMapping(path, name);
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

}
