/*
 * Copyright 2015 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.cloudstore.xenon.entity;

import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;

import java.util.UUID;

/**
 * Class EntityLockServiceFactory is a factory to create a EntityLockService instances.
 */
public class EntityLockServiceFactory extends FactoryService {

  public static final String SELF_LINK = ServiceUriPaths.CLOUDSTORE_ROOT + "/entity-locks";

  public EntityLockServiceFactory() {
    super(EntityLockService.State.class);
    super.setPeerNodeSelectorPath(ServiceUriPaths.DEFAULT_CLOUD_STORE_NODE_SELECTOR);
    super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
  }

  @Override
  public Service createServiceInstance() throws Throwable {
    return new EntityLockService();
  }

  @Override
  public void handleStop(Operation stop) {
    ServiceUtils.logWarning(this, "Stopping factory service %s", stop);
    super.handleStop(stop);
  }

  @Override
  public void handleDelete(Operation delete) {
    ServiceUtils.logWarning(this, "Deleting factory service %s", delete);
    super.handleDelete(delete);
  }

  /**
   * Use GUID instead of Xenon generated IDs for readability.
   * The time difference of computing GUID and hash is insignificant in our case because the rate of creation is low.
   */
  @Override
  protected String buildDefaultChildSelfLink() {
    return UUID.randomUUID().toString();
  }
}
