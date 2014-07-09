/******************************************************************************
 *
 *  Copyright 2011-2012 Tavendo GmbH
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package de.tavendo.autobahn;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;

import android.os.Handler;
import android.util.Log;
import de.tavendo.autobahn.WampConnection.CallMeta;
import de.tavendo.autobahn.WampConnection.PubMeta;
import de.tavendo.autobahn.WampConnection.SubMeta;


/**
 * Autobahn WAMP reader, the receiving leg of a WAMP connection.
 */
public class WampReader extends WebSocketReader {

	private static final boolean DEBUG = true;
	private static final String TAG = WampReader.class.getName();

	
	// / Jackson JSON-to-object mapper.
	private final ObjectMapper mJsonMapper;

	// / Jackson JSON factory from which we create JSON parsers.
	private final JsonFactory mJsonFactory;

	// / Holds reference to call map created on master.
	private final ConcurrentHashMap<String, CallMeta> mCalls;

	// / Holds reference to event subscription map created on master.
	private final ConcurrentHashMap<String, SubMeta> mSubs;

	// / Holds reference to event publication map created on master.
	private final ConcurrentHashMap<String, PubMeta> mPubs;

	// holds reference to request id and respective request topic.
	private final ConcurrentHashMap<String, String> mRequestTopicMap;

	/**
	 * A reader object is created in AutobahnConnection.
	 * 
	 * @param calls
	 *            The call map created on master.
	 * @param subs
	 *            The event subscription map created on master.
	 * @param master
	 *            Message handler of master (used by us to notify the master).
	 * @param socket
	 *            The TCP socket.
	 * @param options
	 *            WebSockets connection options.
	 * @param threadName
	 *            The thread name we announce.
	 */
	public WampReader(ConcurrentHashMap<String, CallMeta> calls,
			ConcurrentHashMap<String, SubMeta> subs,
			ConcurrentHashMap<String, PubMeta> pubs,
			ConcurrentHashMap<String, String> reqtopicmap, Handler master,
			SocketChannel socket, WebSocketOptions options, String threadName) {

		super(master, socket, options, threadName);

		mCalls = calls;
		mSubs = subs;
		mPubs = pubs;
		mRequestTopicMap = reqtopicmap;

		mJsonMapper = new ObjectMapper();
		mJsonMapper
				.configure(
						DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES,
						false);
		mJsonFactory = mJsonMapper.getJsonFactory();

		if (DEBUG)
			Log.d(TAG, "created");
	}

	protected void onTextMessage(String payload) {

		// / \todo make error propagation consistent
		notify(new WebSocketMessage.Error(new WebSocketException(
				"non-raw receive of text message")));
	}

	protected void onBinaryMessage(byte[] payload) {

		// / \todo make error propagation consistent
		notify(new WebSocketMessage.Error(new WebSocketException(
				"received binary message")));
	}

