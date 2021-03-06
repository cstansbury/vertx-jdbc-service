/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.ext.jdbc;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.impl.actions.AbstractJdbcAction;
import io.vertx.test.core.VertxTestBase;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public abstract class JdbcServiceTestBase extends VertxTestBase {
  protected JdbcService service;

  private static final List<String> SQL = new ArrayList<>();

  static {
    //TODO: Create table with more types for testing
    SQL.add("drop table if exists select_table;");
    SQL.add("drop table if exists insert_table;");
    SQL.add("drop table if exists update_table;");
    SQL.add("drop table if exists delete_table;");
    SQL.add("create table select_table (id int, lname varchar(255), fname varchar(255) );");
    SQL.add("insert into select_table values (1, 'doe', 'john');");
    SQL.add("insert into select_table values (2, 'doe', 'jane');");
    SQL.add("create table insert_table (id int generated by default as identity (start with 1 increment by 1) not null, lname varchar(255), fname varchar(255), dob date );");
    SQL.add("create table update_table (id int, lname varchar(255), fname varchar(255), dob date );");
    SQL.add("insert into update_table values (1, 'doe', 'john', '2001-01-01');");
    SQL.add("create table delete_table (id int, lname varchar(255), fname varchar(255), dob date );");
    SQL.add("insert into delete_table values (1, 'doe', 'john', '2001-01-01');");
    SQL.add("insert into delete_table values (2, 'doe', 'jane', '2002-02-02');");
  }

  @BeforeClass
  public static void createDb() throws Exception {
    Connection conn = DriverManager.getConnection(config().getString("url"));
    for (String sql : SQL) {
      conn.createStatement().execute(sql);
    }
  }

  protected static JsonObject config() {
    return new JsonObject()
      .put("driver", "org.hsqldb.jdbcDriver")
      .put("url", "jdbc:hsqldb:mem:test?shutdown=true")
      .put("txTimeout", 1000);
  }

  @Test
  public void testSelect() {
    String sql = "SELECT * FROM select_table";
    connection().query(sql, null, onSuccess(results -> {
      assertNotNull(results);
      assertEquals(2, results.size());
      JsonObject result = results.get(0);
      assertEquals(1, (int) result.getInteger("ID"));
      assertEquals("doe", result.getString("LNAME"));
      assertEquals("john", result.getString("FNAME"));
      testComplete();
    }));

    await();
  }

  @Test
  public void testSelectWithParameters() {
    String sql = "SELECT * FROM select_table WHERE fname = ?";
    connection().query(sql, new JsonArray().add("john"), onSuccess(results -> {
      assertNotNull(results);
      assertEquals(1, results.size());
      JsonObject result = results.get(0);
      assertEquals(1, (int) result.getInteger("ID"));
      assertEquals("doe", result.getString("LNAME"));
      assertEquals("john", result.getString("FNAME"));
      testComplete();
    }));

    await();
  }

  @Test
  public void testSelectTx() {
    String sql = "INSERT INTO insert_table VALUES (?, ?, ?, ?);";
    JsonArray params = new JsonArray().addNull().add("smith").add("john").add("2003-03-03");
    service.getConnection(onSuccess(conn -> {
      assertNotNull(conn);
      conn.setAutoCommit(false, onSuccess(v -> {
        conn.update(sql, params, onSuccess(result -> {
          assertUpdate(result, 1);
          int id = result.getJsonArray("keys").getInteger(0);
          conn.query("SELECT * FROM insert_table WHERE id = ?", new JsonArray().add(id), onSuccess(results -> {
            assertFalse(results.isEmpty());
            assertEquals("smith", results.get(0).getString("LNAME"));
            testComplete();
          }));
        }));
      }));
    }));

    await();
  }

  @Test
  public void testInvalidSelect() {
    // Suppress log output so this test doesn't look to fail
    setLogLevel(AbstractJdbcAction.class.getName(), Level.SEVERE);
    String sql = "SELECT FROM WHERE FOO BAR";
    connection().query(sql, null, onFailure(t -> {
      assertNotNull(t);
      testComplete();
    }));

    await();
  }

  @Test
  public void testInsert() {
    String sql = "INSERT INTO insert_table VALUES (null, 'doe', 'john', '2001-01-01');";
    connection().update(sql, null, onSuccess(result -> {
      assertUpdate(result, 1);
      testComplete();
    }));

    await();
  }

  @Test
  public void testInsertWithParameters() {
    JdbcConnection conn = connection();
    String sql = "INSERT INTO insert_table VALUES (?, ?, ?, ?);";
    JsonArray params = new JsonArray().addNull().add("doe").add("jane").add("2002-02-02");
    conn.update(sql, params, onSuccess(result -> {
      assertUpdate(result, 1);
      int id = result.getJsonArray("keys").getInteger(0);
      conn.query("SElECT * FROM insert_table WHERE id=?;", new JsonArray().add(id), onSuccess(results -> {
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("2002-02-02", results.get(0).getString("DOB"));
        testComplete();
      }));
    }));

    await();
  }

  @Test
  public void testUpdate() {
    JdbcConnection conn = connection();
    String sql = "UPDATE update_table SET fname='jane' WHERE id = 1";
    conn.update(sql, null, onSuccess(updated -> {
      assertUpdate(updated, 1);
      conn.query("SELECT fname FROM update_table WHERE id = 1", null, onSuccess(results -> {
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("jane", results.get(0).getString("FNAME"));
        testComplete();
      }));
    }));

    await();
  }

  @Test
  public void testUpdateWithParams() {
    JdbcConnection conn = connection();
    String sql = "UPDATE update_table SET fname = ? WHERE id = ?";
    JsonArray params = new JsonArray().add("bob").add(1);
    conn.update(sql, params, onSuccess(result -> {
      assertUpdate(result, 1);
      conn.query("SELECT fname FROM update_table WHERE id = 1", null, onSuccess(results -> {
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("bob", results.get(0).getString("FNAME"));
        testComplete();
      }));
    }));

    await();
  }

  @Test
  public void testUpdateNoMatch() {
    JdbcConnection conn = connection();
    String sql = "UPDATE update_table SET fname='jane' WHERE id = -231";
    conn.update(sql, null, onSuccess(result -> {
      assertUpdate(result, 0);
      testComplete();
    }));

    await();
  }

  @Test
  public void testDelete() {
    String sql = "DELETE FROM delete_table WHERE id = 1;";
    connection().update(sql, null, onSuccess(result -> {
      assertNotNull(result);
      assertEquals(1, (int) result.getInteger("updated"));
      testComplete();
    }));

    await();
  }

  @Test
  public void testDeleteWithParams() {
    String sql = "DELETE FROM delete_table WHERE id = ?;";
    JsonArray params = new JsonArray().add(2);
    connection().update(sql, params, onSuccess(result -> {
      assertNotNull(result);
      assertEquals(1, (int) result.getInteger("updated"));
      testComplete();
    }));

    await();
  }

  @Test
  public void testClose() throws Exception {
    service.getConnection(onSuccess(conn -> {
      conn.query("SELECT 1 FROM select_table", null, onSuccess(results-> {
        assertNotNull(results);
        conn.close(onSuccess(v -> {
          testComplete();
        }));
      }));
    }));

    await();
  }

  @Test
  public void testCloseThenQuery() throws Exception {
    service.getConnection(onSuccess(conn -> {
      conn.close(onSuccess(v -> {
        conn.query("SELECT 1 FROM select_table", null, onFailure(t-> {
          assertNotNull(t);
          testComplete();
        }));
      }));
    }));

    await();
  }

  @Test
  public void testCommit() throws Exception {
    testTx(3, true);
  }

  @Test
  public void testRollback() throws Exception {
    testTx(5, false);
  }

  private void testTx(int inserts, boolean commit) throws Exception {
    String sql = "INSERT INTO insert_table VALUES (?, ?, ?, ?);";
    JsonArray params = new JsonArray().addNull().add("smith").add("john").add("2003-03-03");
    List<Integer> insertIds = new CopyOnWriteArrayList<>();

    CountDownLatch latch = new CountDownLatch(inserts);
    AtomicReference<JdbcConnection> connRef = new AtomicReference<>();
    service.getConnection(onSuccess(conn -> {
      assertNotNull(conn);
      connRef.set(conn);
      conn.setAutoCommit(false, onSuccess(v -> {
        for (int i = 0; i < inserts; i++) {
          conn.update(sql, params, onSuccess(result -> {
            assertUpdate(result, 1);
            int id = result.getJsonArray("keys").getInteger(0);
            insertIds.add(id);
            latch.countDown();
          }));
        }
      }));
    }));

    awaitLatch(latch);

    StringBuilder selectSql = new StringBuilder("SELECT * FROM insert_table WHERE");
    JsonArray selectParams = new JsonArray();
    for (int i = 0; i < insertIds.size(); i++) {
      selectParams.add(insertIds.get(i));
      if (i == 0) {
        selectSql.append(" id = ?");
      } else {
        selectSql.append(" OR id = ?");
      }
    }

    JdbcConnection conn = connRef.get();
    if (commit) {
      conn.commit(onSuccess(v -> {
        service.getConnection(onSuccess(newconn -> {
          newconn.query(selectSql.toString(), selectParams, onSuccess(results -> {
            assertFalse(results.isEmpty());
            assertEquals(inserts, results.size());
            testComplete();
          }));
        }));
      }));
    } else {
      conn.rollback(onSuccess(v -> {
        service.getConnection(onSuccess(newconn -> {
          newconn.query(selectSql.toString(), selectParams, onSuccess(results -> {
            assertTrue(results.isEmpty());
            testComplete();
          }));
        }));
      }));
    }

    await();
  }

  private void assertUpdate(JsonObject result, int updated) {
    assertUpdate(result, updated, false);
  }

  private void assertUpdate(JsonObject result, int updated, boolean generatedKeys) {
    assertNotNull(result);
    Integer u = result.getInteger("updated");
    assertNotNull(u);
    assertEquals(updated, (int) u);
    if (generatedKeys) {
      JsonArray keys = result.getJsonArray("keys");
      assertNotNull(keys);
      assertEquals(updated, keys.size());
      Set<Integer> numbers = new HashSet<>();
      for (int i = 0; i < updated; i++) {
        assertTrue(keys.getValue(i) instanceof Integer);
        assertTrue(numbers.add(i));
      }
    }
  }

  private JdbcConnection connection() {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<JdbcConnection> ref = new AtomicReference<>();
    service.getConnection(onSuccess(conn -> {
      ref.set(conn);
      latch.countDown();
    }));

    try {
      latch.await();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    return ref.get();
  }

  private static void setLogLevel(String name, Level level) {
    Logger logger = Logger.getLogger(name);
    if (logger != null) {
      logger.setLevel(level);
    }
  }
}
