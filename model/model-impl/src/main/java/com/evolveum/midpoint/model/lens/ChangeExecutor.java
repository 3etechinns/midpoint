/*
 * Copyright (c) 2010-2013 Evolveum
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

package com.evolveum.midpoint.model.lens;

import static com.evolveum.midpoint.common.InternalsConfig.consistencyChecks;

import com.evolveum.midpoint.common.expression.Expression;
import com.evolveum.midpoint.common.expression.ExpressionEvaluationContext;
import com.evolveum.midpoint.common.expression.ExpressionFactory;
import com.evolveum.midpoint.common.refinery.RefinedObjectClassDefinition;
import com.evolveum.midpoint.model.api.ModelExecuteOptions;
import com.evolveum.midpoint.model.api.PolicyViolationException;
import com.evolveum.midpoint.model.api.context.ModelContext;
import com.evolveum.midpoint.model.api.context.SynchronizationPolicyDecision;
import com.evolveum.midpoint.model.controller.ModelUtils;
import com.evolveum.midpoint.model.sync.SynchronizationSituation;
import com.evolveum.midpoint.model.util.Utils;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismObjectDefinition;
import com.evolveum.midpoint.prism.PrismPropertyDefinition;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.PrismReference;
import com.evolveum.midpoint.prism.PrismReferenceValue;
import com.evolveum.midpoint.prism.delta.ChangeType;
import com.evolveum.midpoint.prism.delta.ContainerDelta;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.delta.PrismValueDeltaSetTriple;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.delta.ReferenceDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.provisioning.api.ProvisioningOperationOptions;
import com.evolveum.midpoint.provisioning.api.ProvisioningService;
import com.evolveum.midpoint.provisioning.api.ResourceObjectShadowChangeDescription;
import com.evolveum.midpoint.repo.api.RepoAddOptions;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.ObjectDeltaOperation;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.constants.ExpressionConstants;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.schema.util.ShadowUtil;
import com.evolveum.midpoint.schema.util.SynchronizationSituationUtil;
import com.evolveum.midpoint.task.api.LightweightTaskHandler;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.task.api.TaskManager;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.JAXBUtil;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.wf.api.WorkflowService;
import com.evolveum.midpoint.xml.ns._public.common.api_types_2.PropertyReferenceListType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.*;

import com.evolveum.midpoint.xml.ns._public.model.model_context_2.LensContextType;
import org.apache.commons.lang.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.xml.bind.JAXBElement;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;

/**
 * @author semancik
 */
@Component
public class ChangeExecutor {

    private static final Trace LOGGER = TraceManager.getTrace(ChangeExecutor.class);

	private static final String OPERATION_EXECUTE_DELTA = ChangeExecutor.class.getName() + ".executeDelta";
	private static final String OPERATION_EXECUTE = ChangeExecutor.class.getName() + ".execute";
	private static final String OPERATION_EXECUTE_FOCUS = OPERATION_EXECUTE + ".focus";
    private static final String OPERATION_EXECUTE_PROJECTIONS = OPERATION_EXECUTE + ".projections";
	private static final String OPERATION_EXECUTE_PROJECTION = OPERATION_EXECUTE + ".projection";
	private static final String OPERATION_LINK_ACCOUNT = ChangeExecutor.class.getName() + ".linkAccount";
	private static final String OPERATION_UNLINK_ACCOUNT = ChangeExecutor.class.getName() + ".unlinkAccount";
	private static final String OPERATION_UPDATE_SITUATION_ACCOUNT = ChangeExecutor.class.getName() + ".updateSituationInAccount";

    private static final long SUBTASKS_EXECUTION_TIMEOUT = 10000L;      // for testing purposes

    @Autowired(required = true)
    private transient TaskManager taskManager;

    @Autowired(required = true)
    @Qualifier("cacheRepositoryService")
    private transient RepositoryService cacheRepositoryService;

    @Autowired(required = true)
    private ProvisioningService provisioning;
    
    @Autowired(required = true)
    private PrismContext prismContext;
    
    @Autowired(required = true)
	private ExpressionFactory expressionFactory;

    // for inserting workflow-related metadata to changed object
    @Autowired(required = false)
    private WorkflowService workflowService;
    
    private PrismObjectDefinition<UserType> userDefinition = null;
    private PrismObjectDefinition<ShadowType> shadowDefinition = null;
    
    @PostConstruct
    private void locateUserDefinition() {
    	userDefinition = prismContext.getSchemaRegistry().findObjectDefinitionByCompileTimeClass(UserType.class);
    	shadowDefinition = prismContext.getSchemaRegistry().findObjectDefinitionByCompileTimeClass(ShadowType.class);
    }

