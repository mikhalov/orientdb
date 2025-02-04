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

package com.tinkerpop.blueprints.impls.orient;

import com.orientechnologies.common.concur.ONeedRetryException;
import com.orientechnologies.common.exception.OException;
import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.common.log.OLogger;
import com.orientechnologies.common.util.OPair;
import com.orientechnologies.orient.core.db.ODatabaseDocumentInternal;
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.record.impl.ODocumentInternal;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Features;
import com.tinkerpop.blueprints.util.ExceptionFactory;
import org.apache.commons.configuration.Configuration;

/**
 * A Blueprints implementation of the graph database OrientDB (http://orientdb.com)
 *
 * @author Luca Garulli (l.garulli--(at)--orientdb.com) (http://orientdb.com)
 */
public class OrientGraphNoTx extends OrientBaseGraph {
  private static final OLogger logger = OLogManager.instance().logger(OrientGraphNoTx.class);
  private final Features features = new Features();

  /**
   * Constructs a new object using an existent database instance.
   *
   * @param iDatabase Underlying database object to attach
   */
  public OrientGraphNoTx(final ODatabaseDocumentInternal iDatabase) {
    super(iDatabase, null, null, null);
    config();
  }

  public OrientGraphNoTx(OPartitionedDatabasePool pool) {
    super(pool);
    config();
  }

  public OrientGraphNoTx(OPartitionedDatabasePool pool, final Settings configuration) {
    super(pool, configuration);
    config();
  }

  public OrientGraphNoTx(final String url) {
    super(url, ADMIN, ADMIN);
    config();
  }

  public OrientGraphNoTx(final String url, final String username, final String password) {
    super(url, username, password);
    config();
  }

  public OrientGraphNoTx(final Configuration configuration) {
    super(configuration);
    config();
  }

  public OrientGraphNoTx(
      final ODatabaseDocumentInternal iDatabase, final String user, final String password) {
    super(iDatabase, user, password, null);
    config();
  }

  public OrientGraphNoTx(
      final ODatabaseDocumentInternal iDatabase,
      final String user,
      final String password,
      final Settings iConfiguration) {
    super(iDatabase, user, password, iConfiguration);
    config();
  }

  public Features getFeatures() {
    makeActive();

    // DYNAMIC features BASED ON CONFIGURATION
    features.supportsEdgeIndex = !isUseLightweightEdges();
    features.supportsEdgeKeyIndex = !isUseLightweightEdges();
    features.supportsEdgeIteration = !isUseLightweightEdges();
    features.supportsEdgeRetrieval = !isUseLightweightEdges();
    return features;
  }

  protected void config() {
    features.supportsDuplicateEdges = true;
    features.supportsSelfLoops = true;
    features.isPersistent = true;
    features.supportsVertexIteration = true;
    features.supportsVertexIndex = true;
    features.ignoresSuppliedIds = true;
    features.supportsTransactions = false;
    features.supportsVertexKeyIndex = true;
    features.supportsKeyIndices = true;
    features.isWrapper = false;
    features.supportsIndices = true;
    features.supportsVertexProperties = true;
    features.supportsEdgeProperties = true;

    // For more information on supported types, please see:
    // http://code.google.com/p/orient/wiki/Types
    features.supportsSerializableObjectProperty = true;
    features.supportsBooleanProperty = true;
    features.supportsDoubleProperty = true;
    features.supportsFloatProperty = true;
    features.supportsIntegerProperty = true;
    features.supportsPrimitiveArrayProperty = true;
    features.supportsUniformListProperty = true;
    features.supportsMixedListProperty = true;
    features.supportsLongProperty = true;
    features.supportsMapProperty = true;
    features.supportsStringProperty = true;
    features.supportsThreadedTransactions = false;
    features.supportsThreadIsolatedTransactions = false;
  }

  OrientEdge addEdgeInternal(
      final OrientVertex currentVertex,
      String label,
      final OrientVertex inVertex,
      final String iClassName,
      final String iClusterName,
      final Object... fields) {
    return OrientGraphNoTx.addEdgeInternal(
        this, currentVertex, label, inVertex, iClassName, iClusterName, fields);
  }

