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

package net.myrrix.client;

import java.io.File;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.common.io.PatternFilenameFilter;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.MyrrixTest;
import net.myrrix.web.Runner;
import net.myrrix.web.RunnerConfiguration;

public abstract class AbstractClientTest extends MyrrixTest {

  private static final Logger log = LoggerFactory.getLogger(AbstractClientTest.class);

  private static File savedModelFile = null;

  private Runner runner;
  private ClientRecommender client;

  protected abstract String getTestDataPath();

  protected boolean useSecurity() {
    return false;
  }

  protected boolean callAwait() {
    return true;
  }

  protected final Runner getRunner() {
    return runner;
  }

  protected final ClientRecommender getClient() {
    return client;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    File tempDir = getTestTempDir();
    File testDataDir = new File(getTestDataPath());
    Preconditions.checkState(testDataDir.exists() && testDataDir.isDirectory(),
                             "%s is not an existing directory", testDataDir.getAbsolutePath());

    log.info("Copying files to {}", tempDir);

    boolean runRefresh;
    if (savedModelFile == null) {
      log.info("No saved model file, building model");
      File[] srcDataFiles = testDataDir.listFiles(new PatternFilenameFilter("[^.].*"));
      if (srcDataFiles != null) {
        for (File srcDataFile : srcDataFiles) {
          File destFile = new File(tempDir, srcDataFile.getName());
          Files.copy(srcDataFile, destFile);
        }
      }
      runRefresh = true;
    } else {
      log.info("Found saved model file {} (size {})", savedModelFile, savedModelFile.length());
      Files.copy(savedModelFile, new File(tempDir, "model.bin"));
      runRefresh = false;
    }

    log.info("Configuring recommender...");

    RunnerConfiguration runnerConfig = new RunnerConfiguration();
    runnerConfig.setInstanceID("test");
    runnerConfig.setPort(8090); // Odd ports to avoid conflicts
    if (useSecurity()) {
      runnerConfig.setSecurePort(8453); // Odd ports to avoid conflicts
      runnerConfig.setKeystorePassword("changeit");
      runnerConfig.setKeystoreFile(new File("testdata/keystore"));
      runnerConfig.setUserName("foo");
      runnerConfig.setPassword("bar");
    }
    runnerConfig.setLocalInputDir(tempDir);

    runner = new Runner(runnerConfig);
    runner.call();

    boolean clientSecure = runnerConfig.getKeystoreFile() != null;
    int clientPort = clientSecure ? runnerConfig.getSecurePort() : runnerConfig.getPort();
    MyrrixClientConfiguration clientConfig = new MyrrixClientConfiguration();
    clientConfig.setHost("localhost");
    clientConfig.setPort(clientPort);
    clientConfig.setSecure(clientSecure);
    clientConfig.setKeystorePassword(runnerConfig.getKeystorePassword());
    clientConfig.setKeystoreFile(runnerConfig.getKeystoreFile());
    clientConfig.setUserName(runnerConfig.getUserName());
    clientConfig.setPassword(runnerConfig.getPassword());
    client = new ClientRecommender(clientConfig);

    if (runRefresh) {
      client.refresh(null);
    }

    if (callAwait()) {
      log.info("Waiting for client...");
      client.await();
    }
  }

  @Override
  @After
  public void tearDown() throws Exception {
    if (runner != null) {
      runner.close();
      runner = null;
    }
    client = null;
    synchronized (AbstractClientTest.class) {
      if (savedModelFile == null) {
        File modelBinFile = new File(getTestTempDir(), "model.bin");
        if (modelBinFile.exists()) {
          savedModelFile = File.createTempFile("model-", ".bin");
          savedModelFile.deleteOnExit();
          Files.copy(modelBinFile, savedModelFile);
        }
      }
    }
    super.tearDown();
  }

  @AfterClass
  public static void tearDownClass() {
    if (savedModelFile != null) {
      savedModelFile.delete();
      savedModelFile = null;
    }
  }

}
