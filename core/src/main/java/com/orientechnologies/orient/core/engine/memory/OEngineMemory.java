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
package com.orientechnologies.orient.core.engine.memory;

import com.orientechnologies.common.exception.OException;
import com.orientechnologies.common.io.OIOUtils;
import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.common.log.OLogger;
import com.orientechnologies.orient.core.db.OrientDBInternal;
import com.orientechnologies.orient.core.engine.OEngineAbstract;
import com.orientechnologies.orient.core.engine.OMemoryAndLocalPaginatedEnginesInitializer;
import com.orientechnologies.orient.core.exception.ODatabaseException;
import com.orientechnologies.orient.core.storage.OStorage;
import com.orientechnologies.orient.core.storage.memory.ODirectMemoryStorage;

public class OEngineMemory extends OEngineAbstract {
  private static final OLogger logger = OLogManager.instance().logger(OEngineMemory.class);
  public static final String NAME = "memory";

  public OEngineMemory() {}

  public OStorage createStorage(
      String url,
      long maxWalSegSize,
      long doubleWriteLogMaxSegSize,
      int storageId,
      OrientDBInternal context) {
    try {
      return new ODirectMemoryStorage(url, url, storageId, context);
    } catch (Exception e) {
      final String message = "Error on opening in memory storage: " + url;
      logger.error(message, e);

      throw OException.wrapException(new ODatabaseException(message), e);
    }
  }

  public String getName() {
    return NAME;
  }

  @Override
  public String getNameFromPath(String dbPath) {
    return OIOUtils.getRelativePathIfAny(dbPath, null);
  }

  @Override
  public void startup() {
    OMemoryAndLocalPaginatedEnginesInitializer.INSTANCE.initialize();
    super.startup();
  }
}
