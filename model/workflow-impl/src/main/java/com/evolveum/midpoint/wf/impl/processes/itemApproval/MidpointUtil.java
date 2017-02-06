/*
 * Copyright (c) 2010-2017 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.wf.impl.processes.itemApproval;

import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.builder.DeltaBuilder;
import com.evolveum.midpoint.prism.delta.builder.S_ItemEntry;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.builder.QueryBuilder;
import com.evolveum.midpoint.prism.schema.SchemaRegistry;
import com.evolveum.midpoint.prism.util.CloneUtil;
import com.evolveum.midpoint.prism.util.PrismUtil;
import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.ObjectTreeDeltas;
import com.evolveum.midpoint.schema.SchemaConstantsGenerated;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.schema.util.WfContextUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.wf.impl.processes.common.ActivitiUtil;
import com.evolveum.midpoint.wf.impl.processes.common.WfTimedActionTriggerHandler;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.types_3.ObjectDeltaType;
import org.activiti.engine.delegate.DelegateTask;
import org.jetbrains.annotations.NotNull;

import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;
import java.util.*;
import java.util.stream.Collectors;

import static com.evolveum.midpoint.wf.impl.processes.common.SpringApplicationContextHolder.getCacheRepositoryService;
import static com.evolveum.midpoint.wf.impl.processes.common.SpringApplicationContextHolder.getPrismContext;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.TaskType.F_WORKFLOW_CONTEXT;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.WfContextType.F_EVENT;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.WfContextType.F_PROCESSOR_SPECIFIC_STATE;

/**
 * @author mederly
 */
public class MidpointUtil {

	private static final Trace LOGGER = TraceManager.getTrace(MidpointUtil.class);

	public static ApprovalLevelType getApprovalLevelType(String taskOid) {
		RepositoryService cacheRepositoryService = getCacheRepositoryService();
		OperationResult result = new OperationResult(MidpointUtil.class.getName() + ".getApprovalLevelType");
		try {
			PrismObject<TaskType> task = cacheRepositoryService.getObject(TaskType.class, taskOid, null, result);
			return WfContextUtil.getCurrentApprovalLevel(task.asObjectable().getWorkflowContext());
		} catch (Exception e) {
			throw new SystemException("Couldn't retrieve approval level for task " + taskOid + ": " + e.getMessage(), e);
		}
	}

	// additional delta is a bit hack ... TODO refactor (but without splitting the modify operation!)
	public static void recordEventInTask(WfProcessEventType event, ObjectDeltaType additionalDelta, String taskOid, OperationResult result) {
		RepositoryService cacheRepositoryService = getCacheRepositoryService();
		PrismContext prismContext = getPrismContext();
		try {
			S_ItemEntry deltaBuilder = DeltaBuilder.deltaFor(TaskType.class, getPrismContext())
					.item(F_WORKFLOW_CONTEXT, F_EVENT).add(event);

			if (additionalDelta != null) {
				PrismObject<TaskType> task = cacheRepositoryService.getObject(TaskType.class, taskOid, null, result);
				WfPrimaryChangeProcessorStateType state = WfContextUtil
						.getPrimaryChangeProcessorState(task.asObjectable().getWorkflowContext());
				ObjectTreeDeltasType updatedDelta = ObjectTreeDeltas.mergeDeltas(state.getDeltasToProcess(),
						additionalDelta, prismContext);
				ItemPath deltasToProcessPath = new ItemPath(F_WORKFLOW_CONTEXT, F_PROCESSOR_SPECIFIC_STATE, WfPrimaryChangeProcessorStateType.F_DELTAS_TO_PROCESS);		// assuming it already exists!
				ItemDefinition<?> deltasToProcessDefinition = getPrismContext().getSchemaRegistry()
						.findContainerDefinitionByCompileTimeClass(WfPrimaryChangeProcessorStateType.class)
						.findItemDefinition(WfPrimaryChangeProcessorStateType.F_DELTAS_TO_PROCESS);
				deltaBuilder = deltaBuilder.item(deltasToProcessPath, deltasToProcessDefinition)
						.replace(updatedDelta);
			}
			cacheRepositoryService.modifyObject(TaskType.class, taskOid, deltaBuilder.asItemDeltas(), result);
		} catch (ObjectNotFoundException | SchemaException | ObjectAlreadyExistsException e) {
			throw new SystemException("Couldn't record decision to the task " + taskOid + ": " + e.getMessage(), e);
		}
	}

