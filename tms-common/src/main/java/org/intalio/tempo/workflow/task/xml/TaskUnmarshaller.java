/**
 * Copyright (c) 2005-2006 Intalio inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Intalio inc. - initial API and implementation
 *
 * $Id: TaskManagementServicesFacade.java 5440 2006-06-09 08:58:15Z imemruk $
 * $Log:$
 */

package org.intalio.tempo.workflow.task.xml;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;

import javax.xml.namespace.QName;

import org.apache.axiom.om.OMElement;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.impl.values.XmlValueOutOfRangeException;
import org.intalio.tempo.workflow.auth.ACL;
import org.intalio.tempo.workflow.auth.AuthIdentifierSet;
import org.intalio.tempo.workflow.task.Notification;
import org.intalio.tempo.workflow.task.PATask;
import org.intalio.tempo.workflow.task.PIPATask;
import org.intalio.tempo.workflow.task.Task;
import org.intalio.tempo.workflow.task.TaskState;
import org.intalio.tempo.workflow.task.attachments.Attachment;
import org.intalio.tempo.workflow.task.attachments.AttachmentMetadata;
import org.intalio.tempo.workflow.task.traits.IChainableTask;
import org.intalio.tempo.workflow.task.traits.ICompleteReportingTask;
import org.intalio.tempo.workflow.task.traits.IProcessBoundTask;
import org.intalio.tempo.workflow.task.traits.ITaskWithAttachments;
import org.intalio.tempo.workflow.task.traits.ITaskWithInput;
import org.intalio.tempo.workflow.task.traits.ITaskWithOutput;
import org.intalio.tempo.workflow.task.traits.ITaskWithState;
import org.intalio.tempo.workflow.util.RequiredArgumentException;
import org.intalio.tempo.workflow.util.xml.InvalidInputFormatException;
import org.intalio.tempo.workflow.util.xml.OMDOMConvertor;
import org.intalio.tempo.workflow.util.xml.XmlBeanUnmarshaller;
import org.intalio.tempo.workflow.util.xml.XsdDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.intalio.bpms.workflow.taskManagementServices20051109.TaskMetadata;

public class TaskUnmarshaller extends XmlBeanUnmarshaller {

	@SuppressWarnings("unused")
	private static final Logger _logger = LoggerFactory.getLogger(TaskUnmarshaller.class);

	public TaskUnmarshaller() {
		super(TaskXMLConstants.TASK_NAMESPACE,
				TaskXMLConstants.TASK_NAMESPACE_PREFIX);
	}

