/**
 * ***************************************************************************
 * Copyright (c) 2010 Qcadoo Limited
 * Project: Qcadoo MES
 * Version: 1.1.4
 *
 * This file is part of Qcadoo.
 *
 * Qcadoo is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation; either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 * ***************************************************************************
 */
package com.qcadoo.mes.ganttForOperations;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.search.SearchRestrictions;
import com.qcadoo.view.api.components.ganttChart.GanttChartItem;
import com.qcadoo.view.api.components.ganttChart.GanttChartScale;

@Service
public class OperationsGanttChartItemResolverImpl implements OperationsGanttChartItemResolver {

    private static final String ORDER_OPERATION_COMPONENT_FIELD = "orderOperationComponent";

    private static final String PRODUCTION_SCHEDULING_MODEL = "productionScheduling";

    private static final String NAME_FIELD = "name";

    private static final String NUMBER_FIELD = "number";

    private static final String NODE_NUMBER_FIELD = "nodeNumber";

    private static final String ORDERS_MODEL = "orders";

    private static final String OPERATION_FIELD = "operation";

    private static final String DATE_TO_FIELD = "dateTo";

    private static final String ORDER_FIELD = "order";

    private static final String DATE_FROM_FIELD = "dateFrom";

    private static final String EFFECTIVE_DATE_TO_FIELD = "effectiveDateTo";

    private static final String EFFECTIVE_DATE_FROM_FIELD = "effectiveDateFrom";

    private static final Logger LOG = LoggerFactory.getLogger(OperationsGanttChartItemResolverImpl.class);

    @Autowired
    private DataDefinitionService dataDefinitionService;

    @Override
    public Map<String, List<GanttChartItem>> resolve(final GanttChartScale scale, final JSONObject context, final Locale locale) {
        try {
            Long orderId = Long.valueOf(context.getString("orderId"));
            Entity order = dataDefinitionService.get(ORDERS_MODEL, ORDER_FIELD).get(orderId);

            if (order == null) {
                LOG.warn("Cannot find order for " + orderId);
                return Collections.emptyMap();
            }

            List<Entity> operations = dataDefinitionService.get(PRODUCTION_SCHEDULING_MODEL, ORDER_OPERATION_COMPONENT_FIELD)
                    .find().add(SearchRestrictions.belongsTo(ORDER_FIELD, order)).list().getEntities();

            if (operations.isEmpty()) {
                LOG.warn("Cannot find operations for " + order);
                return Collections.emptyMap();
            }

            Date orderStartDate = null;
            Date orderEndDate = null;

            if (order.getField(EFFECTIVE_DATE_FROM_FIELD) == null) {
                if (order.getField(DATE_FROM_FIELD) == null) {
                    LOG.warn("Cannot find orderStartDate for " + order);
                    return Collections.emptyMap();
                } else {
                    orderStartDate = (Date) order.getField(DATE_FROM_FIELD);
                    orderEndDate = (Date) order.getField(DATE_TO_FIELD);
                }
            } else {
                orderStartDate = (Date) order.getField(EFFECTIVE_DATE_FROM_FIELD);
                orderEndDate = new Date(((Date) order.getField(EFFECTIVE_DATE_FROM_FIELD)).getTime()
                        + (((Date) order.getField(DATE_TO_FIELD)).getTime() - ((Date) order.getField(DATE_FROM_FIELD)).getTime()));
            }

            scale.setDateFrom(orderStartDate);
            scale.setDateTo(orderEndDate);

            Map<String, List<GanttChartItem>> items = new TreeMap<String, List<GanttChartItem>>();
            Map<String, Integer> counters = new HashMap<String, Integer>();

            for (Entity operation : operations) {
                Date dateFrom = (Date) operation.getField(EFFECTIVE_DATE_FROM_FIELD);
                Date dateTo = (Date) operation.getField(EFFECTIVE_DATE_TO_FIELD);

                if (dateFrom == null || dateTo == null) {
                    continue;
                }

                StringBuffer operationName = new StringBuffer(getDescriptionForOperarion(operation));

                int counter = 0;

                if (counters.containsKey(operationName.toString())) {
                    counter = counters.get(operationName.toString()) + 1;
                    operationName.append(" (");
                    operationName.append(counter);
                    operationName.append(") ");
                }

                GanttChartItem item = scale.createGanttChartItem(operationName.toString(), operationName.toString(),
                        operation.getId(), dateFrom, dateTo);

                if (item != null) {
                    items.put(operationName.toString(), Collections.singletonList(item));
                    counters.put(operationName.toString(), counter);
                }
            }

            return items;
        } catch (NumberFormatException e) {
            throw new IllegalStateException(e.getMessage(), e);
        } catch (JSONException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private String getDescriptionForOperarion(final Entity operation) {
        return operation.getStringField(NODE_NUMBER_FIELD) + " "
                + operation.getBelongsToField(OPERATION_FIELD).getStringField(NUMBER_FIELD) + " "
                + operation.getBelongsToField(OPERATION_FIELD).getStringField(NAME_FIELD);
    }

}