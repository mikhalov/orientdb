/*
 *
 *  *  Copyright 2010-2016 OrientDB LTD (http://orientdb.com)
 *  *
 *  *  Licensed under the Apache License, Version 2.0 (the "License");
 *  *  you may not use this file except in compliance with the License.
 *  *  You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *  Unless required by applicable law or agreed to in writing, software
 *  *  distributed under the License is distributed on an "AS IS" BASIS,
 *  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  See the License for the specific language governing permissions and
 *  *  limitations under the License.
 *  *
 *  * For more information: http://orientdb.com
 *
 */
package com.orientechnologies.orient.core.sql.query;

import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.common.log.OLogger;
import com.orientechnologies.common.util.OUncaughtExceptionHandler;
import com.orientechnologies.orient.core.command.OCommandRequestAsynch;
import com.orientechnologies.orient.core.command.OCommandResultListener;
import com.orientechnologies.orient.core.db.ODatabaseDocumentInternal;
import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal;
import com.orientechnologies.orient.core.db.document.ODatabaseDocument;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * SQL asynchronous query. When executed the caller does not wait for the execution, rather the
 * listener will be called for each item found in the query. OSQLAsynchQuery has been built on top
 * of this. NOTE: if you're working with remote databases don't execute any remote call inside the
 * callback function because the network channel is locked until the query command has finished.
 *
 * @author Luca Garulli (l.garulli--(at)--orientdb.com)
 * @see com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
 */
public class OSQLNonBlockingQuery<T extends Object> extends OSQLQuery<T>
    implements OCommandRequestAsynch {
  private static final OLogger logger = OLogManager.instance().logger(OSQLNonBlockingQuery.class);
  private static final long serialVersionUID = 1L;

  public class ONonBlockingQueryFuture implements Future, List<Future> {

    protected volatile boolean finished = false;

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return false; // TODO
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public boolean isDone() {
      return finished;
    }

    @Override
    public synchronized Object get() throws InterruptedException, ExecutionException {
      while (!finished) {
        wait();
      }
      return OSQLNonBlockingQuery.this.getResultListener().getResult();
    }

    @Override
    public synchronized Object get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      while (!finished) {
        wait();
      }
      return OSQLNonBlockingQuery.this.getResultListener().getResult();
    }

    @Override
    public int size() {
      return 1;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public boolean contains(Object o) {
      return o == this;
    }

    @Override
    public Iterator<Future> iterator() {
      return new Iterator<Future>() {

        @Override
        public boolean hasNext() {
          return false;
        }

        @Override
        public Future next() {
          return null;
        }

        @Override
        public void remove() {
          throw new UnsupportedOperationException("Unsuppored remove on non blocking query result");
        }
      };
    }

    @Override
    public Object[] toArray() {
      return new Object[0];
    }

    @Override
    public <T> T[] toArray(T[] a) {
      return null;
    }

    @Override
    public boolean add(Future future) {
      return false;
    }

    @Override
    public boolean remove(Object o) {
      return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
      return false;
    }

    @Override
    public boolean addAll(Collection<? extends Future> c) {
      return false;
    }

    @Override
    public boolean addAll(int index, Collection<? extends Future> c) {
      return false;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
      return false;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
      return false;
    }

    @Override
    public void clear() {}

    @Override
    public Future get(int index) {
      if (index == 0) {
        return this;
      }
      return null;
    }

    public boolean isIdempotent() {
      return false;
    }

    @Override
    public Future set(int index, Future element) {
      return null;
    }

    @Override
    public void add(int index, Future element) {}

    @Override
    public Future remove(int index) {
      return get(index);
    }

    @Override
    public int indexOf(Object o) {
      return 0;
    }

    @Override
    public int lastIndexOf(Object o) {
      return 0;
    }

    @Override
    public ListIterator<Future> listIterator() {
      return null;
    }

    @Override
    public ListIterator<Future> listIterator(int index) {
      return null;
    }

    @Override
    public List<Future> subList(int fromIndex, int toIndex) {
      return null;
    }
  }

  /** Empty constructor for unmarshalling. */
  public OSQLNonBlockingQuery() {}

  public OSQLNonBlockingQuery(final String iText, final OCommandResultListener iResultListener) {
    this(iText, -1, iResultListener);
  }

  public OSQLNonBlockingQuery(
      final String iText,
      final int iLimit,
      final String iFetchPlan,
      final Map<Object, Object> iArgs,
      final OCommandResultListener iResultListener) {
    this(iText, iLimit, iResultListener);
    this.fetchPlan = iFetchPlan;
    this.parameters = iArgs;
  }

  public OSQLNonBlockingQuery(
      final String iText, final int iLimit, final OCommandResultListener iResultListener) {
    super(iText);
    limit = iLimit;
    resultListener = iResultListener;
  }

  @SuppressWarnings("unchecked")
  public <RET> RET execute2(final String iText, final Object... iArgs) {
    text = iText;
    return (RET) execute(iArgs);
  }

  public T executeFirst() {
    execute(1);
    return null;
  }

  @Override
  public <RET> RET execute(final Object... iArgs) {
    final ODatabaseDocumentInternal database = ODatabaseRecordThreadLocal.instance().get();

    final ONonBlockingQueryFuture future = new ONonBlockingQueryFuture();

    if (database instanceof ODatabaseDocument) {
      ODatabaseDocumentInternal currentThreadLocal =
          ODatabaseRecordThreadLocal.instance().getIfDefined();
      final ODatabaseDocumentInternal db = database.copy();
      if (currentThreadLocal != null) {
        currentThreadLocal.activateOnCurrentThread();
      } else {
        ODatabaseRecordThreadLocal.instance().set(null);
      }

      Thread t =
          new Thread(
              () -> {
                db.activateOnCurrentThread();
                try {
                  OSQLAsynchQuery<T> query =
                      new OSQLAsynchQuery<T>(
                          OSQLNonBlockingQuery.this.getText(),
                          OSQLNonBlockingQuery.this.getResultListener());
                  query.setFetchPlan(OSQLNonBlockingQuery.this.getFetchPlan());
                  query.setLimit(OSQLNonBlockingQuery.this.getLimit());
                  query.execute(iArgs);
                } catch (RuntimeException e) {
                  if (getResultListener() != null) {
                    getResultListener().end();
                  }
                  throw e;
                } finally {
                  if (db != null) {
                    try {
                      db.close();
                    } catch (Exception e) {
                      logger.error("Error during database close", e);
                    }
                  }
                  try {
                    synchronized (future) {
                      future.finished = true;
                      future.notifyAll();
                    }
                  } catch (Exception e) {
                    logger.error("", e);
                  }
                }
              });

      t.setUncaughtExceptionHandler(new OUncaughtExceptionHandler());
      t.start();

      return (RET) future;
    } else {
      throw new RuntimeException("cannot run non blocking query with non tx db"); // TODO
    }
  }

  @Override
  public boolean isIdempotent() {
    return false;
  }

  @Override
  public boolean isAsynchronous() {
    return true;
  }
}
