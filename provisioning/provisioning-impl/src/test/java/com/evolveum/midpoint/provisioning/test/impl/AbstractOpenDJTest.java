/**
 * Copyright (c) 2012 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 *
 * Portions Copyrighted 2012 [name of copyright owner]
 */
package com.evolveum.midpoint.provisioning.test.impl;

import static com.evolveum.midpoint.provisioning.test.impl.AbstractOpenDJTest.ACCOUNT_BAD_FILENAME;
import static com.evolveum.midpoint.provisioning.test.impl.AbstractOpenDJTest.LDAP_CONNECTOR_TYPE;
import static com.evolveum.midpoint.provisioning.test.impl.AbstractOpenDJTest.RESOURCE_OPENDJ_FILENAME;
import static com.evolveum.midpoint.test.IntegrationTestTools.display;
import static org.testng.AssertJUnit.assertEquals;

import java.util.List;

import javax.xml.namespace.QName;

import org.springframework.beans.factory.annotation.Autowired;

import com.evolveum.icf.dummy.resource.DummyAccount;
import com.evolveum.icf.dummy.resource.DummyAttributeDefinition;
import com.evolveum.icf.dummy.resource.DummyObjectClass;
import com.evolveum.icf.dummy.resource.DummyResource;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.EqualsFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.provisioning.ProvisioningTestUtil;
import com.evolveum.midpoint.provisioning.api.ProvisioningService;
import com.evolveum.midpoint.provisioning.impl.ConnectorTypeManager;
import com.evolveum.midpoint.provisioning.test.mock.SynchornizationServiceMock;
import com.evolveum.midpoint.provisioning.ucf.impl.ConnectorFactoryIcfImpl;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ResourceObjectShadowUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.AbstractIntegrationTest;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.AccountShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ConnectorType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ResourceObjectShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ResourceType;

/**
 * @author semancik
 *
 */
public abstract class AbstractOpenDJTest extends AbstractIntegrationTest {
	
	protected static final String TEST_DIR_NAME = "src/test/resources/impl";
	
	protected static final String RESOURCE_OPENDJ_FILENAME = ProvisioningTestUtil.COMMON_TEST_DIR_FILENAME + "resource-opendj.xml";
	protected static final String RESOURCE_OPENDJ_INITIALIZED_FILENAME = ProvisioningTestUtil.COMMON_TEST_DIR_FILENAME + "resource-opendj-initialized.xml";
	protected static final String RESOURCE_OPENDJ_OID = "ef2bc95b-76e0-59e2-86d6-3d4f02d3ffff";
	
	protected static final String ACCOUNT1_FILENAME = TEST_DIR_NAME + "/account1.xml";
	protected static final String ACCOUNT1_REPO_FILENAME = TEST_DIR_NAME + "/account1-repo.xml";
	protected static final String ACCOUNT1_OID = "dbb0c37d-9ee6-44a4-8d39-016dbce1cccc";
	
	protected static final String ACCOUNT_NEW_FILENAME = TEST_DIR_NAME + "/account-new.xml";
	protected static final String ACCOUNT_NEW_OID = "c0c010c0-d34d-b44f-f11d-333222123456";
	
	protected static final String ACCOUNT_BAD_FILENAME = TEST_DIR_NAME + "/account-bad.xml";
	protected static final String ACCOUNT_BAD_OID = "dbb0c37d-9ee6-44a4-8d39-016dbce1ffff";
	
	protected static final String ACCOUNT_MODIFY_FILENAME = TEST_DIR_NAME + "/account-modify.xml";
	protected static final String ACCOUNT_MODIFY_OID = "c0c010c0-d34d-b44f-f11d-333222444555";
	
	protected static final String ACCOUNT_MODIFY_PASSWORD_FILENAME = TEST_DIR_NAME + "/account-modify-password.xml";
	protected static final String ACCOUNT_MODIFY_PASSWORD_OID = "c0c010c0-d34d-b44f-f11d-333222444566";
	
	protected static final String ACCOUNT_DELETE_FILENAME = TEST_DIR_NAME + "/account-delete.xml";
	protected static final String ACCOUNT_DELETE_OID = "c0c010c0-d34d-b44f-f11d-333222654321";
	
	protected static final String ACCOUNT_SEARCH_ITERATIVE_FILENAME = TEST_DIR_NAME + "/account-search-iterative.xml";
	protected static final String ACCOUNT_SEARCH_ITERATIVE_OID = "c0c010c0-d34d-b44f-f11d-333222666666";
	
	protected static final String ACCOUNT_SEARCH_FILENAME = TEST_DIR_NAME + "/account-search.xml";
	protected static final String ACCOUNT_SEARCH_OID = "c0c010c0-d34d-b44f-f11d-333222777777";
	
	protected static final String ACCOUNT_NEW_WITH_PASSWORD_FILENAME = TEST_DIR_NAME + "/account-new-with-password.xml";;
	protected static final String ACCOUNT_NEW_WITH_PASSWORD_OID = "c0c010c0-d34d-b44f-f11d-333222124422";
	
	protected static final String ACCOUNT_DISABLE_SIMULATED_FILENAME = TEST_DIR_NAME + "/account-disable-simulated-opendj.xml";
	protected static final String ACCOUNT_DISABLE_SIMULATED_OID = "dbb0c37d-9ee6-44a4-8d39-016dbce1aaaa";
	
	protected static final String ACCOUNT_NO_SN_FILENAME = TEST_DIR_NAME + "/account-opendj-no-sn.xml";
	protected static final String ACCOUNT_NO_SN_OID = "c0c010c0-d34d-beef-f33d-113222123444";
	
	protected static final String NON_EXISTENT_OID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
	
	protected static final String RESOURCE_NS = "http://midpoint.evolveum.com/xml/ns/public/resource/instance/ef2bc95b-76e0-59e2-86d6-3d4f02d3ffff";
	protected static final QName RESOURCE_OPENDJ_ACCOUNT_OBJECTCLASS = new QName(RESOURCE_NS,"AccountObjectClass");
	protected static final String LDAP_CONNECTOR_TYPE = "org.identityconnectors.ldap.LdapConnector";
		
	private static final Trace LOGGER = TraceManager.getTrace(AbstractOpenDJTest.class);
	
	protected PrismObject<ResourceType> resource;
	protected ResourceType resourceType;
	protected PrismObject<ConnectorType> connector;
	
	@Autowired(required = true)
	protected ProvisioningService provisioningService;

	// Used to make sure that the connector is cached
	@Autowired(required = true)
	protected ConnectorTypeManager connectorTypeManager;

	@Autowired(required = true)
	protected SynchornizationServiceMock syncServiceMock;

	
	@Override
	public void initSystem(Task initTask, OperationResult initResult) throws Exception {
		provisioningService.postInit(initResult);
		PrismObject<ResourceType> resource = addResourceFromFile(RESOURCE_OPENDJ_FILENAME, LDAP_CONNECTOR_TYPE, initResult);
//		addObjectFromFile(FILENAME_ACCOUNT1);
		addObjectFromFile(ACCOUNT_BAD_FILENAME, AccountShadowType.class, initResult);
	}

}