	/**
	 * Unwraps a WAMP message which is a WebSockets text message with JSON
	 * payload conforming to WAMP.
	 */
	protected void onRawTextMessage(byte[] payload) {

		try {

			// create parser on top of raw UTF-8 payload
			JsonParser parser = mJsonFactory.createJsonParser(payload);
			JsonToken token = null;

			// all Autobahn messages are JSON arrays
			if ((token = parser.nextToken()) == JsonToken.START_ARRAY) {

				// message type
				if ((token = parser.nextToken()) == JsonToken.VALUE_NUMBER_INT) {

					int msgType = parser.getIntValue();

					if (msgType == WampMessage.MESSAGE_TYPE_GOODBYE) {
						// details
						token = parser.nextToken();
						HashMap<String, Object> dict = parser
								.readValueAs(new TypeReference<HashMap<String, Object>>() {
								});

						// reason
						token = parser.nextToken();
						String reason = parser.getText();

						notify(new WampMessage.GoodBye(dict, reason));

						token = parser.nextToken();

					} else if (msgType == WampMessage.MESSAGE_TYPE_ABORT) {
						// details
						token = parser.nextToken();
						HashMap<String, Object> dict = parser
								.readValueAs(new TypeReference<HashMap<String, Object>>() {
								});

						// reason
						token = parser.nextToken();
						String reason = parser.getText();

						notify(new WampMessage.Abort(dict, reason));

						token = parser.nextToken();

					} else if (msgType == WampMessage.MESSAGE_TYPE_RESULT) {

						// call ID
						token = parser.nextToken();
						String callId = parser.getText();

						// result
						token = parser.nextToken();
						Object result = null;

						if (mCalls.containsKey(callId)) {

							CallMeta meta = mCalls.get(callId);
							if (meta.mResultClass != null) {
								result = parser.readValueAs(meta.mResultClass);
							} else if (meta.mResultTypeRef != null) {
								result = parser
										.readValueAs(meta.mResultTypeRef);
							} else {
							}
							notify(new WampMessage.CallResult(callId, result));

						} else {

							if (DEBUG)
								Log.d(TAG,
										"WAMP RPC success return for unknown call ID received");
						}

						token = parser.nextToken();

					} else if (msgType == WampMessage.MESSAGE_TYPE_SUBSCRIBED) {
						// subscribe request id
						token = parser.nextToken();
						long requestId = parser.getLongValue();

						// subscription id
						token = parser.nextToken();
						long subscriptionid = parser.getLongValue();

						String topicUri = mRequestTopicMap.get(String
								.valueOf(requestId));

						SubMeta subMeta = null;
						if (topicUri != null)
							subMeta = mSubs.get(topicUri);

						if (subMeta != null)
							notify(new WampMessage.Subscribed(
									String.valueOf(requestId),
									String.valueOf(subscriptionid)));
						else {
							if (DEBUG)
								Log.d(TAG,
										" WAMP subscription success return for unknown subscribe Id received.");
						}

						token = parser.nextToken();
					} else if (msgType == WampMessage.MESSAGE_TYPE_UNSUBSCRIBED) {
						// unsubscribed request id.
						token = parser.nextToken();
						long unsubscribedId = parser.getLongValue();

						if (DEBUG)
							Log.d(TAG,
									" Acknowledge sent by a Broker to a Subscriber to acknowledge unsubscription : "
											+ unsubscribedId);

						token = parser.nextToken();
					} else if (msgType == WampMessage.MESSAGE_TYPE_PUBLISHED) {
						// subscribe request id
						token = parser.nextToken();
						long requestId = parser.getLongValue();

						// subscription id
						token = parser.nextToken();
						long publicationId = parser.getLongValue();

						String topicUri = mRequestTopicMap.get(String
								.valueOf(requestId));

						PubMeta pubMeta = null;
						if (topicUri != null)
							pubMeta = mPubs.get(topicUri);

						if (pubMeta != null)
							notify(new WampMessage.Published(
									String.valueOf(requestId),
									String.valueOf(publicationId)));
						else {
							if (DEBUG)
								Log.d(TAG,
										" WAMP publication success return for unknown publish Id received.");
						}

						token = parser.nextToken();
					} else if (msgType == WampMessage.MESSAGE_TYPE_ERROR) {

						// call ID
						token = parser.nextToken();
						String callId = parser.getText();

						// error URI
						token = parser.nextToken();
						String errorUri = parser.getText();

						// error description
						token = parser.nextToken();
						String errorDesc = parser.getText();

						if (mCalls.containsKey(callId)) {

							notify(new WampMessage.CallError(callId, errorUri,
									errorDesc));

						} else {

							if (DEBUG)
								Log.d(TAG,
										"WAMP RPC error return for unknown call ID received");
						}

						token = parser.nextToken();

					} else if (msgType == WampMessage.MESSAGE_TYPE_EVENT) {

						// SUBSCRIBED.Subscription id
						token = parser.nextToken();
						Long subscriptionId = parser.getLongValue();

						// PUBLISHED.Publication id
						token = parser.nextToken();
						Long publicationId = parser.getLongValue();

						// read option details
						parser.nextToken();
						HashMap<String, Object> options = parser
								.readValueAs(new TypeReference<HashMap<String, Object>>() {
								});

						// event
						token = parser.nextToken();
						Object event = null;

						SubMeta meta = null;
						String mtopicUri = null;
						for (String topicUri : mSubs.keySet()) {

							SubMeta meta0 = mSubs.get(topicUri);

							if (meta0.mSubscriptionId.equals(String
									.valueOf(subscriptionId))) {
								meta = meta0;
								mtopicUri = topicUri;
								break;
							}
						}

						if (meta != null) {
							HashMap<String, Object> argumentkw = null;
							if (token != null && token != JsonToken.END_ARRAY) {
								if (meta.mEventClass != null) {
									event = parser
											.readValueAs(meta.mEventClass);
								} else if (meta.mEventTypeRef != null) {
									event = parser
											.readValueAs(meta.mEventTypeRef);
								} else {
								}

								// read argumentskw list details								
								token = parser.nextToken();
								if (token != null
										&& token != JsonToken.END_ARRAY) {

									argumentkw = parser
											.readValueAs(new TypeReference<HashMap<String, Object>>() {
											});
									token = parser.nextToken();
								}

							}

							notify(new WampMessage.Event(mtopicUri,
									String.valueOf(subscriptionId),
									String.valueOf(publicationId), options,
									event, argumentkw));

						} else {

							if (DEBUG)
								Log.d(TAG,
										"WAMP event for not-subscribed topic received");
						}

					} else if (msgType == WampMessage.MESSAGE_TYPE_WELCOME) {

						if (DEBUG)
							Log.w(TAG, "MESSAGE_TYPE_WELCOME RECEIVED.");
						// session ID
						token = parser.nextToken();
						String sessionId = parser.getText();

						// auth id
						token = parser.nextToken();
						String authid = parser.getText();

						// auth method
						token = parser.nextToken();
						String authmethod = parser.getText();

						// auth role;
						token = parser.nextToken();
						String authrole = parser.getText();

						// roles;
						token = parser.nextToken();
						WampMessage.WAMP_FEATURES roles = parser
								.readValueAs(WampMessage.WAMP_FEATURES.class);

						notify(new WampMessage.Welcome(sessionId, authid,
								authmethod, "", authrole, roles));

						token = parser.nextToken();

					} else {

						// FIXME: invalid WAMP message
						if (DEBUG)
							Log.d(TAG,
									"invalid WAMP message: unrecognized message type");

					}
				} else {

					if (DEBUG)
						Log.d(TAG,
								"invalid WAMP message: missing message type or message type not an integer");
				}

				if (token == JsonToken.END_ARRAY) {

					// nothing to do here

				} else {

					if (DEBUG)
						Log.d(TAG,
								"invalid WAMP message: missing array close or invalid additional args");
				}

			} else {

				if (DEBUG)
					Log.d(TAG, "invalid WAMP message: not an array");
			}
			parser.close();

		} catch (JsonParseException e) {

			if (DEBUG)
				e.printStackTrace();

		} catch (IOException e) {

			if (DEBUG)
				e.printStackTrace();

		}
	}
}
