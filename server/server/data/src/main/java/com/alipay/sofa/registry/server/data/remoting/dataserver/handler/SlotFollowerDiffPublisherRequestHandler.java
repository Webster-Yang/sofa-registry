/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alipay.sofa.registry.server.data.remoting.dataserver.handler;

import com.alipay.sofa.registry.common.model.GenericResponse;
import com.alipay.sofa.registry.common.model.Node;
import com.alipay.sofa.registry.common.model.dataserver.DatumSummary;
import com.alipay.sofa.registry.common.model.slot.DataSlotDiffPublisherRequest;
import com.alipay.sofa.registry.common.model.slot.DataSlotDiffSyncResult;
import com.alipay.sofa.registry.common.model.slot.DataSlotDiffUtils;
import com.alipay.sofa.registry.common.model.store.Publisher;
import com.alipay.sofa.registry.log.Logger;
import com.alipay.sofa.registry.log.LoggerFactory;
import com.alipay.sofa.registry.remoting.Channel;
import com.alipay.sofa.registry.remoting.ChannelHandler;
import com.alipay.sofa.registry.server.data.bootstrap.DataServerConfig;
import com.alipay.sofa.registry.server.data.cache.DatumStorage;
import com.alipay.sofa.registry.server.data.cache.SlotManager;
import com.alipay.sofa.registry.server.data.remoting.handler.AbstractServerHandler;
import com.alipay.sofa.registry.util.ParaCheckUtil;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 *
 * @author yuzhi.lyz
 * @version v 0.1 2020-11-06 15:41 yuzhi.lyz Exp $
 */
public class SlotFollowerDiffPublisherRequestHandler
                                                    extends
                                                    AbstractServerHandler<DataSlotDiffPublisherRequest> {

    private static final Logger LOGGER = LoggerFactory
                                           .getLogger(SlotFollowerDiffPublisherRequestHandler.class);

    @Autowired
    private ThreadPoolExecutor  slotSyncRequestProcessorExecutor;

    @Autowired
    private DataServerConfig    dataServerConfig;

    @Autowired
    private DatumStorage        localDatumStorage;

    @Autowired
    private SlotManager         slotManager;

    @Override
    public void checkParam(DataSlotDiffPublisherRequest request) throws RuntimeException {
        ParaCheckUtil.checkNonNegative(request.getSlotId(), "request.slotId");
    }

    @Override
    public Object doHandle(Channel channel, DataSlotDiffPublisherRequest request) {
        try {
            slotManager.triggerUpdateSlotTable(request.getSlotTableEpoch());
            DataSlotDiffSyncResult result = calcDiffResult(request.getSlotId(),
                request.getDatumSummarys(), localDatumStorage.getPublishers(request.getSlotId()));
            result.setSlotTableEpoch(slotManager.getSlotTableEpoch());
            return new GenericResponse().fillSucceed(result);
        } catch (Throwable e) {
            LOGGER.error("DiffSync publisher Request error for slot {}", request.getSlotId(), e);
            throw new RuntimeException("DiffSync Request error!", e);
        }
    }

    private DataSlotDiffSyncResult calcDiffResult(int targetSlot,
                                                  Map<String, DatumSummary> datumSummarys,
                                                  Map<String, Map<String, Publisher>> existingPublishers) {
        DataSlotDiffSyncResult result = DataSlotDiffUtils.diffPublishersResult(datumSummarys,
            existingPublishers, dataServerConfig.getSlotSyncPublisherMaxNum());
        DataSlotDiffUtils.logDiffResult(result, targetSlot, LOGGER);
        return result;
    }

    @Override
    public ChannelHandler.HandlerType getType() {
        return ChannelHandler.HandlerType.PROCESSER;
    }

    @Override
    protected Node.NodeType getConnectNodeType() {
        return Node.NodeType.DATA;
    }

    @Override
    public Class interest() {
        return SlotFollowerDiffPublisherRequestHandler.class;
    }

    @Override
    public Object buildFailedResponse(String msg) {
        return new GenericResponse().fillFailed(msg);
    }

    @Override
    public Executor getExecutor() {
        return slotSyncRequestProcessorExecutor;
    }
}