	public static Set<ObjectReferenceType> expandGroups(Set<ObjectReferenceType> approverRefs) {
		PrismContext prismContext = getPrismContext();
		Set<ObjectReferenceType> rv = new HashSet<>();
		for (ObjectReferenceType approverRef : approverRefs) {
			@SuppressWarnings({ "unchecked", "raw" })
			Class<? extends Containerable> clazz = (Class<? extends Containerable>)
					prismContext.getSchemaRegistry().getCompileTimeClassForObjectType(approverRef.getType());
			if (clazz == null) {
				throw new IllegalStateException("Unknown object type " + approverRef.getType());
			}
			if (UserType.class.isAssignableFrom(clazz)) {
				rv.add(approverRef.clone());
			} else if (AbstractRoleType.class.isAssignableFrom(clazz)) {
				rv.addAll(expandAbstractRole(approverRef, prismContext));
			} else {
				LOGGER.warn("Unexpected type {} for approver: {}", clazz, approverRef);
				rv.add(approverRef.clone());
			}
		}
		return rv;
	}

	private static Collection<ObjectReferenceType> expandAbstractRole(ObjectReferenceType approverRef, PrismContext prismContext) {
		ObjectQuery query = QueryBuilder.queryFor(UserType.class, prismContext)
				.item(FocusType.F_ROLE_MEMBERSHIP_REF).ref(approverRef.asReferenceValue())
				.build();
		try {
			return getCacheRepositoryService()
					.searchObjects(UserType.class, query, null, new OperationResult("dummy"))
					.stream()
					.map(o -> ObjectTypeUtil.createObjectRef(o))
					.collect(Collectors.toList());
		} catch (SchemaException e) {
			throw new SystemException("Couldn't resolve " + approverRef + ": " + e.getMessage(), e);
		}
	}

	static void setTaskDeadline(DelegateTask delegateTask, Duration duration) {
		XMLGregorianCalendar deadline = XmlTypeConverter.createXMLGregorianCalendar(new Date());
		deadline.add(duration);
		delegateTask.setDueDate(XmlTypeConverter.toDate(deadline));
	}

	public static void createTriggersForTimedActions(DelegateTask delegateTask, Task wfTask,
			List<WorkItemTimedActionsType> timedActions,
			OperationResult result) {
		try {
			PrismContext prismContext = getPrismContext();
			SchemaRegistry schemaRegistry = prismContext.getSchemaRegistry();
			@SuppressWarnings("unchecked")
			@NotNull PrismPropertyDefinition<String> workItemIdDef =
					schemaRegistry.findPropertyDefinitionByElementName(SchemaConstantsGenerated.C_WORK_ITEM_ID);
			@NotNull PrismContainerDefinition<WorkItemActionsType> workItemActionsDef =
					schemaRegistry.findContainerDefinitionByElementName(SchemaConstantsGenerated.C_WORK_ITEM_ACTIONS);
			List<TriggerType> triggers = new ArrayList<>();
			for (WorkItemTimedActionsType timedAction : timedActions) {
				int escalationLevel = ActivitiUtil.getEscalationLevelNumber(delegateTask.getVariables());
				if (timedAction.getEscalationLevelFrom() != null && escalationLevel < timedAction.getEscalationLevelFrom()) {
					LOGGER.trace("Current escalation level is before 'escalationFrom', skipping timed action {}", timedAction);
					continue;
				}
				if (timedAction.getEscalationLevelTo() != null && escalationLevel > timedAction.getEscalationLevelTo()) {
					LOGGER.trace("Current escalation level is after 'escalationTo', skipping timed action {}", timedAction);
					continue;
				}
				// TODO evaluate the condition
				List<WfTimeSpecificationType> timeSpecifications = CloneUtil.cloneCollectionMembers(timedAction.getTime());
				if (timeSpecifications.isEmpty()) {
					timeSpecifications.add(new WfTimeSpecificationType());
				}
				for (WfTimeSpecificationType timeSpec : timeSpecifications) {
					if (timeSpec.getValue().isEmpty()) {
						timeSpec.getValue().add(XmlTypeConverter.createDuration(0));
					}
					for (Duration duration : timeSpec.getValue()) {
						TriggerType trigger = new TriggerType(prismContext);
						trigger.setTimestamp(computeTriggerTime(duration, timeSpec.getBase(),
								delegateTask.getCreateTime(), delegateTask.getDueDate()));
						trigger.setHandlerUri(WfTimedActionTriggerHandler.HANDLER_URI);
						ExtensionType extension = new ExtensionType(prismContext);
						trigger.setExtension(extension);
						PrismProperty<String> workItemIdProp = workItemIdDef.instantiate();
						workItemIdProp.addRealValue(delegateTask.getId());
						extension.asPrismContainerValue().add(workItemIdProp);
						PrismContainer<WorkItemActionsType> workItemActionsCont = workItemActionsDef.instantiate();
						workItemActionsCont.add(timedAction.getActions().asPrismContainerValue().clone());
						extension.asPrismContainerValue().add(workItemActionsCont);
						triggers.add(trigger);
					}
				}
			}
			LOGGER.trace("Adding {} triggers to {}:\n{}", triggers.size(), wfTask,
					PrismUtil.serializeQuietlyLazily(prismContext, triggers));
			if (triggers.isEmpty()) {
				return;
			}
			List<PrismContainerValue<TriggerType>> pcvList = triggers.stream()
					.map(t -> (PrismContainerValue<TriggerType>) t.asPrismContainerValue())
					.collect(Collectors.toList());
			List<ItemDelta<?, ?>> itemDeltas = DeltaBuilder.deltaFor(TaskType.class, prismContext)
					.item(TaskType.F_TRIGGER).add(pcvList)
					.asItemDeltas();
			getCacheRepositoryService().modifyObject(TaskType.class, wfTask.getOid(), itemDeltas, result);
		} catch (ObjectNotFoundException | SchemaException | ObjectAlreadyExistsException | RuntimeException e) {
			throw new SystemException("Couldn't add trigger(s) to " + wfTask + ": " + e.getMessage(), e);
		}
	}

