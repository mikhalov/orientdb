package com.tinkerpop.blueprints.impls.orient;

import static org.junit.Assert.assertEquals;

import com.orientechnologies.orient.core.Orient;
import com.orientechnologies.orient.core.config.OGlobalConfiguration;
import com.orientechnologies.orient.core.db.OrientDB;
import com.orientechnologies.orient.core.db.OrientDBConfig;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.server.OServer;
import com.tinkerpop.blueprints.Vertex;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class OrientGraphMultithreadRemoteTest {
  private static final String serverPort = System.getProperty("orient.server.port", "3080");
  private static OServer server;
  private static String oldOrientDBHome;

  private static String serverHome;

  private OrientGraphFactory graphFactory;

  @BeforeClass
  public static void startEmbeddedServer() throws Exception {
    final String buildDirectory = System.getProperty("buildDirectory", ".");
    serverHome = buildDirectory + "/" + OrientGraphMultithreadRemoteTest.class.getSimpleName();

    File file = new File(serverHome);
    deleteDirectory(file);

    file = new File(serverHome);
    Assert.assertTrue(file.mkdir());

    oldOrientDBHome = System.getProperty("ORIENTDB_HOME");
    System.setProperty("ORIENTDB_HOME", serverHome);

    server = new OServer(false);
    server.startup(
        OrientGraphMultithreadRemoteTest.class.getResourceAsStream("/embedded-server-config.xml"));
    server.activate();
  }

  @AfterClass
  public static void stopEmbeddedServer() throws Exception {
    server.shutdown();
    Thread.sleep(1000);
    ODatabaseDocumentTx.closeAll();

    Orient.instance().shutdown();
    Orient.instance().startup();

    if (oldOrientDBHome != null) System.setProperty("ORIENTDB_HOME", oldOrientDBHome);
    else System.clearProperty("ORIENTDB_HOME");

    final File file = new File(serverHome);
    deleteDirectory(file);

    Orient.instance().startup();
  }

  @Before
  public void before() {
    OGlobalConfiguration.NETWORK_LOCK_TIMEOUT.setValue(15000);
    final String url =
        "remote:localhost:"
            + serverPort
            + "/"
            + OrientGraphMultithreadRemoteTest.class.getSimpleName();

    OrientDB orientdb =
        new OrientDB(
            "remote:localhost:" + serverPort, "root", "root", OrientDBConfig.defaultConfig());
    if (!orientdb.exists(OrientGraphMultithreadRemoteTest.class.getSimpleName())) {
      orientdb.execute(
          "create database "
              + OrientGraphMultithreadRemoteTest.class.getSimpleName()
              + " memory users(admin identified by 'adminpwd' role admin,reader identified by"
              + " 'readerpwd' role reader, writer identified by 'writerpwd' role writer)");
    }
    orientdb.close();

    graphFactory = new OrientGraphFactory(url, "admin", "adminpwd");
    graphFactory.setupPool(5, 256);
  }

  @Test
  public void testThreadingInsert() throws InterruptedException {
    List<Thread> threads = new ArrayList<Thread>();
    int threadCount = 8;
    final int recordsPerThread = 20;
    long records = threadCount * recordsPerThread;
    try {
      for (int t = 0; t < threadCount; t++) {
        Thread thread =
            new Thread() {
              @Override
              public void run() {
                for (int i = 0; i < recordsPerThread; i++) {
                  OrientGraph graph = graphFactory.getTx(); // get an instance from the pool
                  try {

                    Vertex v1 = graph.addVertex(null);
                    v1.setProperty("name", "b");
                    // v1.setProperty("blob", blob);

                    graph.commit(); // commits transaction
                  } catch (Exception ex) {
                    try {
                      graph.rollback();
                    } catch (Exception ex1) {
                      System.out.println("rollback exception! " + ex);
                    }

                    System.out.println("operation exception! " + ex);
                    ex.printStackTrace(System.out);
                  } finally {
                    graph.shutdown();
                  }
                }
              }
            };
        threads.add(thread);
        thread.start();
      }
    } catch (Exception ex) {
      System.err.println("instance exception! " + ex);
      System.out.println("instance exception! " + ex);
      ex.printStackTrace(System.err);
    } finally {
      for (Thread t : threads) {
        t.join();
      }
    }
    OrientGraph graph = graphFactory.getTx();
    assertEquals(graph.countVertices(), records);
    graph.shutdown();
  }

  @After
  public void after() {
    graphFactory.close();
  }

  protected static void deleteDirectory(final File directory) {
    if (directory.exists()) {
      for (File file : directory.listFiles()) {
        if (file.isDirectory()) {
          deleteDirectory(file);
        } else {
          file.delete();
        }
      }
      directory.delete();
    }
  }
}
