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
package com.evolveum.midpoint.model.common.expression.script;

import com.evolveum.midpoint.model.common.expression.script.xpath.XPathScriptEvaluator;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.crypto.Protector;

import java.io.File;

/**
 * @author Radovan Semancik
 */
public class TestXPathExpressions extends AbstractScriptTest {

    @Override
	protected ScriptEvaluator createEvaluator(PrismContext prismContext, Protector protector) {
		return new XPathScriptEvaluator(prismContext);
	}

	@Override
	protected File getTestDir() {
		return new File(BASE_TEST_DIR, "xpath");
	}

	@Override
	protected boolean supportsRootNode() {
		return true;
	}

}