  static OrientEdge addEdgeInternal(
      final OrientBaseGraph graph,
      final OrientVertex currentVertex,
      String label,
      final OrientVertex inVertex,
      final String iClassName,
      final String iClusterName,
      final Object... fields) {

    OrientEdge edge = null;
    ODocument outDocument = null;
    ODocument inDocument = null;
    boolean outDocumentModified = false;

    final Settings settings = graph != null ? graph.settings : new Settings();

    final int maxRetries = graph != null ? graph.getMaxRetries() : 1;
    for (int retry = 0; retry < maxRetries; ++retry) {
      try {
        // TEMPORARY STATIC LOCK TO AVOID MT PROBLEMS AGAINST OMVRBTreeRID
        if (outDocument == null) {
          outDocument = currentVertex.getRecord();
          if (outDocument == null)
            throw new IllegalArgumentException(
                "source vertex is invalid (rid=" + currentVertex.getIdentity() + ")");
        }

        if (!ODocumentInternal.getImmutableSchemaClass(outDocument).isVertexType())
          throw new IllegalArgumentException("source record is not a vertex");

        if (inDocument == null) {
          inDocument = inVertex.getRecord();
          if (inDocument == null)
            throw new IllegalArgumentException(
                "destination vertex is invalid (rid=" + inVertex.getIdentity() + ")");
        }

        if (!ODocumentInternal.getImmutableSchemaClass(inDocument).isVertexType())
          throw new IllegalArgumentException("destination record is not a vertex");

        OIdentifiable to;
        OIdentifiable from;

        label = OrientBaseGraph.encodeClassName(label);
        if (label == null && iClassName != null)
          // RETRO-COMPATIBILITY WITH THE SYNTAX CLASS:<CLASS-NAME>
          label = OrientBaseGraph.encodeClassName(iClassName);

        if (graph != null && graph.isUseClassForEdgeLabel()) {
          final OrientEdgeType edgeType = graph.getEdgeType(label);
          if (edgeType == null)
            // AUTO CREATE CLASS
            graph.createEdgeType(label);
          else
            // OVERWRITE CLASS NAME BECAUSE ATTRIBUTES ARE CASE SENSITIVE
            label = edgeType.getName();
        }

        final String outFieldName =
            currentVertex.getConnectionFieldName(
                Direction.OUT, label, settings.isUseVertexFieldsForEdgeLabels());
        final String inFieldName =
            currentVertex.getConnectionFieldName(
                Direction.IN, label, settings.isUseVertexFieldsForEdgeLabels());

        // since the label for the edge can potentially get re-assigned
        // before being pushed into the OrientEdge, the
        // null check has to go here.
        if (label == null) throw ExceptionFactory.edgeLabelCanNotBeNull();

        final ODocument edgeRecord;
        final boolean lightWeightEdge;
        if (currentVertex.canCreateDynamicEdge(
            outDocument, inDocument, outFieldName, inFieldName, fields, label)) {
          // CREATE A LIGHTWEIGHT DYNAMIC EDGE
          lightWeightEdge = true;
          from = currentVertex.rawElement;
          to = inDocument;
          if (edge == null) {
            if (settings.isKeepInMemoryReferences())
              edge = graph.getEdgeInstance(from.getIdentity(), to.getIdentity(), label);
            else edge = graph.getEdgeInstance(from, to, label);
          }
          edgeRecord = null;
        } else {
          lightWeightEdge = false;
          if (edge == null) {
            // CREATE THE EDGE DOCUMENT TO STORE FIELDS TOO
            edge = graph.getEdgeInstance(label, fields);
            edgeRecord = edge.getRecord();

            if (settings.isKeepInMemoryReferences())
              edgeRecord.fields(
                  OrientBaseGraph.CONNECTION_OUT,
                  currentVertex.rawElement.getIdentity(),
                  OrientBaseGraph.CONNECTION_IN,
                  inDocument.getIdentity());
            else
              edgeRecord.fields(
                  OrientBaseGraph.CONNECTION_OUT,
                  currentVertex.rawElement,
                  OrientBaseGraph.CONNECTION_IN,
                  inDocument);
          } else edgeRecord = edge.getRecord();

          from = edgeRecord;
          to = edgeRecord;
        }

        if (settings.isKeepInMemoryReferences()) {
          // USES REFERENCES INSTEAD OF DOCUMENTS
          from = from.getIdentity();
          to = to.getIdentity();
        }

        if (graph != null && edgeRecord != null) edgeRecord.save(iClusterName);

        if (!outDocumentModified) {
          // OUT-VERTEX ---> IN-VERTEX/EDGE
          currentVertex.createLink(graph, outDocument, to, outFieldName);

          if (graph != null) {
            outDocument.save();
            outDocumentModified = true;
          }
        }

        // IN-VERTEX ---> OUT-VERTEX/EDGE
        currentVertex.createLink(graph, inDocument, from, inFieldName);

        if (graph != null) inDocument.save();

        // OK
        break;

      } catch (ONeedRetryException e) {
        // RETRY
        if (!outDocumentModified) outDocument.reload();
        else if (inDocument != null) inDocument.reload();
      } catch (RuntimeException e) {
        // REVERT CHANGES. EDGE.REMOVE() TAKES CARE TO UPDATE ALSO BOTH VERTICES IN CASE
        try {
          edge.remove();
        } catch (Exception ex) {
        }
        throw e;
      } catch (Exception e) {
        // REVERT CHANGES. EDGE.REMOVE() TAKES CARE TO UPDATE ALSO BOTH VERTICES IN CASE
        try {
          edge.remove();
        } catch (Exception ex) {
        }
        throw OException.wrapException(
            new OrientGraphModificationException("Error on addEdge in non tx environment"), e);
      }
    }
    return edge;
  }