    // true = we continue on foreground; false = some processing continues on background (i.e. we have to exit clockwork in current thread)
    public <F extends ObjectType, P extends ObjectType> boolean executeChanges(final LensContext<F,P> syncContext, Task task, OperationResult parentResult) throws ObjectAlreadyExistsException,
            ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException {
    	
    	OperationResult result = parentResult.createSubresult(OPERATION_EXECUTE);
    	
    	// FOCUS
    	
    	final LensFocusContext<F> focusContext = syncContext.getFocusContext();
    	if (focusContext != null) {
	        ObjectDelta<F> userDelta = focusContext.getWaveDelta(syncContext.getExecutionWave());
	        if (userDelta != null) {
	
	        	OperationResult subResult = result.createSubresult(OPERATION_EXECUTE_FOCUS+"."+focusContext.getObjectTypeClass().getSimpleName());
	        	try {
	        		
		            executeDelta(userDelta, focusContext, syncContext, null, task, subResult);
		
	                subResult.computeStatus();
	                
	        	} catch (SchemaException e) {
	        		recordFatalError(subResult, result, null, e);
	    			throw e;
	    		} catch (ObjectNotFoundException e) {
	        		recordFatalError(subResult, result, null, e);
	    			throw e;
	    		} catch (ObjectAlreadyExistsException e) {
	    			subResult.computeStatus();
	    			if (!subResult.isSuccess()) {
	    				subResult.recordFatalError(e);
	    			}
	    			result.computeStatusComposite();
	    			throw e;
	    		} catch (CommunicationException e) {
	        		recordFatalError(subResult, result, null, e);
	    			throw e;
	    		} catch (ConfigurationException e) {
	        		recordFatalError(subResult, result, null, e);
	    			throw e;
	    		} catch (SecurityViolationException e) {
	        		recordFatalError(subResult, result, null, e);
	    			throw e;
	    		} catch (ExpressionEvaluationException e) {
	        		recordFatalError(subResult, result, null, e);
	    			throw e;
	    		} catch (RuntimeException e) {
	        		recordFatalError(subResult, result, null, e);
	    			throw e;
	    		}  
	        } else {
	            LOGGER.trace("Skipping focus change execute, because user delta is null");
	        }
    	}

    	// PROJECTIONS

        OperationResult projectionsResult = result.createSubresult(OPERATION_EXECUTE_PROJECTIONS);

        List<Task> executionSubtasks = new ArrayList<Task>();
    	
        for (final LensProjectionContext<P> accCtx : syncContext.getProjectionContexts()) {
        	if (accCtx.getWave() != syncContext.getExecutionWave()) {
        		continue;
			}
        	final OperationResult projectionResult = projectionsResult.createSubresult(OPERATION_EXECUTE_PROJECTION+"."+accCtx.getObjectTypeClass().getSimpleName());
        	projectionResult.addContext("discriminator", accCtx.getResourceShadowDiscriminator());
			if (accCtx.getResource() != null) {
				projectionResult.addParam("resource", accCtx.getResource().getName());
			}
            LightweightTaskHandler executeDeltaHandler = new LightweightTaskHandler() {

                @Override
                public void run(Task subtask) {

                    try {

                        executeReconciliationScript(accCtx, syncContext, ProvisioningScriptOrderType.BEFORE, subtask, projectionResult);

                        ObjectDelta<P> accDelta = accCtx.getExecutableDelta();

                        if (accCtx.getSynchronizationPolicyDecision() == SynchronizationPolicyDecision.BROKEN) {
                            if (syncContext.getFocusContext().getDelta() != null
                                    && syncContext.getFocusContext().getDelta().isDelete()
                                    && syncContext.getOptions() != null
                                    && ModelExecuteOptions.isForce(syncContext.getOptions())) {
                                if (accDelta == null) {
                                    accDelta = ObjectDelta.createDeleteDelta(accCtx.getObjectTypeClass(),
                                            accCtx.getOid(), prismContext);
                                }
                            }
                            if (accDelta != null && accDelta.isDelete()) {

                                executeDelta(accDelta, accCtx, syncContext, accCtx.getResource(), subtask, projectionResult);

                            }
                        } else {

                            if (accDelta == null || accDelta.isEmpty()) {
                                if (LOGGER.isTraceEnabled()) {
                                    LOGGER.trace("No change for account "
                                            + accCtx.getResourceShadowDiscriminator());
                                }
                                if (focusContext != null) {
                                    updateAccountLinks(focusContext.getObjectNew(), focusContext, accCtx, subtask,
                                            projectionResult);
                                }

                                // Make sure post-reconcile delta is always executed, even if there is no change
                                executeReconciliationScript(accCtx, syncContext, ProvisioningScriptOrderType.AFTER, subtask, projectionResult);

                                projectionResult.computeStatus();
                                projectionResult.recordNotApplicableIfUnknown();
                                return;

                            }

                            executeDelta(accDelta, accCtx, syncContext, accCtx.getResource(), subtask, projectionResult);

                        }

                        if (focusContext != null) {
                            updateAccountLinks(focusContext.getObjectNew(), focusContext, accCtx, subtask, projectionResult);
                        }

                        executeReconciliationScript(accCtx, syncContext, ProvisioningScriptOrderType.AFTER, subtask, projectionResult);

                        projectionResult.computeStatus();
                        projectionResult.recordNotApplicableIfUnknown();
                    } catch (SchemaException e) {
                        recordProjectionExecutionException(e, accCtx, projectionResult, SynchronizationPolicyDecision.BROKEN);
                    } catch (ObjectNotFoundException e) {
                        recordProjectionExecutionException(e, accCtx, projectionResult, SynchronizationPolicyDecision.BROKEN);
                    } catch (ObjectAlreadyExistsException e) {
                        // in his case we do not need to set account context as
                        // broken, instead we need to restart projector for this
                        // context to recompute new account or find out if the
                        // account was already linked..
                        // and also do not set fatal error to the operation result, this is a special case
                        // if it is fatal, it will be set later
                        // but we need to set some result
                        projectionResult.recordHandledError(e);
                    } catch (CommunicationException e) {
                        recordProjectionExecutionException(e, accCtx, projectionResult, SynchronizationPolicyDecision.BROKEN);
                    } catch (ConfigurationException e) {
                        recordProjectionExecutionException(e, accCtx, projectionResult, SynchronizationPolicyDecision.BROKEN);
                    } catch (SecurityViolationException e) {
                        recordProjectionExecutionException(e, accCtx, projectionResult, SynchronizationPolicyDecision.BROKEN);
                    } catch (ExpressionEvaluationException e) {
                        recordProjectionExecutionException(e, accCtx, projectionResult, SynchronizationPolicyDecision.BROKEN);
                    } catch (RuntimeException e) {
                        recordProjectionExecutionException(e, accCtx, projectionResult, SynchronizationPolicyDecision.BROKEN);
                    }

                    // if the task carrying this Runnable was switched to background in the meantime, we have to store
                    // current context to the task extension

                    if (subtask.isPersistent()) {
                        try {
                            storeModelContext(subtask, syncContext, projectionResult);
                        } catch (SchemaException e) {       // we can do nothing meaningful with these exceptions here
                            throw new RuntimeException(e);
                        } catch (ObjectAlreadyExistsException e) {
                            throw new RuntimeException(e);
                        } catch (ObjectNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

                public void storeModelContext(Task task, ModelContext context, OperationResult result) throws SchemaException, ObjectAlreadyExistsException, ObjectNotFoundException {
                    PrismContainer<LensContextType> modelContext = ((LensContext) context).toPrismContainer();
                    task.setExtensionContainer(modelContext);
                    task.savePendingModifications(result);
                }

                private <P extends ObjectType> void recordProjectionExecutionException(Exception e, LensProjectionContext<P> accCtx,
                                                                                       OperationResult subResult, SynchronizationPolicyDecision decision) {
                    subResult.recordFatalError(e);
                    LOGGER.error("Error executing changes for {}: {}", new Object[]{accCtx.toHumanReadableString(), e.getMessage(), e});
                    if (decision != null) {
                        accCtx.setSynchronizationPolicyDecision(decision);
                    }
                }

            };

            Task subtask = task.createSubtask(executeDeltaHandler);
            subtask.setResult(projectionResult);
            subtask.start();
            LOGGER.debug("Execution subtask {} for {} has been created and started", subtask, accCtx.getResource());
            executionSubtasks.add(subtask);
		}

        boolean subtasksFinished = task.waitForTransientSubtasks(SUBTASKS_EXECUTION_TIMEOUT, result);
        if (!subtasksFinished) {
            LOGGER.trace("Some subtasks have not finished yet; root task = {}", task);
            for (Task subtask : task.getTransientSubtasks()) {
                subtask.switchToBackground(result);
            }
            task.switchToBackground(result);
        }

        // Result computation for projections needs to be slightly different
        projectionsResult.computeStatusComposite();
        result.computeStatus();

        if (!subtasksFinished && !result.isError()) {
            result.recordInProgress();
        }

        return subtasksFinished;
    }

	private void recordFatalError(OperationResult subResult, OperationResult result, String message, Throwable e) {
		if (message == null) {
			message = e.getMessage();
		}
		subResult.recordFatalError(e);
		if (result != null) {
			result.computeStatusComposite();
		}
	}

	/**
     * Make sure that the account is linked (or unlinked) as needed.
     */
    private <F extends ObjectType, P extends ObjectType> void updateAccountLinks(PrismObject<F> prismObject,
    		LensFocusContext<F> focusContext, LensProjectionContext<P> accCtx,
    		Task task, OperationResult result) throws ObjectNotFoundException, SchemaException {
    	if (prismObject == null) {
    		return;
    	}
        F objectTypeNew = prismObject.asObjectable();
        if (!(objectTypeNew instanceof UserType)) {
        	return;
        }
        UserType userTypeNew = (UserType) objectTypeNew;
        
        if (accCtx.getResourceShadowDiscriminator() != null && accCtx.getResourceShadowDiscriminator().getOrder() > 0) {
        	// Don't mess with links for higher-order contexts. The link should be dealt with
        	// during processing of zero-order context.
        	return;
        }
        
        String accountOid = accCtx.getOid();
        if (accountOid == null) {
        	if (accCtx.getSynchronizationPolicyDecision() == SynchronizationPolicyDecision.BROKEN) {
        		// This seems to be OK. In quite a strange way, but still OK.
        		return;
        	}
        	LOGGER.trace("Account has null OID, this should not happen, context:\n{}", accCtx.dump());
            throw new IllegalStateException("Account has null OID, this should not happen");
        }

        if (accCtx.getSynchronizationPolicyDecision() == SynchronizationPolicyDecision.UNLINK 
        		|| accCtx.getSynchronizationPolicyDecision() == SynchronizationPolicyDecision.DELETE
        		|| accCtx.getSynchronizationPolicyDecision() == SynchronizationPolicyDecision.BROKEN
        		|| accCtx.isDelete()) {
            // Link should NOT exist
        	
        	PrismReference accountRef = userTypeNew.asPrismObject().findReference(UserType.F_LINK_REF);
        	if (accountRef != null) {
        		for (PrismReferenceValue accountRefVal: accountRef.getValues()) {
        			if (accountRefVal.getOid().equals(accountOid)) {
                        // Linked, need to unlink
                        unlinkAccount(userTypeNew.getOid(), accountRefVal, (LensFocusContext<UserType>) focusContext, task, result);
                    }
        		}
        		
        	}
            
    		if (accCtx.isDelete() || accCtx.isThombstone()) {
    			LOGGER.trace("Account {} deleted, updating also situation in account.", accountOid);	
				updateSituationInAccount(task, SynchronizationSituationType.DELETED, focusContext, accCtx, result);
    		} else {
    			// This should NOT be UNLINKED. We just do not know the situation here. Reflect that in the shadow.
				LOGGER.trace("Account {} unlinked from the user, updating also situation in account.", accountOid);	
				updateSituationInAccount(task, null, focusContext, accCtx, result);
    		}
            // Not linked, that's OK

        } else {
            // Link should exist
        	
            for (ObjectReferenceType accountRef : userTypeNew.getLinkRef()) {
                if (accountOid.equals(accountRef.getOid())) {
                    // Already linked, nothing to do, only be sure, the situation is set with the good value
                	LOGGER.trace("Updating situation in already linked account.");
                	updateSituationInAccount(task, SynchronizationSituationType.LINKED, focusContext, accCtx, result);
                	return;
                }
            }
            // Not linked, need to link
            linkAccount(userTypeNew.getOid(), accountOid, (LensFocusContext<UserType>) focusContext, task, result);
            //be sure, that the situation is set correctly
            LOGGER.trace("Updating situation after account was linked.");
            updateSituationInAccount(task, SynchronizationSituationType.LINKED, focusContext, accCtx, result);
        }
    }

    private void linkAccount(String userOid, String accountOid, LensElementContext<UserType> userContext, Task task, OperationResult parentResult) throws ObjectNotFoundException,
            SchemaException {

        LOGGER.trace("Linking account " + accountOid + " to user " + userOid);
        
        OperationResult result = parentResult.createSubresult(OPERATION_LINK_ACCOUNT);
        
        PrismReferenceValue accountRef = new PrismReferenceValue();
        accountRef.setOid(accountOid);
        accountRef.setTargetType(ShadowType.COMPLEX_TYPE);

        Collection<? extends ItemDelta> accountRefDeltas = ReferenceDelta.createModificationAddCollection(
        		UserType.F_LINK_REF, getUserDefinition(), accountRef); 

        try {
            cacheRepositoryService.modifyObject(UserType.class, userOid, accountRefDeltas, result);
        } catch (ObjectAlreadyExistsException ex) {
            throw new SystemException(ex);
        } finally {
        	result.computeStatus();
        	ObjectDelta<UserType> userDelta = ObjectDelta.createModifyDelta(userOid, accountRefDeltas, UserType.class, prismContext);
        	LensObjectDeltaOperation<UserType> userDeltaOp = new LensObjectDeltaOperation<UserType>(userDelta);
            userDeltaOp.setExecutionResult(result);
    		userContext.addToExecutedDeltas(userDeltaOp);
        }

    }

	private PrismObjectDefinition<UserType> getUserDefinition() {
		return userDefinition;
	}

	private void unlinkAccount(String userOid, PrismReferenceValue accountRef, LensElementContext<UserType> userContext, Task task, OperationResult parentResult) throws
            ObjectNotFoundException, SchemaException {

        LOGGER.trace("Deleting accountRef " + accountRef + " from user " + userOid);
        
        OperationResult result = parentResult.createSubresult(OPERATION_UNLINK_ACCOUNT);

        Collection<? extends ItemDelta> accountRefDeltas = ReferenceDelta.createModificationDeleteCollection(
        		UserType.F_LINK_REF, getUserDefinition(), accountRef.clone()); 
        
        try {
            cacheRepositoryService.modifyObject(UserType.class, userOid, accountRefDeltas, result);
        } catch (ObjectAlreadyExistsException ex) {
        	result.recordFatalError(ex);
            throw new SystemException(ex);
        } finally {
        	result.computeStatus();
        	ObjectDelta<UserType> userDelta = ObjectDelta.createModifyDelta(userOid, accountRefDeltas, UserType.class, prismContext);
        	LensObjectDeltaOperation<UserType> userDeltaOp = new LensObjectDeltaOperation<UserType>(userDelta);
            userDeltaOp.setExecutionResult(result);
    		userContext.addToExecutedDeltas(userDeltaOp);
        }
 
    }
	
    private <F extends ObjectType, P extends ObjectType> void updateSituationInAccount(Task task, SynchronizationSituationType situation, 
    		LensFocusContext<F> focusContext, LensProjectionContext<P> projectionCtx, OperationResult parentResult) throws ObjectNotFoundException, SchemaException{

    	String projectionOid = projectionCtx.getOid();
    	
    	OperationResult result = new OperationResult(OPERATION_UPDATE_SITUATION_ACCOUNT);
    	result.addParam("situation", situation);
    	result.addParam("accountRef", projectionOid);
		
    	PrismObject<ShadowType> account = null;
    	try {
    		account = provisioning.getObject(ShadowType.class, projectionOid, 
    				SelectorOptions.createCollection(GetOperationOptions.createNoFetch()), task, result);
    	} catch (Exception ex){
    		LOGGER.trace("Problem with getting account, skipping modifying situation in account.");
			return;
    	}
    	List<PropertyDelta<?>> syncSituationDeltas = SynchronizationSituationUtil.createSynchronizationSituationAndDescriptionDelta(account,
    			situation, task.getChannel(), projectionCtx.hasFullShadow());

		try {
            Utils.setRequestee(task, focusContext);
			String changedOid = provisioning.modifyObject(ShadowType.class, projectionOid,
					syncSituationDeltas, null, ProvisioningOperationOptions.createCompletePostponed(false),
					task, result);
//			modifyProvisioningObject(AccountShadowType.class, accountRef, syncSituationDeltas, ProvisioningOperationOptions.createCompletePostponed(false), task, result);
			projectionCtx.setSynchronizationSituationResolved(situation);
			LOGGER.trace("Situation in projection {} was updated to {}.", projectionCtx, situation);
		} catch (ObjectNotFoundException ex) {
			// if the object not found exception is thrown, it's ok..probably
			// the account was deleted by previous execution of changes..just
			// log in the trace the message for the user.. 
			LOGGER.trace("Situation in account could not be updated. Account not found on the resource. Skipping modifying situation in account");
			return;
		} catch (Exception ex) {
            throw new SystemException(ex.getMessage(), ex);
        } finally {
            Utils.clearRequestee(task);
        }
		// if everything is OK, add result of the situation modification to the
		// parent result
		result.recordSuccess();
		parentResult.addSubresult(result);
		
	}
    
	private <T extends ObjectType, F extends ObjectType, P extends ObjectType>
    	void executeDelta(ObjectDelta<T> objectDelta, LensElementContext<T> objectContext, LensContext<F,P> context,
    			ResourceType resource, Task task, OperationResult parentResult) 
    			throws ObjectAlreadyExistsException, ObjectNotFoundException, SchemaException, CommunicationException,
    			ConfigurationException, SecurityViolationException, ExpressionEvaluationException {
		
        if (objectDelta == null) {
            throw new IllegalArgumentException("Null change");
        }
        
        if (alreadyExecuted(objectDelta, objectContext)) {
        	LOGGER.debug("Skipping execution of delta because it was already executed: {}", objectContext);
        	return;
        }
        
        if (consistencyChecks) objectDelta.checkConsistence();
        
        // Other types than user type may not be definition-complete (e.g. accounts and resources are completed in provisioning)
        if (UserType.class.isAssignableFrom(objectDelta.getObjectTypeClass())) {
        	objectDelta.assertDefinitions();
        }
        
    	if (LOGGER.isTraceEnabled()) {
    		logDeltaExecution(objectDelta, context, resource, null);
    	}

    	OperationResult result = parentResult.createSubresult(OPERATION_EXECUTE_DELTA);
    		
    	try {
    		
	        if (objectDelta.getChangeType() == ChangeType.ADD) {
	            executeAddition(objectDelta, context, resource, task, result);
	        } else if (objectDelta.getChangeType() == ChangeType.MODIFY) {
	        	executeModification(objectDelta, objectContext, context, resource, task, result);
	        } else if (objectDelta.getChangeType() == ChangeType.DELETE) {
	            executeDeletion(objectDelta, context, resource, task, result);
	        }
	        
	        // To make sure that the OID is set (e.g. after ADD operation)
	        objectContext.setOid(objectDelta.getOid());
	        
    	} finally {
    		
    		result.computeStatus();
    		LensObjectDeltaOperation<T> objectDeltaOp = new LensObjectDeltaOperation<T>(objectDelta.clone());
	        objectDeltaOp.setExecutionResult(result);
	        objectContext.addToExecutedDeltas(objectDeltaOp);
        
	        if (LOGGER.isDebugEnabled()) {
	        	if (LOGGER.isTraceEnabled()) {
	        		LOGGER.trace("EXECUTION result {}", result.getLastSubresult());
	        	} else {
	        		// Execution of deltas was not logged yet
	        		logDeltaExecution(objectDelta, context, resource, result.getLastSubresult());
	        	}
	    	}
    	}
    }
	
	private <T extends ObjectType, F extends ObjectType, P extends ObjectType> boolean alreadyExecuted(
			ObjectDelta<T> objectDelta, LensElementContext<T> objectContext) {
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("Checking for already executed delta:\n{}\nIn deltas:\n{}",
					objectDelta.dump(), DebugUtil.debugDump(objectContext.getExecutedDeltas()));
		}
		return ObjectDeltaOperation.containsDelta(objectContext.getExecutedDeltas(), objectDelta);
	}
	
	private ProvisioningOperationOptions copyFromModelOptions(ModelExecuteOptions options) {
		ProvisioningOperationOptions provisioningOptions = new ProvisioningOperationOptions();
		if (options == null){
			return provisioningOptions;
		}
		
		provisioningOptions.setForce(options.getForce());
		provisioningOptions.setOverwrite(options.getOverwrite());
		return provisioningOptions;
	}

	private <T extends ObjectType, F extends ObjectType, P extends ObjectType>
				void logDeltaExecution(ObjectDelta<T> objectDelta, LensContext<F,P> context, 
						ResourceType resource, OperationResult result) {
		StringBuilder sb = new StringBuilder();
		sb.append("---[ ");
		if (result == null) {
			sb.append("Going to EXECUTE");
		} else {
			sb.append("EXECUTED");
		}
		sb.append(" delta of ").append(objectDelta.getObjectTypeClass().getSimpleName());
		sb.append(" ]---------------------\n");
		DebugUtil.debugDumpLabel(sb, "Channel", 0);
		sb.append(" ").append(context.getChannel()).append("\n");
		DebugUtil.debugDumpLabel(sb, "Wave", 0);
		sb.append(" ").append(context.getExecutionWave()).append("\n");
		if (resource != null) {
			sb.append("Resource: ").append(resource.toString()).append("\n");
		}
		sb.append(objectDelta.dump());
		sb.append("\n");
		if (result != null) {
			DebugUtil.debugDumpLabel(sb, "Result", 0);
			sb.append(" ").append(result.getStatus()).append(": ").append(result.getMessage());
		}
		sb.append("\n--------------------------------------------------");
		
		LOGGER.debug("\n{}", sb);
	}

    private <T extends ObjectType, F extends ObjectType, P extends ObjectType> void executeAddition(ObjectDelta<T> change, 
    		LensContext<F, P> context, ResourceType resource, Task task, OperationResult result) 
    				throws ObjectAlreadyExistsException, ObjectNotFoundException, SchemaException, CommunicationException, 
    				ConfigurationException, SecurityViolationException, ExpressionEvaluationException {

        PrismObject<T> objectToAdd = change.getObjectToAdd();

        if (change.getModifications() != null) {
            for (ItemDelta delta : change.getModifications()) {
                delta.applyTo(objectToAdd);
            }
            change.getModifications().clear();
        }

        T objectTypeToAdd = objectToAdd.asObjectable();

    	applyMetadata(context.getChannel(), task, objectTypeToAdd, result);
    	
        String oid;
        if (objectTypeToAdd instanceof TaskType) {
            oid = addTask((TaskType) objectTypeToAdd, result);
        } else if (objectTypeToAdd instanceof NodeType) {
            throw new UnsupportedOperationException("NodeType cannot be added using model interface");
        } else if (ObjectTypes.isManagedByProvisioning(objectTypeToAdd)) {
        	ProvisioningOperationOptions options = copyFromModelOptions(context.getOptions());
        	if (context.getChannel() != null && context.getChannel().equals(QNameUtil.qNameToUri(SchemaConstants.CHANGE_CHANNEL_RECON))){
        		options.setCompletePostponed(false);
    		}
            oid = addProvisioningObject(objectToAdd, context, options, resource, task, result);
            if (oid == null) {
            	throw new SystemException("Provisioning addObject returned null OID while adding " + objectToAdd);
            }
            result.addReturn("createdAccountOid", oid);
        } else {
        	RepoAddOptions addOpt = new RepoAddOptions();
        	if (ModelExecuteOptions.isOverwrite(context.getOptions())){
        		addOpt.setOverwrite(true);
        	}
        	if (ModelExecuteOptions.isNoCrypt(context.getOptions())){
        		addOpt.setAllowUnencryptedValues(true);
        	}
            oid = cacheRepositoryService.addObject(objectToAdd, addOpt, result);
            if (oid == null) {
            	throw new SystemException("Repository addObject returned null OID while adding " + objectToAdd);
            }
        }
        change.setOid(oid);
    }

    
    private <T extends ObjectType, F extends ObjectType, P extends ObjectType> void executeDeletion(ObjectDelta<T> change, 
    		LensContext<F,P> context, ResourceType resource, Task task, OperationResult result) throws
            ObjectNotFoundException, ObjectAlreadyExistsException, SchemaException, CommunicationException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException {

        String oid = change.getOid();
        Class<T> objectTypeClass = change.getObjectTypeClass();

        if (TaskType.class.isAssignableFrom(objectTypeClass)) {
            taskManager.deleteTask(oid, result);
        } else if (NodeType.class.isAssignableFrom(objectTypeClass)) {
            taskManager.deleteNode(oid, result);
        } else if (ObjectTypes.isClassManagedByProvisioning(objectTypeClass)) {
        	ProvisioningOperationOptions options = copyFromModelOptions(context.getOptions());
        	if (context.getChannel() != null && context.getChannel().equals(QNameUtil.qNameToUri(SchemaConstants.CHANGE_CHANNEL_RECON))){
        		options.setCompletePostponed(false);
    		}
            deleteProvisioningObject(objectTypeClass, oid, context, options, resource, task, result);
        } else {
            cacheRepositoryService.deleteObject(objectTypeClass, oid, result);
        }
    }

    private <T extends ObjectType, F extends ObjectType, P extends ObjectType> void executeModification(ObjectDelta<T> change,
            LensElementContext<T> objectContext,
    		LensContext<F, P> context, ResourceType resource, Task task, OperationResult result)
            throws ObjectNotFoundException, SchemaException, ObjectAlreadyExistsException, CommunicationException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException {
        if (change.isEmpty()) {
            // Nothing to do
            return;
        }
        Class<T> objectTypeClass = change.getObjectTypeClass();
        	
    	applyMetadata(change, objectContext, objectTypeClass, task, context.getChannel(), result);
        
        if (TaskType.class.isAssignableFrom(objectTypeClass)) {
            taskManager.modifyTask(change.getOid(), change.getModifications(), result);
        } else if (NodeType.class.isAssignableFrom(objectTypeClass)) {
            throw new UnsupportedOperationException("NodeType is not modifiable using model interface");
        } else if (ObjectTypes.isClassManagedByProvisioning(objectTypeClass)) {
            ProvisioningOperationOptions options = copyFromModelOptions(context.getOptions());
            if (context.getChannel() != null && context.getChannel().equals(QNameUtil.qNameToUri(SchemaConstants.CHANGE_CHANNEL_RECON))){
                options.setCompletePostponed(false);
            }
            String oid = modifyProvisioningObject(objectTypeClass, change.getOid(), change.getModifications(), context, options, resource, task, result);
            if (!oid.equals(change.getOid())){
                change.setOid(oid);
            }
        } else {
            cacheRepositoryService.modifyObject(objectTypeClass, change.getOid(), change.getModifications(), result);
        }
    }

	private <T extends ObjectType> void applyMetadata(String contextChannel, Task task, T objectTypeToAdd, OperationResult result) throws SchemaException {
		MetadataType metaData = new MetadataType();
		String channel = getChannel(contextChannel, task);
		metaData.setCreateChannel(channel);
		metaData.setCreateTimestamp(XmlTypeConverter.createXMLGregorianCalendar(System.currentTimeMillis()));
		if (task.getOwner() != null) {
			metaData.setCreatorRef(ObjectTypeUtil.createObjectRef(task.getOwner()));
		}
        if (workflowService != null) {
            metaData.getCreateApproverRef().addAll(workflowService.getApprovedBy(task, result));
        }

		objectTypeToAdd.setMetadata(metaData);
	}
    
    private String getChannel(String contextChannel, Task task){
    	if (contextChannel != null){
    		return contextChannel;
    	} else if (task.getChannel() != null){
    		return task.getChannel();
    	}
    	return null;
    }
    
    private <T extends ObjectType> void applyMetadata(ObjectDelta<T> change, LensElementContext<T> objectContext, Class objectTypeClass, Task task, String contextChannel, OperationResult result) throws SchemaException {
        String channel = getChannel(contextChannel, task);

    	PrismObjectDefinition def = prismContext.getSchemaRegistry().findObjectDefinitionByCompileTimeClass(objectTypeClass);

        if (channel != null) {
            PropertyDelta delta = PropertyDelta.createModificationReplaceProperty((new ItemPath(ObjectType.F_METADATA, MetadataType.F_MODIFY_CHANNEL)), def, channel);
            ((Collection) change.getModifications()).add(delta);
        }
        PropertyDelta delta = PropertyDelta.createModificationReplaceProperty((new ItemPath(ObjectType.F_METADATA, MetadataType.F_MODIFY_TIMESTAMP)), def, XmlTypeConverter.createXMLGregorianCalendar(System.currentTimeMillis()));
        ((Collection) change.getModifications()).add(delta);
        if (task.getOwner() != null) {
            ReferenceDelta refDelta = ReferenceDelta.createModificationReplace((new ItemPath(ObjectType.F_METADATA,
                    MetadataType.F_MODIFIER_REF)), def, task.getOwner().getOid());
            ((Collection) change.getModifications()).add(refDelta);
        }

        List<PrismReferenceValue> approverReferenceValues = new ArrayList<PrismReferenceValue>();

        if (workflowService != null) {
            for (ObjectReferenceType approverRef : workflowService.getApprovedBy(task, result)) {
                approverReferenceValues.add(new PrismReferenceValue(approverRef.getOid()));
            }
        }

        if (!approverReferenceValues.isEmpty()) {
            ReferenceDelta refDelta = ReferenceDelta.createModificationReplace((new ItemPath(ObjectType.F_METADATA,
                        MetadataType.F_MODIFY_APPROVER_REF)), def, approverReferenceValues);
            ((Collection) change.getModifications()).add(refDelta);
        } else {

            // a bit of hack - we want to replace all existing values with empty set of values;
            // however, it is not possible to do this using REPLACE, so we have to explicitly remove all existing values

            if (objectContext.getObjectOld() != null) {
                // a null value of objectOld means that we execute MODIFY delta that is a part of primary ADD operation (in a wave greater than 0)
                // i.e. there are NO modifyApprovers set (theoretically they could be set in previous waves, but because in these waves the data
                // are taken from the same source as in this step - so there are none modify approvers).

                if (objectContext.getObjectOld().asObjectable().getMetadata() != null) {
                    List<ObjectReferenceType> existingModifyApproverRefs = objectContext.getObjectOld().asObjectable().getMetadata().getModifyApproverRef();
                    LOGGER.trace("Original values of MODIFY_APPROVER_REF: {}", existingModifyApproverRefs);

                    if (!existingModifyApproverRefs.isEmpty()) {
                        List<PrismReferenceValue> valuesToDelete = new ArrayList<PrismReferenceValue>();
                        for (ObjectReferenceType approverRef : objectContext.getObjectOld().asObjectable().getMetadata().getModifyApproverRef()) {
                            valuesToDelete.add(new PrismReferenceValue(approverRef.getOid()));
                        }
                        ReferenceDelta refDelta = ReferenceDelta.createModificationDelete((new ItemPath(ObjectType.F_METADATA,
                                MetadataType.F_MODIFY_APPROVER_REF)), def, valuesToDelete);
                        ((Collection) change.getModifications()).add(refDelta);
                    }
                }
            }
        }

    }

    private String addTask(TaskType task, OperationResult result) throws ObjectAlreadyExistsException,
            ObjectNotFoundException {
        try {
            return taskManager.addTask(task.asPrismObject(), result);
        } catch (ObjectAlreadyExistsException ex) {
            throw ex;
        } catch (Exception ex) {
            LoggingUtils.logException(LOGGER, "Couldn't add object {} to task manager", ex, task.getName());
            throw new SystemException(ex.getMessage(), ex);
        }
    }

    private <F extends ObjectType, P extends ObjectType> String addProvisioningObject(PrismObject<? extends ObjectType> object, LensContext<F, P> context, ProvisioningOperationOptions options, ResourceType resource, Task task, OperationResult result)
            throws ObjectNotFoundException, ObjectAlreadyExistsException, SchemaException,
            CommunicationException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException {

        if (object.canRepresent(ShadowType.class)) {
            ShadowType shadow = (ShadowType) object.asObjectable();
            String resourceOid = ShadowUtil.getResourceOid(shadow);
            if (resourceOid == null) {
                throw new IllegalArgumentException("Resource OID is null in shadow");
            }
        }

        OperationProvisioningScriptsType scripts = prepareScripts(object, context, ProvisioningOperationTypeType.ADD, resource, result);
        Utils.setRequestee(task, context);
        String oid = provisioning.addObject(object, scripts, options, task, result);
        Utils.clearRequestee(task);
        return oid;
    }

    private <F extends ObjectType, P extends ObjectType> void deleteProvisioningObject(Class<? extends ObjectType> objectTypeClass, String oid,
    		LensContext<F, P> context, ProvisioningOperationOptions options, ResourceType resource, Task task, 
            OperationResult result) throws ObjectNotFoundException, ObjectAlreadyExistsException,
            SchemaException, CommunicationException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException {
    	
		OperationProvisioningScriptsType scripts = null;
		try {
			PrismObject<? extends ObjectType> shadowToModify = provisioning.getObject(objectTypeClass, oid,
					SelectorOptions.createCollection(GetOperationOptions.createNoFetch()), task, result);
			scripts = prepareScripts(shadowToModify, context, ProvisioningOperationTypeType.DELETE, resource,
					result);
		} catch (ObjectNotFoundException ex) {
			// this is almost OK, mute the error and try to delete account (it
			// will fail if something is wrong)
			result.muteLastSubresultError();
		}
        Utils.setRequestee(task, context);
		provisioning.deleteObject(objectTypeClass, oid, options, scripts, task, result);
        Utils.clearRequestee(task);
    }

    private <F extends ObjectType, P extends ObjectType> String modifyProvisioningObject(Class<? extends ObjectType> objectTypeClass, String oid,
            Collection<? extends ItemDelta> modifications, LensContext<F, P> context, ProvisioningOperationOptions options, 
            ResourceType resource, Task task, OperationResult result) throws ObjectNotFoundException, CommunicationException, SchemaException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException, ObjectAlreadyExistsException {

    	PrismObject<? extends ObjectType> shadowToModify = provisioning.getObject(objectTypeClass, oid, 
    			SelectorOptions.createCollection(GetOperationOptions.createRaw()), task, result);
    	OperationProvisioningScriptsType scripts = prepareScripts(shadowToModify, context, ProvisioningOperationTypeType.MODIFY, resource, result);
        Utils.setRequestee(task, context);
        String changedOid = provisioning.modifyObject(objectTypeClass, oid, modifications, scripts, options, task, result);
        Utils.clearRequestee(task);
        return changedOid;
    }

    private <F extends ObjectType, P extends ObjectType> OperationProvisioningScriptsType prepareScripts(
    		PrismObject<? extends ObjectType> changedObject, LensContext<F, P> context, 
    		ProvisioningOperationTypeType operation, ResourceType resource, OperationResult result) throws ObjectNotFoundException,
            SchemaException, CommunicationException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException {
    	
    	if (!changedObject.canRepresent(ShadowType.class)) {
    		return null;
    	}
    	
    	if (resource == null){
    		LOGGER.warn("Resource does not exist. Skipping processing scripts.");
    		return null;
    	}
    	OperationProvisioningScriptsType resourceScripts = resource.getScripts();
    	PrismObject<? extends ShadowType> resourceObject = (PrismObject<? extends ShadowType>) changedObject;
        
        PrismObject<F> user = null;
		if (context.getFocusContext() != null){
			if (context.getFocusContext().getObjectNew() != null){
			user = context.getFocusContext().getObjectNew();
			} else if (context.getFocusContext().getObjectOld() != null){
				user = context.getFocusContext().getObjectOld();
			}	
		}
        
        Map<QName, Object> variables = getDefaultExpressionVariables((PrismObject<UserType>) user, resourceObject, resource.asPrismObject());
        return evaluateScript(resourceScripts, operation, null, variables, result);
      
    }
	
	private OperationProvisioningScriptsType evaluateScript(OperationProvisioningScriptsType resourceScripts, 
			ProvisioningOperationTypeType operation, ProvisioningScriptOrderType order, Map<QName, Object> variables, OperationResult result) throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException{
		  OperationProvisioningScriptsType outScripts = new OperationProvisioningScriptsType();
	        if (resourceScripts != null) {
	        	OperationProvisioningScriptsType scripts = resourceScripts.clone();
	        	for (OperationProvisioningScriptType script: scripts.getScript()) {
	        		if (script.getOperation().contains(operation)) {
	        			if (order == null || order == script.getOrder()) {
		        			for (ProvisioningScriptArgumentType argument : script.getArgument()){
		        				evaluateScriptArgument(argument, variables, result);
		        			}
		        			outScripts.getScript().add(script);
	        			}
	        		}
	        	}
	        }

	        return outScripts;
	}
    
    private void evaluateScriptArgument(ProvisioningScriptArgumentType argument, Map<QName, Object> variables, OperationResult result) throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException{
    	
    	QName FAKE_SCRIPT_ARGUMENT_NAME = new QName(SchemaConstants.NS_C, "arg");
    	
    	PrismPropertyDefinition scriptArgumentDefinition = new PrismPropertyDefinition(FAKE_SCRIPT_ARGUMENT_NAME,
				FAKE_SCRIPT_ARGUMENT_NAME, DOMUtil.XSD_STRING, prismContext);
    	
    	String shortDesc = "Provisioning script argument expression";
    	Expression<PrismPropertyValue<String>> expression = expressionFactory.makeExpression(argument, scriptArgumentDefinition, shortDesc, result);
    	
    	
    	ExpressionEvaluationContext params = new ExpressionEvaluationContext(null, variables, shortDesc, result);
		PrismValueDeltaSetTriple<PrismPropertyValue<String>> outputTriple = expression.evaluate(params);
		
		Collection<PrismPropertyValue<String>> nonNegativeValues = null;
		if (outputTriple != null) {
			nonNegativeValues = outputTriple.getNonNegativeValues();
		}
			
		//replace dynamic script with static value..
		argument.getExpressionEvaluator().clear();
		if (nonNegativeValues == null || nonNegativeValues.isEmpty()) {
			// We need to create at least one evaluator. Otherwise the expression code will complain
			Element value = DOMUtil.createElement(SchemaConstants.C_VALUE);
			DOMUtil.setNill(value);
			JAXBElement<Element> el = new JAXBElement(SchemaConstants.C_VALUE, Element.class, value);
			argument.getExpressionEvaluator().add(el);
			
		} else {
			for (PrismPropertyValue<String> val : nonNegativeValues){
				Element value = DOMUtil.createElement(SchemaConstants.C_VALUE);
				value.setTextContent(val.getValue());
				JAXBElement<Element> el = new JAXBElement(SchemaConstants.C_VALUE, Element.class, value);
				argument.getExpressionEvaluator().add(el);
			}
		}
	}
    
    private Map<QName, Object> getDefaultExpressionVariables(PrismObject<UserType> user, 
    		PrismObject<? extends ShadowType> account, PrismObject<ResourceType> resource) {		
		Map<QName, Object> variables = new HashMap<QName, Object>();
		variables.put(ExpressionConstants.VAR_USER, user);
		variables.put(ExpressionConstants.VAR_FOCUS, user);
		variables.put(ExpressionConstants.VAR_ACCOUNT, account);
		variables.put(ExpressionConstants.VAR_SHADOW, account);
		variables.put(ExpressionConstants.VAR_RESOURCE, resource);
		return variables;
	}
    
    private <T extends ObjectType, F extends ObjectType, P extends ObjectType>
	void executeReconciliationScript(LensProjectionContext<P> projContext, LensContext<F,P> context,
			ProvisioningScriptOrderType order, Task task, OperationResult parentResult) throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException, CommunicationException, ConfigurationException, SecurityViolationException, ObjectAlreadyExistsException {
    	
    	if (!projContext.isDoReconciliation()) {
    		return;
    	}
    	
    	ResourceType resource = projContext.getResource();
    	if (resource == null){
    		LOGGER.warn("Resource does not exist. Skipping processing reconciliation scripts.");
    		return;
    	}
    	
    	OperationProvisioningScriptsType resourceScripts = resource.getScripts();
    	if (resourceScripts == null) {
    		return;
    	}
        
        PrismObject<F> user = null;
        PrismObject<ShadowType> shadow = null;
        
		if (context.getFocusContext() != null){
			if (context.getFocusContext().getObjectNew() != null){
				user = context.getFocusContext().getObjectNew();
				} else if (context.getFocusContext().getObjectOld() != null){
					user = context.getFocusContext().getObjectOld();
				}	
//			if (order == ProvisioningScriptOrderType.BEFORE) {
//				user = context.getFocusContext().getObjectOld();
//			} else if (order == ProvisioningScriptOrderType.AFTER) {
//				user = context.getFocusContext().getObjectNew();
//			} else {
//				throw new IllegalArgumentException("Unknown order "+order);
//			}	
		}
		
		if (order == ProvisioningScriptOrderType.BEFORE) {
			shadow = (PrismObject<ShadowType>) projContext.getObjectOld();
		} else if (order == ProvisioningScriptOrderType.AFTER) {
			shadow = (PrismObject<ShadowType>) projContext.getObjectNew();
		} else {
			throw new IllegalArgumentException("Unknown order "+order);
		}
        
		Map<QName, Object> variables = getDefaultExpressionVariables((PrismObject<UserType>) user, shadow, resource.asPrismObject());
        OperationProvisioningScriptsType evaluatedScript = evaluateScript(resourceScripts, 
        		ProvisioningOperationTypeType.RECONCILE, order, variables, parentResult);

        for (OperationProvisioningScriptType script: evaluatedScript.getScript()) {
            Utils.setRequestee(task, context);
        	provisioning.executeScript(resource.getOid(), script, task, parentResult);
            Utils.clearRequestee(task);
        }
    }

}
