package com.orientechnologies.orient.core.storage.index.hashindex.local.v3;

import com.orientechnologies.BasePlocalInternalDatabase;
import com.orientechnologies.common.serialization.types.OIntegerSerializer;
import com.orientechnologies.orient.core.db.ODatabaseDocumentInternal;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.serialization.serializer.binary.OBinarySerializerFactory;
import com.orientechnologies.orient.core.storage.impl.local.OAbstractPaginatedStorage;
import com.orientechnologies.orient.core.storage.impl.local.paginated.atomicoperations.OAtomicOperationsManager;
import com.orientechnologies.orient.core.storage.index.hashindex.local.OHashFunction;
import com.orientechnologies.orient.core.storage.index.hashindex.local.OHashTable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author Andrey Lomakin (a.lomakin-at-orientdb.com)
 * @since 13.03.13
 */
public class LocalHashTableV3IterationTestIT extends BasePlocalInternalDatabase {
  private static final int KEYS_COUNT = 500000;

  private OLocalHashTableV3<Integer, String> localHashTable;
  private OAtomicOperationsManager atomicOperationsManager;

  @Before
  public void beforeTest() throws Exception {
    super.beforeTest();

    OHashFunction<Integer> hashFunction = value -> Long.MAX_VALUE / 2 + value;

    localHashTable =
        new OLocalHashTableV3<>(
            "localHashTableIterationTest",
            ".imc",
            ".tsc",
            ".obf",
            ".nbh",
            (OAbstractPaginatedStorage) db.getStorage());

    atomicOperationsManager =
        ((OAbstractPaginatedStorage) ((ODatabaseDocumentInternal) db).getStorage())
            .getAtomicOperationsManager();
    atomicOperationsManager.executeInsideAtomicOperation(
        null,
        atomicOperation ->
            localHashTable.create(
                atomicOperation,
                OIntegerSerializer.INSTANCE,
                OBinarySerializerFactory.getInstance().<String>getObjectSerializer(OType.STRING),
                null,
                null,
                hashFunction,
                true));
  }

  @After
  public void afterTest() throws Exception {
    doClearTable();

    atomicOperationsManager.executeInsideAtomicOperation(
        null, atomicOperation -> localHashTable.delete(atomicOperation));
    super.afterTest();
  }

  private void doClearTable() throws java.io.IOException {
    final OHashTable.Entry<Integer, String> firstEntry = localHashTable.firstEntry();

    if (firstEntry != null) {
      OHashTable.Entry<Integer, String>[] entries = localHashTable.ceilingEntries(firstEntry.key);
      while (entries.length > 0) {
        for (final OHashTable.Entry<Integer, String> entry : entries) {
          atomicOperationsManager.executeInsideAtomicOperation(
              null, atomicOperation -> localHashTable.remove(atomicOperation, entry.key));
        }

        entries = localHashTable.higherEntries(entries[entries.length - 1].key);
      }
    }

    if (localHashTable.isNullKeyIsSupported()) {
      atomicOperationsManager.executeInsideAtomicOperation(
          null, atomicOperation -> localHashTable.remove(atomicOperation, null));
    }
  }

  @After
  public void afterMethod() throws Exception {
    doClearTable();
  }

  @Test
  public void testNextHaveRightOrder() throws Exception {
    SortedSet<Integer> keys = new TreeSet<>();
    final Random random = new Random();

    while (keys.size() < KEYS_COUNT) {
      int key = random.nextInt();

      if (localHashTable.get(key) == null) {
        atomicOperationsManager.executeInsideAtomicOperation(
            null, atomicOperation -> localHashTable.put(atomicOperation, key, String.valueOf(key)));
        keys.add(key);
        Assert.assertEquals(localHashTable.get(key), String.valueOf(key));
      }
    }

    OHashTable.Entry<Integer, String>[] entries = localHashTable.ceilingEntries(Integer.MIN_VALUE);
    int curPos = 0;
    for (int key : keys) {
      int sKey = entries[curPos].key;

      Assert.assertEquals(key, sKey);
      curPos++;
      if (curPos >= entries.length) {
        entries = localHashTable.higherEntries(entries[entries.length - 1].key);
        curPos = 0;
      }
    }
  }

  public void testNextSkipsRecordValid() throws Exception {
    List<Integer> keys = new ArrayList<>();

    final Random random = new Random();
    while (keys.size() < KEYS_COUNT) {
      int key = random.nextInt();

      if (localHashTable.get(key) == null) {
        atomicOperationsManager.executeInsideAtomicOperation(
            null, atomicOperation -> localHashTable.put(atomicOperation, key, String.valueOf(key)));
        keys.add(key);
        Assert.assertEquals(localHashTable.get(key), String.valueOf(key));
      }
    }

    Collections.sort(keys);

    OHashTable.Entry<Integer, String>[] entries = localHashTable.ceilingEntries(keys.get(10));
    int curPos = 0;
    for (int key : keys) {
      if (key < keys.get(10)) {
        continue;
      }
      int sKey = entries[curPos].key;
      Assert.assertEquals(key, sKey);

      curPos++;
      if (curPos >= entries.length) {
        entries = localHashTable.higherEntries(entries[entries.length - 1].key);
        curPos = 0;
      }
    }
  }

  @Test
  @Ignore
  public void testNextHaveRightOrderUsingNextMethod() throws Exception {
    List<Integer> keys = new ArrayList<>();
    Random random = new Random();

    while (keys.size() < KEYS_COUNT) {
      int key = random.nextInt();

      if (localHashTable.get(key) == null) {
        atomicOperationsManager.executeInsideAtomicOperation(
            null, atomicOperation -> localHashTable.put(atomicOperation, key, String.valueOf(key)));
        keys.add(key);
        Assert.assertEquals(localHashTable.get(key), String.valueOf(key));
      }
    }

    Collections.sort(keys);

    for (int key : keys) {
      OHashTable.Entry<Integer, String>[] entries = localHashTable.ceilingEntries(key);
      Assert.assertEquals(key, (int) entries[0].key);
    }

    for (int j = 0, keysSize = keys.size() - 1; j < keysSize; j++) {
      int key = keys.get(j);
      int sKey = localHashTable.higherEntries(key)[0].key;
      Assert.assertEquals(sKey, (int) keys.get(j + 1));
    }
  }
}