	// for compatibility usage
	public Task unmarshalTaskFromMetadata(OMElement rootElement)
			throws InvalidInputFormatException {
		try {
			XmlObject xmlObject = XmlObject.Factory.parse(rootElement.getXMLStreamReader());
			XmlCursor xmlCursor = xmlObject.newCursor();
			xmlCursor.toStartDoc();
			xmlCursor.toNextToken();
			TaskMetadata taskMetadata =  com.intalio.bpms.workflow.taskManagementServices20051109.Task.Factory.newInstance().addNewMetadata();
			taskMetadata.set(xmlCursor.getObject());
			return unmarshalTaskFromMetadata(taskMetadata);
		} catch (XmlException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	private Task unmarshalTaskFromMetadata(TaskMetadata taskMetadata)
			throws XmlValueOutOfRangeException {
		if (taskMetadata == null) {
			throw new RequiredArgumentException("rootElement");
		}
		checkNS(taskMetadata);

		String taskID = taskMetadata.getTaskId();
		if (taskID == null) {
			throw new InvalidInputFormatException("No task id specified");
		}
		String taskStateStr = taskMetadata.getTaskState();
		String taskTypeStr = taskMetadata.getTaskType();
		String description = taskMetadata.getDescription();
		String processID = taskMetadata.getProcessId();
		String creationDateStr = null;

		try {
			Calendar cal = taskMetadata.getCreationDate();
			if (cal != null) {
				creationDateStr = cal.toString();

			}
		} catch (Exception e) {
			// TODO need to confirm how to deal with it
		}

		AuthIdentifierSet userOwners = new AuthIdentifierSet(taskMetadata
				.getUserOwnerArray());
		AuthIdentifierSet roleOwners = new AuthIdentifierSet(taskMetadata
				.getRoleOwnerArray());

		ACL claim = readACL(taskMetadata, "claim");
		ACL revoke = readACL(taskMetadata, "revoke");
		ACL save = readACL(taskMetadata, "save");
		ACL complete = readACL(taskMetadata, "complete");

		String formURLStr = taskMetadata.getFormUrl();
		URI formURL = null;
		try {
			if (formURLStr != null) {
				formURL = new URI(formURLStr);
			} else {
				throw new InvalidInputFormatException("No formURL specified");
			}
		} catch (URISyntaxException e) {
			throw new InvalidInputFormatException(e);
		}

		String failureCode = taskMetadata.getFailureCode();
		String failureReason = taskMetadata.getFailureReason();
		expectElementValue(taskMetadata, "userProcessEndpoint"); // TODO:
		// these
		// violate
		// the WSDL!
		// do
		// something
		expectElementValue(taskMetadata, "userProcessNamespaceURI");
		String completeSOAPAction = taskMetadata
				.getUserProcessCompleteSOAPAction();
		com.intalio.bpms.workflow.taskManagementServices20051109.TaskMetadata.Attachments attachmentsElement = taskMetadata
				.getAttachments();
		String isChainedBeforeStr = expectElementValue(taskMetadata,
				"isChainedBefore");
		String previousTaskID = expectElementValue(taskMetadata,
				"previousTaskId");

		Class<? extends Task> taskClass = TaskTypeMapper
				.getTypeClassByName(taskTypeStr);
		Task resultTask = null;
		TaskState taskState = null;

		if (!ITaskWithState.class.isAssignableFrom(taskClass)) {
			forbidParameter(taskStateStr, "task state");
			forbidParameter(failureCode, "failure code");
			forbidParameter(failureReason, "failure reason");
		} else {
			try {
				taskState = (taskStateStr == null) ? TaskState.READY
						: TaskState.valueOf(taskStateStr.toUpperCase());
			} catch (IllegalArgumentException e) {
				throw new InvalidInputFormatException("Unknown task state: '"
						+ taskStateStr + "'");
			}
		}
		if (IProcessBoundTask.class.isAssignableFrom(taskClass)) {
			requireParameter(processID, "processID");
		} else {
			forbidParameter(processID, "processID");
		}
		if (ICompleteReportingTask.class.isAssignableFrom(taskClass)) {
			requireParameter(completeSOAPAction, "completion SOAPAction");
		} else {
			forbidParameter(completeSOAPAction, "completion SOAPAction");
		}
		if (!ITaskWithAttachments.class.isAssignableFrom(taskClass)) {
			forbidParameter(attachmentsElement, "task attachment(s)");
		}
		if (!IChainableTask.class.isAssignableFrom(taskClass)) {
			forbidParameter(isChainedBeforeStr, "is-chained-before flag");
			forbidParameter(previousTaskID, "previous chained task ID");
		}

		// TODO: the following is a loathsome if-cascade:
		if (taskClass.equals(PIPATask.class)) {
			resultTask = new PIPATask(taskID, formURL, null, null, null);
		} else if (taskClass.equals(PATask.class)) {
			resultTask = new PATask(taskID, formURL, processID,
					completeSOAPAction, null);
		} else if (taskClass.equals(Notification.class)) {
			resultTask = new Notification(taskID, formURL, null);
		} else {
			throw new RuntimeException("Unknown task class: " + taskClass);
		}

		resultTask.getUserOwners().addAll(userOwners);
		resultTask.getRoleOwners().addAll(roleOwners);

		resultTask.setDescription(description == null ? "" : description);
		_logger.debug("Setting date from " + creationDateStr);
		if ((creationDateStr != null) && (creationDateStr.trim().length() > 0)) {
			resultTask.setCreationDate(new XsdDateTime(creationDateStr)
					.getTime());
		} else {
			resultTask.setCreationDate(new Date());
		}
		_logger.debug("Date set to " + resultTask.getCreationDate());

		authorize(resultTask, "claim", claim);
		authorize(resultTask, "revoke", revoke);
		authorize(resultTask, "save", save);
		authorize(resultTask, "complete", complete);

		if (ITaskWithState.class.isAssignableFrom(taskClass)) {
			ITaskWithState taskWithState = (ITaskWithState) resultTask;
			taskWithState.setState(taskState);
			if (taskWithState.getState().equals(TaskState.FAILED)) {
				requireParameter(failureCode, "failure code");

				taskWithState.setFailureCode(failureCode);
				taskWithState.setFailureReason(failureReason == null ? ""
						: failureReason);
			} else {
				forbidParameter(failureCode, "failure code");
				forbidParameter(failureReason, "failure reason");
			}
		}
		if (IProcessBoundTask.class.isAssignableFrom(taskClass)) {
			((IProcessBoundTask) resultTask).setProcessID(processID);
		}
		if (ICompleteReportingTask.class.isAssignableFrom(taskClass)) {
			((ICompleteReportingTask) resultTask)
					.setCompleteSOAPAction(completeSOAPAction);
		}
		if (ITaskWithAttachments.class.isAssignableFrom(taskClass)) {
			ITaskWithAttachments taskWithAttachments = (ITaskWithAttachments) resultTask;
			if (attachmentsElement != null) {

				for (int i = 0; i < attachmentsElement.sizeOfAttachmentArray(); i++) {
					com.intalio.bpms.workflow.taskManagementServices20051109.Attachment attachmentElement = attachmentsElement
							.getAttachmentArray(i);
					com.intalio.bpms.workflow.taskManagementServices20051109.AttachmentMetadata attachmentMetadata = attachmentElement
							.getAttachmentMetadata();
					if (attachmentElement != null) {
						AttachmentMetadata metadata = new AttachmentMetadata();
						String mimeType = attachmentMetadata.getMimeType();
						if (mimeType != null) {
							metadata.setMimeType(mimeType);
						}
						String fileName = attachmentMetadata.getFileName();
						if (fileName != null) {
							metadata.setFileName(fileName);
						}
						String title = attachmentMetadata.getTitle();
						if (title != null) {
							metadata.setTitle(title);
						}
						String description2 = attachmentMetadata
								.getDescription();
						if (description2 != null) {
							metadata.setDescription(description2);
						}
						try {
							Calendar cal = attachmentMetadata.getCreationDate();
							if ((cal != null)) {
								metadata.setCreationDate(new XsdDateTime(cal
										.toString()).getTime());
							}
						} catch (Exception e) {
							// TODO
						}

						String payloadURLStr = attachmentElement
								.getPayloadUrl();
						URL payloadURL;
						try {
							payloadURL = new URL(payloadURLStr);
						} catch (MalformedURLException e) {
							throw new InvalidInputFormatException(e);
						}

						Attachment attachment = new Attachment(metadata,
								payloadURL);
						taskWithAttachments.addAttachment(attachment);
					}
				}
			}
		}
		if (IChainableTask.class.isAssignableFrom(taskClass)) {
			IChainableTask chainableTask = (IChainableTask) resultTask;
			if (isChainedBeforeStr != null) {
				if ("1".equals(isChainedBeforeStr)
						|| "true".equals(isChainedBeforeStr)) {
					if (previousTaskID == null) {
						throw new InvalidInputFormatException(
								"tms:previousTaskId is required "
										+ "if tms:isChainedBefore is true");
					}
					chainableTask.setPreviousTaskID(previousTaskID);
					chainableTask.setChainedBefore(true);
				} else {
					if ((previousTaskID != null)
							&& (!"".equals(previousTaskID))) {
						throw new InvalidInputFormatException(
								"tms:previousTaskId must be empty or not present "
										+ "if tms:isChainedBefore is false");
					}
				}
			} else {
				if (previousTaskID != null) {
					throw new InvalidInputFormatException(
							"tms:isChainedBefore is required "
									+ "if tms:previousTaskId is present");
				}
			}
		}

		return resultTask;
	}

	private void authorize(Task resultTask, String action, ACL acl) {
		for (String user : acl.getUsers()) {
			resultTask.authorizeActionForUser(action, user);
		}
		for (String role : acl.getRoles()) {
			resultTask.authorizeActionForRole(action, role);
		}
	}

	private ACL readACL(XmlObject root, String action) {
		ACL acl = new ACL();
		XmlObject el = expectElement(root, action + "Action");
		if (el != null) {
			acl.setUsers(expectAuthIdentifiers(el, "user"));
			acl.setRoles(expectAuthIdentifiers(el, "role"));
		}
		return acl;
	}

	private void checkTaskPayload(XmlObject containerElement)
			throws InvalidInputFormatException {
		if (containerElement == null) {
			throw new RequiredArgumentException("containerElement");
		}
		XmlCursor payloadCursor = containerElement.newCursor();

		if (!payloadCursor.toFirstChild()) {
			throw new InvalidInputFormatException(
					"Payload container element must contain exactly one child element");
		}
		if (payloadCursor.toNextSibling()) {
			throw new InvalidInputFormatException(
					"Task payload must consist of exactly one element.");
		}
		payloadCursor.dispose();
	}

	public XmlObject unmarshalTaskInput(XmlObject inputContainerElement)
			throws InvalidInputFormatException {
		checkTaskPayload(inputContainerElement);
		return inputContainerElement;

	}

	// for compatibility usage
	private Document unmarshalTaskPayload(OMElement containerElement)
			throws InvalidInputFormatException {
		if (containerElement == null) {
			throw new RequiredArgumentException("containerElement");
		}
		Iterator<OMElement> it = containerElement.getChildElements();
		if (!it.hasNext()) {
			throw new InvalidInputFormatException(
					"Payload container element must contain exactly one child element");
		}
		Document result = null;
		OMElement firstPayloadElement = it.next();
		if (it.hasNext()) {
			throw new InvalidInputFormatException(
					"Task payload must consist of exactly one element.");
		} else {
			result = OMDOMConvertor.convertOMToDOM(firstPayloadElement);
		}
		return result;
	}

	// for compatibility usage
	public Document unmarshalTaskOutput(OMElement outputContainerElement)
			throws InvalidInputFormatException {
		return unmarshalTaskPayload(outputContainerElement);
	}

	public XmlObject unmarshalTaskOutput(XmlObject outputContainerElement)
			throws InvalidInputFormatException {
		checkTaskPayload(outputContainerElement);
		return outputContainerElement;
	}

	public Task unmarshalFullTask(OMElement rootElement)
			throws InvalidInputFormatException {
		try {
			XmlObject xmlObject = XmlObject.Factory.parse(rootElement
					.getXMLStreamReader());
			return unmarshalFullTask(xmlObject);
		} catch (XmlException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	private Task unmarshalFullTask(XmlObject rootElement)
			throws InvalidInputFormatException {
		if (rootElement == null) {
			throw new RequiredArgumentException("rootElement");
		}
		Task resultTask = null;

		requireElement(rootElement, "metadata");

		com.intalio.bpms.workflow.taskManagementServices20051109.Task taskElement = com.intalio.bpms.workflow.taskManagementServices20051109.Task.Factory
				.newInstance();

		TaskMetadata metadataElement = taskElement.addNewMetadata();
		metadataElement.set(expectElement(rootElement, "metadata"));

		com.intalio.bpms.workflow.taskManagementServices20051109.Task.Input inputElement = null;
		XmlObject xmlInput = expectElement(rootElement, "input");
		if (xmlInput != null) {
			inputElement = taskElement.addNewInput();
			inputElement.set(xmlInput);
		}

		com.intalio.bpms.workflow.taskManagementServices20051109.Task.Output outputElement = null;
		XmlObject xmlOutput = expectElement(rootElement, "output");
		if (xmlOutput != null) {
			outputElement = taskElement.addNewOutput();
			outputElement.set(xmlOutput);
		}

		resultTask = unmarshalTaskFromMetadata(metadataElement);
		if (resultTask instanceof ITaskWithInput) {
			requireParameter(inputElement, "task input");
			XmlObject input = unmarshalTaskInput(inputElement);
			((ITaskWithInput) resultTask).setInput(input);
		} else {
			forbidParameter(inputElement, "task input");
		}
		if ((resultTask instanceof ITaskWithOutput) && outputElement != null) {
			requireParameter(outputElement, "task output");
			XmlObject output = unmarshalTaskOutput(outputElement);
			((ITaskWithOutput) resultTask).setOutput(output);
		} else {
			forbidParameter(outputElement, "task output");
		}

		return resultTask;
	}

	private void checkNS(XmlObject containerElement)
			throws InvalidInputFormatException {
		if (containerElement == null) {
			throw new RequiredArgumentException("containerElement");
		}
		XmlCursor payloadCursor = containerElement.newCursor();

		if (!payloadCursor.toFirstChild()) {
			throw new InvalidInputFormatException("No taskmetadata element");
		}
		QName qName = payloadCursor.getName();
		if (qName == null || qName.getNamespaceURI() == null
				|| qName.getNamespaceURI().trim().length() == 0) {
			throw new InvalidInputFormatException("No namespace defined");
		}
		payloadCursor.dispose();
	}
}
