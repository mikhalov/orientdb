/* Generated By:JJTree: Do not edit this line. OMatchPathItem.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.orientechnologies.orient.core.sql.parser;

import com.orientechnologies.orient.core.command.OCommandContext;
import com.orientechnologies.orient.core.db.record.OIdentifiable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class OMatchPathItem extends SimpleNode {
  protected OMethodCall  method;
  protected OMatchFilter filter;

  public OMatchPathItem(int id) {
    super(id);
  }

  public OMatchPathItem(OrientSql p, int id) {
    super(p, id);
  }

  /** Accept the visitor. **/
  public Object jjtAccept(OrientSqlVisitor visitor, Object data) {
    return visitor.visit(this, data);
  }

  public boolean isBidirectional() {
    if (filter.getWhileCondition() != null) {
      return false;
    }
    if (filter.getMaxDepth() != null) {
      return false;
    }
    return method.isBidirectional();
  }

  public String toString() {
    StringBuilder result = new StringBuilder();
    result.append(method.toString());
    if (filter != null) {
      result.append(filter.toString());
    }
    return result.toString();
  }

  protected Iterable<OIdentifiable> executeTraversal(OMatchStatement.MatchContext matchContext, OCommandContext iCommandContext,
      OIdentifiable startingPoint, int depth) {

    OWhereClause filter = null;
    OWhereClause whileCondition = null;
    Integer maxDepth = null;
    if (this.filter != null) {
      filter = this.filter.getFilter();
      whileCondition = this.filter.getWhileCondition();
      maxDepth = this.filter.getMaxDepth();
    }

    Set<OIdentifiable> result = new HashSet<OIdentifiable>();

    if (whileCondition == null && maxDepth == null) {// in this case starting point is not returned and only one level depth is
      // evaluated
      Iterable<OIdentifiable> queryResult = traversePatternEdge(matchContext, startingPoint, iCommandContext);

      if (this.filter == null || this.filter.getFilter() == null) {
        return queryResult;
      }

      for (OIdentifiable origin : queryResult) {
        if (filter == null || filter.matchesFilters(origin, iCommandContext)) {
          result.add(origin);
        }
      }
    } else {// in this case also zero level (starting point) is considered and traversal depth is given by the while condition
      iCommandContext.setVariable("$depth", depth);
      if (filter == null || filter.matchesFilters(startingPoint, iCommandContext)) {
        result.add(startingPoint);
      }
      if ((maxDepth == null || depth < maxDepth)
          && (whileCondition == null || whileCondition.matchesFilters(startingPoint, iCommandContext))) {

        Iterable<OIdentifiable> queryResult = traversePatternEdge(matchContext, startingPoint, iCommandContext);

        for (OIdentifiable origin : queryResult) {
          // TODO consider break strategies (eg. re-traverse nodes)
          Iterable<OIdentifiable> subResult = executeTraversal(matchContext, iCommandContext, origin, depth + 1);
          if (subResult instanceof Collection) {
            result.addAll((Collection<? extends OIdentifiable>) subResult);
          } else {
            for (OIdentifiable i : subResult) {
              result.add(i);
            }
          }
        }
      }
    }
    return result;
  }

  protected Iterable<OIdentifiable> traversePatternEdge(OMatchStatement.MatchContext matchContext, OIdentifiable startingPoint,
      OCommandContext iCommandContext) {
    Object qR = this.method.execute(startingPoint, iCommandContext);
    return (qR instanceof Iterable) ? (Iterable) qR : Collections.singleton(qR);
  }
}
/* JavaCC - OriginalChecksum=ffe8e0ffde583d7b21c9084eff6a8944 (do not edit this line) */
