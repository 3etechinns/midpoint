/*
 * Copyright (c) 2011 Evolveum
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
 * Portions Copyrighted 2011 [name of copyright owner]
 * Portions Copyrighted 2011 Peter Prochazka
 */

package com.evolveum.midpoint.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

import org.apache.commons.lang.StringUtils;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;

import com.evolveum.midpoint.common.result.OperationResult;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_1.AppenderConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ClassLoggerConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.FileAppenderConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.LoggingConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.SubSystemLoggerConfigurationType;

public class LoggingConfigurationManager {

	final static Trace LOGGER = TraceManager.getTrace(LoggingConfigurationType.class);

	public static void configure(LoggingConfigurationType config, OperationResult result) {
		OperationResult res = result.createSubresult("Logging reconfiguration configuration");
		LOGGER.info("Changing logging configuration");
		//Get current log configuration
		LoggerContext lc = (LoggerContext) TraceManager.getILoggerFactory();

		//Prepare configurator in current context
		JoranConfigurator configurator = new JoranConfigurator();
		configurator.setContext(lc);

		//Generate configuration file as string
		String configXml = prepareConfiguration(config);

		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("New loging configuration:");
			LOGGER.trace(configXml);
		}
		
		InputStream cis = new ByteArrayInputStream(configXml.getBytes());
		LOGGER.info("Reseting current logging configuration");
		lc.getStatusManager().clear();
		lc.reset();
		//Switch to new logging configuration
		lc.setName("MidPoint");
		try {
			configurator.doConfigure(cis);
			LOGGER.info("New logging configuration applied");
		} catch (JoranException e) {
			LOGGER.error("Error during applying logging configuration: " + e.getMessage(), e);
			result.createSubresult("Applying logging configuration.").recordFatalError(e.getMessage(), e);
		}
		//Get messages if error occurred;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		StatusPrinter.setPrintStream(new PrintStream(baos));
		StatusPrinter.printIfErrorsOccured(lc);

		String error = null;
		try {
			error = baos.toString("UTF8");
		} catch (UnsupportedEncodingException e) {
			//Nothing never happend
		}
		
		if (!StringUtils.isEmpty(error)) {
			res.recordPartialError(error);
		} else {
			res.recordSuccess();
		}
		
		return;
	}

	private static String prepareConfiguration(LoggingConfigurationType config) {

		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		sb.append("<configuration scan=\"false\">");

		//Generate subsystem logging quickstep
		for (SubSystemLoggerConfigurationType ss: config.getSubSystemLogger()) {
			sb.append("\t<turboFilter class=\"com.evolveum.midpoint.util.logging.MDCLevelTurboFilter\">\n");
			sb.append("\tt<MDCKey>subSystem</MDCKey>\n");
			sb.append("\t\t<MDCValue>");
			sb.append(ss.getComponent());
			sb.append("</MDCValue>\n");
			sb.append("\t\t<level>");
			sb.append(ss.getLevel());
			sb.append("</Level>\n");
			sb.append("\t\t<OnMatch>ACCEPT</OnMatch>\n");
			sb.append("\t</turboFilter>\n");
		}
		
		//Generate appenders configuration
		for (AppenderConfigurationType appender : config.getAppender()) {
			if (appender instanceof FileAppenderConfigurationType) {
				FileAppenderConfigurationType a = (FileAppenderConfigurationType) appender;
				sb.append("\t<appender name=\"");
				sb.append(a.getName());
				sb.append("\" class=\"ch.qos.logback.core.rolling.RollingFileAppender\">\n");
				sb.append("\t\t<file>");
				sb.append(a.getFileName());
				sb.append("</file>\n");
				sb.append("\t\t<append>");
				sb.append(a.isAppend());
				sb.append("\t\t</append>\n");
				//rolling policy
				sb.append("\t\t<rollingPolicy class=\"ch.qos.logback.core.rolling.TimeBasedRollingPolicy\">\n");
				sb.append("\t\t\t<fileNamePattern>");
				sb.append(a.getFilePattern());
				sb.append("</fileNamePattern>\n");
				if (a.getMaxHistory() > 0) {
					sb.append("\t\t<maxHistory>");
					sb.append(a.getMaxHistory());
					sb.append("</maxHistory>\n");
				}

				// file triggering
				// if max size is defined
				if (!StringUtils.isEmpty(a.getMaxFileSize())) {
					sb.append("\t\t\t<timeBasedFileNamingAndTriggeringPolicy class=\"ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP\">\n");
					sb.append("\t\t\t\t<maxFileSize>");
					sb.append(a.getMaxFileSize());
					sb.append("</maxFileSize>\n");
					sb.append("\t\t\t</timeBasedFileNamingAndTriggeringPolicy>\n");
				}
				sb.append("\t\t</rollingPolicy>");
				sb.append("\t\t<encoder>\n");
				sb.append("\t\t\t<pattern>");
				sb.append(a.getPattern());
				sb.append("</pattern>\n");
				sb.append("\t\t</encoder>\n");
				sb.append("\t</appender>\n");
			}

			//define root appender if defined
			if (!StringUtils.isEmpty(config.getRootLoggerAppender())) {
				sb.append("\t<root level=\">");
				sb.append(config.getRootLoggerLevel());
				sb.append("\">\n");
				sb.append("\t\t<appender-ref ref=\"");
				sb.append(config.getRootLoggerAppender());
				sb.append("\" />\n");
				sb.append("\t</root>\n");
			}
		}

		//Generate class based loggers
		for (ClassLoggerConfigurationType logger : config.getClassLogger()) {
			sb.append("\t<logger name=\"");
			sb.append(logger.getPackage());
			sb.append("\" level=\"");
			sb.append(logger.getLevel());
			sb.append("\">\n");
			//if logger specific appender is defined
			if ( null != logger.getAppender() && !logger.getAppender().isEmpty()) {
				for (String appenderName: logger.getAppender()) {
					sb.append("\t\t<appender-ref ref=\"");
					sb.append(appenderName);
					sb.append("\"/>");
				}
			}
			sb.append("\t</logger>\n");
		}

		if (null != config.getAdvanced()) {
			for (Object item: config.getAdvanced().getContent()) {
				sb.append(item.toString());
				sb.append("\n");
			}
		}
		sb.append("</configuration>");
		return sb.toString();
	}
}
