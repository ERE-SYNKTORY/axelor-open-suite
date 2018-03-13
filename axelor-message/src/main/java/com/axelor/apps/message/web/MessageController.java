/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.message.web;

import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.repo.MessageRepository;
import com.axelor.apps.message.exception.IExceptionMessage;
import com.axelor.apps.message.service.MessageService;
import com.axelor.apps.message.service.MessageServiceImpl;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import groovy.lang.Tuple2;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.criteria.CriteriaBuilder;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class MessageController {

	private final Logger log = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	@Inject
	private MessageRepository messageRepo;
	
	@Inject
	private MessageService messageService;

	public void sendMessage(ActionRequest request, ActionResponse response) {
		Message message = request.getContext().asType(Message.class);

		try {
			message = messageService.sendMessage(messageRepo.find(message.getId()));
			response.setReload(true);

			if ( message.getStatusSelect() == MessageRepository.STATUS_SENT ) {

				if ( message.getSentByEmail() ) { response.setFlash( I18n.get( IExceptionMessage.MESSAGE_4 ) ); }
				else { response.setFlash( I18n.get( IExceptionMessage.MESSAGE_5 ) ); }

			} else  { response.setFlash( I18n.get( IExceptionMessage.MESSAGE_6 ) );	}
		} catch (AxelorException e) {
			TraceBackService.trace(response, e);
		}
	}

	@SuppressWarnings("unchecked")
	public void sendMessages(ActionRequest request, ActionResponse response) {
		List<Integer> idList = (List<Integer>) request.getContext().get("_ids");
		try {
			if (idList == null) {throw new AxelorException(IException.MISSING_FIELD, I18n.get(IExceptionMessage.MESSAGE_MISSING_SELECTED_MESSAGES));}
			List<Message> messages = messageService.findMessages(idList);
			int error = applyFunctionOnMessages(messages, message -> {
				try {
					messageService.sendMessage(message);
					return true;
				} catch (Exception e) {
					TraceBackService.trace(e);
					return false;
				}
			});
			response.setFlash(String.format(I18n.get(IExceptionMessage.MESSAGES_REGENERATED),
					messages.size() - error, error));
			response.setReload(true);
		} catch (AxelorException e) {
			TraceBackService.trace(response, e);
		}
	}

	@SuppressWarnings("unchecked")
	public void regenerateMessages(ActionRequest request, ActionResponse response) {
		List<Integer> idList = (List<Integer>) request.getContext().get("_ids");
		try {
			if (idList == null) {throw new AxelorException(IException.MISSING_FIELD, I18n.get(IExceptionMessage.MESSAGE_MISSING_SELECTED_MESSAGES));}
			List<Message> messages = messageService.findMessages(idList);
			int error = applyFunctionOnMessages(messages, message -> {
				try {
					messageService.regenerateMessage(message);
					return true;
				} catch (Exception e) {
					TraceBackService.trace(e);
					return false;
				}
			});
			response.setFlash(String.format(I18n.get(IExceptionMessage.MESSAGES_REGENERATED),
					messages.size() - error, error));
			response.setReload(true);
		} catch (AxelorException e) {
		    TraceBackService.trace(response, e);
		}
	}

	/**
	 * Find all messages by id from idMessageList, apply function to each
	 * messages and return a tuple with number of success and error.
	 * @param messages The list of messages id.
	 * @param function The function to apply to each messages.
	 * @return Error count.
	 */
	private int applyFunctionOnMessages(List<Message> messages, Function<Message, Boolean> function) {
		Preconditions.checkNotNull(messages);
		Preconditions.checkNotNull(function);
		return MessageServiceImpl.apply(messages, function);
	}
}
