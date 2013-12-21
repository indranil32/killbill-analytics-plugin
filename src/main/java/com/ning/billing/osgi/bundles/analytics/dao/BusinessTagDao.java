/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.osgi.bundles.analytics.dao;

import java.util.UUID;

import org.osgi.service.log.LogService;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;

import com.ning.billing.clock.Clock;
import com.ning.billing.osgi.bundles.analytics.AnalyticsRefreshException;
import com.ning.billing.osgi.bundles.analytics.dao.factory.BusinessTagFactory;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessModelDaosWithAccountAndTenantRecordId;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessTagModelDao;
import com.ning.billing.util.audit.AccountAuditLogs;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillDataSource;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

public class BusinessTagDao extends BusinessAnalyticsDaoBase {

    private final BusinessTagFactory bTagFactory;

    public BusinessTagDao(final OSGIKillbillLogService logService,
                          final OSGIKillbillAPI osgiKillbillAPI,
                          final OSGIKillbillDataSource osgiKillbillDataSource,
                          final Clock clock) {
        super(logService, osgiKillbillDataSource);
        bTagFactory = new BusinessTagFactory(logService, osgiKillbillAPI, osgiKillbillDataSource, clock);
    }

    public void update(final UUID accountId, final AccountAuditLogs accountAuditLogs, final CallContext context) throws AnalyticsRefreshException {
        logService.log(LogService.LOG_INFO, "Starting rebuild of Analytics tags for account " + accountId);

        final BusinessModelDaosWithAccountAndTenantRecordId<BusinessTagModelDao> tagModelDaos = bTagFactory.createBusinessTags(accountId, accountAuditLogs, context);

        sqlDao.inTransaction(new Transaction<Void, BusinessAnalyticsSqlDao>() {
            @Override
            public Void inTransaction(final BusinessAnalyticsSqlDao transactional, final TransactionStatus status) throws Exception {
                updateInTransaction(tagModelDaos, transactional, context);
                return null;
            }
        });

        logService.log(LogService.LOG_INFO, "Finished rebuild of Analytics tags for account " + accountId);
    }

    private void updateInTransaction(final BusinessModelDaosWithAccountAndTenantRecordId<BusinessTagModelDao> tagModelDaos,
                                     final BusinessAnalyticsSqlDao transactional,
                                     final CallContext context) {
        for (final String tableName : BusinessTagModelDao.ALL_TAGS_TABLE_NAMES) {
            transactional.deleteByAccountRecordId(tableName,
                                                  tagModelDaos.getAccountRecordId(),
                                                  tagModelDaos.getTenantRecordId(),
                                                  context);
        }

        for (final BusinessTagModelDao tagModelDao : tagModelDaos.getBusinessModelDaos()) {
            transactional.create(tagModelDao.getTableName(), tagModelDao, context);
        }
    }
}