	@NotNull
	private static XMLGregorianCalendar computeTriggerTime(Duration duration, WfTimeBaseType base, Date start, Date deadline) {
		Date baseTime;
		if (base == null) {
			base = duration.getSign() <= 0 ? WfTimeBaseType.DEADLINE : WfTimeBaseType.WORK_ITEM_CREATION;
		}
		switch (base) {
			case DEADLINE:
				if (deadline == null) {
					throw new IllegalStateException("Couldn't set timed action relative to work item's deadline because"
							+ " the deadline is not set. Requested interval: " + duration);
				}
				baseTime = deadline;
				break;
			case WORK_ITEM_CREATION:
				if (start == null) {
					throw new IllegalStateException("Task's start time is null");
				}
				baseTime = start;
				break;
			default:
				throw new IllegalArgumentException("base: " + base);
		}
		XMLGregorianCalendar rv = XmlTypeConverter.createXMLGregorianCalendar(baseTime);
		rv.add(duration);
		return rv;
	}

	public static void removeTriggersForWorkItem(Task wfTask, String workItemId, OperationResult result) {
		List<PrismContainerValue<TriggerType>> toDelete = new ArrayList<>();
		for (TriggerType triggerType : wfTask.getTaskPrismObject().asObjectable().getTrigger()) {
			if (WfTimedActionTriggerHandler.HANDLER_URI.equals(triggerType.getHandlerUri())) {
				PrismProperty workItemIdProperty = triggerType.getExtension().asPrismContainerValue()
						.findProperty(SchemaConstantsGenerated.C_WORK_ITEM_ID);
				if (workItemIdProperty != null && workItemId.equals(workItemIdProperty.getRealValue())) {
					toDelete.add(triggerType.clone().asPrismContainerValue());
				}
			}
		}
		removeSelectedTriggers(wfTask, toDelete, result);
	}

	public static void removeAllStageTriggersForWorkItem(Task wfTask, OperationResult result) {
		List<PrismContainerValue<TriggerType>> toDelete = new ArrayList<>();
		for (TriggerType triggerType : wfTask.getTaskPrismObject().asObjectable().getTrigger()) {
			if (WfTimedActionTriggerHandler.HANDLER_URI.equals(triggerType.getHandlerUri())) {
				toDelete.add(triggerType.clone().asPrismContainerValue());
			}
		}
		removeSelectedTriggers(wfTask, toDelete, result);
	}

	private static void removeSelectedTriggers(Task wfTask, List<PrismContainerValue<TriggerType>> toDelete, OperationResult result) {
		try {
			LOGGER.trace("About to delete {} triggers from {}: {}", toDelete.size(), wfTask, toDelete);
			List<ItemDelta<?, ?>> itemDeltas = DeltaBuilder.deltaFor(TaskType.class, getPrismContext())
					.item(TaskType.F_TRIGGER).delete(toDelete)
					.asItemDeltas();
			getCacheRepositoryService().modifyObject(TaskType.class, wfTask.getOid(), itemDeltas, result);
		} catch (SchemaException|ObjectNotFoundException|ObjectAlreadyExistsException|RuntimeException e) {
			LoggingUtils.logUnexpectedException(LOGGER, "Couldn't remove triggers from task {}", e, wfTask);
		}
	}


}
