package com.orientechnologies.orient.core.sql.executor;

import com.orientechnologies.common.concur.OTimeoutException;
import com.orientechnologies.common.exception.OException;
import com.orientechnologies.orient.core.command.OCommandContext;
import com.orientechnologies.orient.core.db.OExecutionThreadLocal;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.exception.OCommandExecutionException;
import com.orientechnologies.orient.core.exception.OCommandInterruptedException;
import com.orientechnologies.orient.core.sql.executor.resultset.OIteratorResultSet;
import com.orientechnologies.orient.core.sql.executor.resultset.OResultSetMapper;
import com.orientechnologies.orient.core.sql.parser.OBinaryCondition;
import com.orientechnologies.orient.core.sql.parser.OFromClause;
import java.util.Iterator;

/** Created by luigidellaquila on 06/08/16. */
public class FetchFromIndexedFunctionStep extends AbstractExecutionStep {
  private OBinaryCondition functionCondition;
  private OFromClause queryTarget;

  private long cost = 0;
  // runtime
  private Iterator<OIdentifiable> fullResult = null;

  public FetchFromIndexedFunctionStep(
      OBinaryCondition functionCondition,
      OFromClause queryTarget,
      OCommandContext ctx,
      boolean profilingEnabled) {
    super(ctx, profilingEnabled);
    this.functionCondition = functionCondition;
    this.queryTarget = queryTarget;
  }

  @Override
  public OResultSet syncPull(OCommandContext ctx) throws OTimeoutException {
    getPrev().ifPresent(x -> x.syncPull(ctx));
    init(ctx);
    return new OResultSetMapper(
        new OIteratorResultSet(fullResult),
        (result) -> {
          if (OExecutionThreadLocal.isInterruptCurrentOperation()) {
            throw new OCommandInterruptedException("The command has been interrupted");
          }
          long begin = profilingEnabled ? System.nanoTime() : 0;
          try {
            ctx.setVariable("$current", result);
            return result;
          } finally {
            if (profilingEnabled) {
              cost += (System.nanoTime() - begin);
            }
          }
        });
  }

  private void init(OCommandContext ctx) {
    if (fullResult == null) {
      long begin = profilingEnabled ? System.nanoTime() : 0;
      try {
        fullResult = functionCondition.executeIndexedFunction(queryTarget, ctx).iterator();
      } finally {
        if (profilingEnabled) {
          cost += (System.nanoTime() - begin);
        }
      }
    }
  }

  @Override
  public String prettyPrint(int depth, int indent) {
    String result =
        OExecutionStepInternal.getIndent(depth, indent)
            + "+ FETCH FROM INDEXED FUNCTION "
            + functionCondition.toString();
    if (profilingEnabled) {
      result += " (" + getCostFormatted() + ")";
    }
    return result;
  }

  @Override
  public void reset() {
    this.fullResult = null;
  }

  @Override
  public long getCost() {
    return cost;
  }

  @Override
  public OResult serialize() {
    OResultInternal result = OExecutionStepInternal.basicSerialize(this);
    result.setProperty("functionCondition", this.functionCondition.serialize());
    result.setProperty("queryTarget", this.queryTarget.serialize());

    return result;
  }

  @Override
  public void deserialize(OResult fromResult) {
    try {
      OExecutionStepInternal.basicDeserialize(fromResult, this);
      functionCondition = new OBinaryCondition(-1);
      functionCondition.deserialize(fromResult.getProperty("functionCondition "));

      queryTarget = new OFromClause(-1);
      queryTarget.deserialize(fromResult.getProperty("functionCondition "));

    } catch (Exception e) {
      throw OException.wrapException(new OCommandExecutionException(""), e);
    }
  }
}