  /** Removes the Edge from the Graph. Connected vertices aren't removed. */
  public void removeEdgeInternal(final OrientEdge edge) {
    removeEdgeInternal(this, edge);
  }

  public static void removeEdgeInternal(final OrientBaseGraph graph, final OrientEdge edge) {
    ODocument outVertexRecord = null;
    boolean outVertexChanged = false;
    ODocument inVertexRecord = null;
    boolean inVertexChanged = false;

    final Settings settings = graph != null ? graph.settings : new Settings();

    final int maxRetries = graph != null ? graph.getMaxRetries() : 1;
    for (int retry = 0; retry < maxRetries; ++retry) {
      try {
        // OUT VERTEX
        final OIdentifiable inVertexEdge = edge.vIn != null ? edge.vIn : edge.rawElement;

        final String edgeClassName = OrientBaseGraph.encodeClassName(edge.getLabel());

        final boolean useVertexFieldsForEdgeLabels = settings.isUseVertexFieldsForEdgeLabels();

        final OIdentifiable outVertex = edge.getOutVertex();

        if (outVertex != null) {
          outVertexRecord = outVertex.getRecord();
          if (outVertexRecord != null) {
            final String outFieldName =
                OrientVertex.getConnectionFieldName(
                    Direction.OUT, edgeClassName, useVertexFieldsForEdgeLabels);
            outVertexChanged =
                edge.dropEdgeFromVertex(
                    inVertexEdge,
                    outVertexRecord,
                    outFieldName,
                    outVertexRecord.field(outFieldName));
          } else
            logger.debug(
                "Found broken link to outgoing vertex "
                    + outVertex.getIdentity()
                    + " while removing edge "
                    + edge.getId());
        }

        // IN VERTEX
        final OIdentifiable outVertexEdge = edge.vOut != null ? edge.vOut : edge.rawElement;

        final OIdentifiable inVertex = edge.getInVertex();

        inVertexRecord = null;
        inVertexChanged = false;

        if (inVertex != null) {
          inVertexRecord = inVertex.getRecord();
          if (inVertexRecord != null) {
            final String inFieldName =
                OrientVertex.getConnectionFieldName(
                    Direction.IN, edgeClassName, useVertexFieldsForEdgeLabels);
            inVertexChanged =
                edge.dropEdgeFromVertex(
                    outVertexEdge, inVertexRecord, inFieldName, inVertexRecord.field(inFieldName));
          } else
            logger.debug(
                "Found broken link to incoming vertex "
                    + inVertex.getIdentity()
                    + " while removing edge "
                    + edge.getId());
        }

        if (outVertexChanged) outVertexRecord.save();
        if (inVertexChanged) inVertexRecord.save();

        if (edge.rawElement != null)
          // NON-LIGHTWEIGHT EDGE
          edge.removeRecord();

        // OK
        break;

      } catch (ONeedRetryException e) {
        // RETRY
        if (outVertexChanged) outVertexRecord.reload();
        else if (inVertexChanged) inVertexRecord.reload();

      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw OException.wrapException(
            new OrientGraphModificationException("Error on addEdge in non tx environment"), e);
      }
    }
  }

  @Override
  void removeEdgesInternal(
      final OrientVertex vertex,
      final ODocument iVertex,
      final OIdentifiable iVertexToRemove,
      final boolean iAlsoInverse,
      final boolean useVertexFieldsForEdgeLabels,
      final boolean autoScaleEdgeType) {

    Exception lastException = null;
    boolean forceReload = false;

    final int maxRetries = getMaxRetries();
    for (int retry = 0; retry < maxRetries; ++retry) {
      try {

        for (String fieldName : iVertex.fieldNames()) {
          final OPair<Direction, String> connection =
              vertex.getConnection(Direction.BOTH, fieldName);
          if (connection == null)
            // SKIP THIS FIELD
            continue;

          removeEdges(
              this,
              iVertex,
              fieldName,
              iVertexToRemove,
              iAlsoInverse,
              useVertexFieldsForEdgeLabels,
              autoScaleEdgeType,
              forceReload);
        }

        // OK
        return;

      } catch (Exception e) {
        forceReload = true;
        lastException = e;
      }
    }

    if (lastException instanceof RuntimeException)
      // CANNOT REVERT CHANGES, RETRY
      throw (RuntimeException) lastException;

    throw OException.wrapException(
        new OrientGraphModificationException(
            "Error on removing edges after vertex ("
                + iVertex.getIdentity()
                + ") delete in non tx environment"),
        lastException);
  }
}
