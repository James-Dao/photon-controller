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

package com.vmware.photon.controller.common.xenon;

import com.vmware.photon.controller.common.manifest.BuildInfo;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.annotations.VisibleForTesting;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Predicate;

/**
 * Test helper used to test Xenon services in isolation.
 */
public class BasicServiceHost
    extends ServiceHost
    implements XenonHostInfoProvider {
  public static final String FIELD_NAME_SELF_LINK = "SELF_LINK";
  public static final String SERVICE_URI = "/TestService";
  public static final int WAIT_ITERATION_SLEEP = 500;
  public static final int WAIT_ITERATION_COUNT = 10000 / WAIT_ITERATION_SLEEP;
  public static final String BIND_ADDRESS = "0.0.0.0";
  public static final Integer BIND_PORT = 0;
  public static final String REFERRER = "test-basic-service-host";

  @VisibleForTesting
  protected String serviceUri;

  @VisibleForTesting
  protected int waitIterationSleep;

  @VisibleForTesting
  protected int waitIterationCount;

  @VisibleForTesting
  protected String bindAddress;

  @VisibleForTesting
  protected Integer bindPort;

  @VisibleForTesting
  protected String storagePath;

  public BasicServiceHost(String storagePath,
                          String serviceUri,
                          int waitIterationSleep,
                          int waitIterationCount) {
    this.bindAddress = BIND_ADDRESS;
    this.bindPort = BIND_PORT;
    this.storagePath = storagePath;
    this.serviceUri = serviceUri;
    this.waitIterationSleep = waitIterationSleep;
    this.waitIterationCount = waitIterationCount;
  }

  public BasicServiceHost() {
    this(null, SERVICE_URI,
        WAIT_ITERATION_SLEEP, WAIT_ITERATION_COUNT);
  }

  public static BasicServiceHost create(String storagePath,
                                        String serviceUri,
                                        int waitIterationSleep,
                                        int waitIterationCount)
      throws Throwable {
    BasicServiceHost host = new BasicServiceHost(storagePath,
        serviceUri,
        waitIterationSleep,
        waitIterationCount);

    host.initialize();
    host.startWithCoreServices();
    return host;
  }

  public static BasicServiceHost create() throws Throwable {
    BasicServiceHost host = new BasicServiceHost();
    host.initialize();
    host.startWithCoreServices();
    return host;
  }

  public static void destroy(BasicServiceHost host) throws Throwable {
    host.destroy();
  }

  @Override
  public boolean isReady() {
    return super.getState().isStarted;
  }

  @SuppressWarnings("rawtypes")
  @Override
  public Class[] getFactoryServices() {
    return new Class[0];
  }

  @Override
  public BuildInfo getBuildInfo() {
    return BuildInfo.get(this.getClass());
  }

  protected synchronized void initialize() throws Throwable {

    ServiceHost.Arguments arguments = new ServiceHost.Arguments();
    arguments.port = this.bindPort;
    arguments.bindAddress = this.bindAddress;

    if (StringUtils.isBlank(this.storagePath)) {
      this.storagePath = Files.createTempDirectory(null).toString();
    }
    arguments.sandbox = Paths.get(this.storagePath);

    super.initialize(arguments);
  }

  protected synchronized BasicServiceHost startWithCoreServices() throws Throwable {
    super.start();
    super.startDefaultCoreServicesSynchronously();
    return this;
  }

  public synchronized void destroy() throws Throwable {
    if (isStarted()) {
      ServiceHostUtils.destroy(this);
    }
  }

  public synchronized void createStorage() throws IOException {
    if (!StringUtils.isBlank(this.storagePath)) {
      new File(this.storagePath).mkdirs();
    }
  }

  public synchronized void cleanStorage() throws IOException {
    if (!StringUtils.isBlank(this.storagePath)) {
      FileUtils.deleteDirectory(new File(this.storagePath));
    }
  }

  public Operation startFactoryServiceSynchronously(Service service, String path) throws Throwable {
    if (!FactoryService.class.isAssignableFrom(service.getClass())) {
      throw new IllegalArgumentException("Service " + service.getClass().getName() + " is not FactoryService");
    }

    Operation post = Operation
        .createPost(UriUtils.buildUri(this, path));

    OperationLatch syncPost = new OperationLatch(post);
    startService(post, service);
    return syncPost.awaitOperationCompletion();
  }

  public Operation startServiceSynchronously(Service service, ServiceDocument body) throws Throwable {
    return startServiceSynchronously(service, body, this.serviceUri);
  }

  public Operation startServiceSynchronously(Service service, ServiceDocument body, String path) throws Throwable {
    return startServiceSynchronously(service, body, path, true);
  }

  public Operation startServiceSynchronously(
      Service service, ServiceDocument body, String path, Boolean disableOptions) throws Throwable {

    if (disableOptions) {
      service.toggleOption(Service.ServiceOption.OWNER_SELECTION, false);
      service.toggleOption(Service.ServiceOption.REPLICATION, false);
    }

    Operation post = Operation
        .createPost(UriUtils.buildUri(this, path));
    if (body != null) {
      post.setBody(body);
    }

    OperationLatch syncPost = new OperationLatch(post);
    startService(post, service);
    Operation completedOperation = syncPost.awaitOperationCompletion();
    return OperationUtils.handleCompletedOperation(post, completedOperation);
  }

  public Operation deleteServiceSynchronously() throws Throwable {
    return deleteServiceSynchronously(SERVICE_URI);
  }

  public Operation deleteServiceSynchronously(String path) throws Throwable {

    Operation delete = Operation
        .createDelete(UriUtils.buildUri(this, path))
        .setBody("{}")
        .setReferer(UriUtils.buildUri(this, REFERRER));

    OperationLatch syncDelete = new OperationLatch(delete);
    sendRequest(delete);
    return syncDelete.awaitOperationCompletion();
  }

  public Operation sendRequestAndWait(Operation op) throws Throwable {
    return ServiceHostUtils.sendRequestAndWait(this, op, REFERRER);
  }

  public <T> T getServiceState(Class<T> type) throws Throwable {
    return getServiceState(type, this.serviceUri);
  }

  public <T> T getServiceState(Class<T> type, String path) throws Throwable {
    return ServiceHostUtils.getServiceState(this, type, path, "test-basic-service-host");
  }

  /**
   * Wait for the state change.
   *
   * @param type
   * @param <T>
   * @return
   * @throws Throwable
   */
  public <T> T waitForState(Class<T> type, Predicate<T> predicate) throws Throwable {
    return waitForState(this.serviceUri, type, predicate);
  }

  /**
   * Wait for the state change.
   *
   * @param type
   * @param <T>
   * @return
   * @throws Throwable
   */
  public <T> T waitForState(String uri, Class<T> type, Predicate<T> predicate) throws Throwable {
    return ServiceHostUtils.waitForServiceState(
        type, uri, predicate, this, this.waitIterationSleep,
        this.waitIterationCount, null);
  }

  /**
   * Wait for a query to returns particular information.
   *
   * @param query
   * @param predicate
   * @return
   * @throws Throwable
   */
  public QueryTask waitForQuery(QueryTask query, Predicate<QueryTask> predicate) throws Throwable {
    return ServiceHostUtils.waitForQuery(this, REFERRER, query, predicate,
        this.waitIterationCount, this.waitIterationSleep);
  }

  /**
   * Wait for a query by providing the customized waiting period.
   * @param query
   * @param predicate
   * @return
   * @throws Throwable
   */
  public QueryTask waitForQuery(QueryTask query,
                                Predicate<QueryTask> predicate,
                                long waitIterationCount,
                                long waitIterationSleep) throws Throwable {
    return ServiceHostUtils.waitForQuery(this, REFERRER, query, predicate,
        waitIterationCount, waitIterationSleep);
  }
}
