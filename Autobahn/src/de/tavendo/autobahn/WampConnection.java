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

import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.http.message.BasicNameValuePair;

import android.os.HandlerThread;
import android.util.Log;

import com.fasterxml.jackson.core.type.TypeReference;

import de.tavendo.autobahn.WampMessage.GoodBye;

public class WampConnection extends WebSocketConnection implements Wamp {
	

	private static final boolean DEBUG = true;
	private static final String TAG = WampConnection.class.getName();

	// / The message handler of the background writer.
	protected WampWriter mWriterHandler;

	// / Prefix map for outgoing messages.
	private final PrefixMap mOutgoingPrefixes = new PrefixMap();

	// / RNG for IDs.
	//private final Random mRng = new Random();
	
	// Is Shut down trigger by client.
	private volatile boolean isShutdown;

	// / Set of chars to be used for IDs.
	/*private static final char[] mBase64Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
			.toCharArray();*/

	/**
	 * RPC metadata.
	 */
	public static class CallMeta {

		CallMeta(CallHandler handler, Class<?> resultClass) {
			this.mResultHandler = handler;
			this.mResultClass = resultClass;
			this.mResultTypeRef = null;
		}

		CallMeta(CallHandler handler, TypeReference<?> resultTypeReference) {
			this.mResultHandler = handler;
			this.mResultClass = null;
			this.mResultTypeRef = resultTypeReference;
		}

		// / Call handler to be fired on.
		public CallHandler mResultHandler;

		// / Desired call result type or null.
		public Class<?> mResultClass;

		// / Desired call result type or null.
		public TypeReference<?> mResultTypeRef;
	}

	// / Metadata about issued, but not yet returned RPCs.
	private final ConcurrentHashMap<String, CallMeta> mCalls = new ConcurrentHashMap<String, CallMeta>();

	private final ConcurrentHashMap<String, String> mRequestTopicMap = new ConcurrentHashMap<String, String>();

	/**
	 * Event subscription metadata.
	 */
	public static class SubMeta {

		public String mSubscriptionId;
		
		SubMeta(EventHandler handler, Class<?> resultClass) {
			this.mEventHandler = handler;
			this.mEventClass = resultClass;
			this.mEventTypeRef = null;
		}

		SubMeta(EventHandler handler, TypeReference<?> resultTypeReference) {
			this.mEventHandler = handler;
			this.mEventClass = null;
			this.mEventTypeRef = resultTypeReference;
		}

		// / Event handler to be fired on.
		public EventHandler mEventHandler;

		// / Desired event type or null.
		public Class<?> mEventClass;

		// / Desired event type or null.
		public TypeReference<?> mEventTypeRef;
	}

	/**
	 * Event publish metadata.
	 */
	public static class PubMeta {

		public String mPublicationId;
		
		PubMeta(EventHandler handler, Class<?> resultClass) {
			this.mEventHandler = handler;
			this.mEventClass = resultClass;
			this.mEventTypeRef = null;
		}

		PubMeta(EventHandler handler, TypeReference<?> resultTypeReference) {
			this.mEventHandler = handler;
			this.mEventClass = null;
			this.mEventTypeRef = resultTypeReference;
		}

		// / Event handler to be fired on.
		public EventHandler mEventHandler;

		// / Desired event type or null.
		public Class<?> mEventClass;

		// / Desired event type or null.
		public TypeReference<?> mEventTypeRef;
	}

	// / Metadata about active event subscriptions.
	private final ConcurrentHashMap<String, SubMeta> mSubs = new ConcurrentHashMap<String, SubMeta>();

	// / Metadata about active event subscriptions.
	private final ConcurrentHashMap<String, PubMeta> mPubs = new ConcurrentHashMap<String, PubMeta>();

	// / The session handler provided to connect().
	private Wamp.ConnectionHandler mSessionHandler;

	/**
	 * Create the connection transmitting leg writer.
	 */
	protected void createWriter() {

		mWriterThread = new HandlerThread("AutobahnWriter");
		mWriterThread.start();
		mWriter = new WampWriter(mWriterThread.getLooper(), mMasterHandler,
				mTransportChannel, mOptions);

		if (DEBUG)
			Log.d(TAG, "writer created and started");
	}

	/**
	 * Create the connection receiving leg reader.
	 */
	protected void createReader() {
		mReader = new WampReader(mCalls, mSubs, mPubs,mRequestTopicMap , mMasterHandler,
				mTransportChannel, mOptions, "AutobahnReader");
		mReader.start();

		if (DEBUG)
			Log.d(TAG, "reader created and started");
	}

	/**
	 * Create new random ID. This is used, i.e. for use in RPC calls to
	 * correlate call message with result message.
	 * 
	 * @param len
	 *            Length of ID.
	 * @return New random ID of given length.
	 */
	/*private String newId(int len) {
		char[] buffer = new char[len];
		for (int i = 0; i < len; i++) {
			buffer[i] = mBase64Chars[mRng.nextInt(mBase64Chars.length)];
		}
		long l = Long.valueOf("9007199254740992");
		 l = (long) (Math.random() * l);
		return new String(String.valueOf(wampIdGenerator.generate()));
	}*/

	/**
	 * Create new random ID of default length.
	 * 
	 * @return New random ID of default length.
	 */
	private String newId() {
		return new String(String.valueOf(mIdGenerator.generate()));
	}

	RequestIdGenerator mIdGenerator = new RequestIdGenerator();
	
	private class RequestIdGenerator {
	    private final Random random = new Random();
	    private final long limit = (long)Math.pow(2, 50);

	    public long generate() {
	        return nextLong(this.random, this.limit);
	    }

	    private long nextLong(Random rng, long n) {
	        // error checking and 2^x checking removed for simplicity.
	        long bits, val;
	        do {
	            bits = (rng.nextLong() << 1) >>> 1;
	            val = bits % n;
	        } while (bits - val + (n - 1) < 0L);
	        return val;
	    }
	}
	
	public void connect(String wsUri, Wamp.ConnectionHandler sessionHandler) {

		WampOptions options = new WampOptions();
		options.setReceiveTextMessagesRaw(true);
		options.setMaxMessagePayloadSize(64 * 1024);
		options.setMaxFramePayloadSize(64 * 1024);
		options.setTcpNoDelay(true);

		connect(wsUri, sessionHandler, options, null);
	}

	/**
	 * Connect to server.
	 * 
	 * @param wsUri
	 *            WebSockets server URI.
	 * @param sessionHandler
	 *            The session handler to fire callbacks on.
	 * @param headers
	 *            The headers for connection
	 */
	public void connect(String wsUri, Wamp.ConnectionHandler sessionHandler,
			WampOptions options, List<BasicNameValuePair> headers) {

		mSessionHandler = sessionHandler;

		mCalls.clear();
		mSubs.clear();
		mOutgoingPrefixes.clear();

		try {
			connect(wsUri, new String[] { "wamp.2.json" },
					new WebSocketConnectionHandler() {

						@Override
						public void onOpen() {
							/*
							 * if (mSessionHandler != null) {
							 * mSessionHandler.onOpen();
							 * 
							 * 
							 * } else { if (DEBUG) Log.d(TAG,
							 * "could not call onOpen() .. handler already NULL"
							 * ); }
							 */

							// send hello messsage to establish session.
							WampMessage.Hello hellow = new WampMessage.Hello();
							mWriter.forward(hellow);

						}

						@Override
						public void onClose(int code, String reason) {
							if (mSessionHandler != null) {
								mSessionHandler.onClose(code, reason);
							} else {
								if (DEBUG)
									Log.d(TAG,
											"could not call onClose() .. handler already NULL");
							}
						}

					}, options, headers);

		} catch (WebSocketException e) {

			if (mSessionHandler != null) {
				mSessionHandler.onClose(
						WebSocketConnectionHandler.CLOSE_CANNOT_CONNECT,
						"cannot connect (" + e.toString() + ")");
			} else {
				if (DEBUG)
					Log.d(TAG,
							"could not call onClose() .. handler already NULL");
			}
		}

	}

	public void connect(String wsUri, Wamp.ConnectionHandler sessionHandler,
			List<BasicNameValuePair> headers) {

		WampOptions options = new WampOptions();
		options.setReceiveTextMessagesRaw(true);
		options.setMaxMessagePayloadSize(64 * 1024);
		options.setMaxFramePayloadSize(64 * 1024);
		options.setTcpNoDelay(true);

		connect(wsUri, sessionHandler, options, headers);
	}

	public void connect(String wsUri, Wamp.ConnectionHandler sessionHandler,
			WampOptions options) {

		connect(wsUri, sessionHandler, options, null);
	}
	
	public void shutdown()
	{
		isShutdown = true;
		
		WampMessage.GoodBye goodbye = new GoodBye(new HashMap<String, Object>(), "wamp.error.goodbye_and_out");
		if(mWriter != null)
			mWriter.forward(goodbye);
	}

	/**
	 * Process WAMP messages coming from the background reader.
	 */
	protected void processAppMessage(Object message) {

		if (message instanceof WampMessage.CallResult) {

			WampMessage.CallResult callresult = (WampMessage.CallResult) message;

			if (mCalls.containsKey(callresult.mCallId)) {
				CallMeta meta = mCalls.get(callresult.mCallId);
				if (meta.mResultHandler != null) {
					meta.mResultHandler.onResult(callresult.mResult);
				}
				mCalls.remove(callresult.mCallId);
			}

		} else if (message instanceof WampMessage.CallError) {

			WampMessage.CallError callerror = (WampMessage.CallError) message;

			if (mCalls.containsKey(callerror.mRequestId)) {
				CallMeta meta = mCalls.get(callerror.mRequestId);
				if (meta.mResultHandler != null) {
					String errorDescription = "";
					if(callerror.mArguments != null)
					 errorDescription = callerror.mArguments.toString();
					meta.mResultHandler.onError(callerror.mErrorUri,
							errorDescription);
				}
				mCalls.remove(callerror.mRequestId);
			}
		} else if (message instanceof WampMessage.Event) {

			WampMessage.Event event = (WampMessage.Event) message;

			if (mSubs.containsKey(event.mTopicUri)) {
				SubMeta meta = mSubs.get(event.mTopicUri);
				if (meta != null && meta.mEventHandler != null) {
					meta.mEventHandler.onEvent(event.mTopicUri, event.mEvent,event.mArgumentsKw);
				}
			}
		} else if (message instanceof WampMessage.Abort) {
			WampMessage.Abort abort = (WampMessage.Abort) message;

			if (DEBUG)
				Log.d(TAG, "WAMP session abort by server : " + abort.Reason);

		} else if (message instanceof WampMessage.GoodBye) {

			WampMessage.GoodBye goodbye = (WampMessage.GoodBye) message;

			if (DEBUG)
				Log.d(TAG, "Session close initiated  , reason : "
						+ goodbye.Reason);

			/**
			 * if shutdown initiated by Wamp Server, send a good bye message.
			 */
			if(!isShutdown){
			goodbye.Details = new HashMap<String, Object>();
			goodbye.Reason = "wamp.error.goodbye_and_out";

			mWriter.forward(goodbye);
			}
			else
			{
				// client has request for connection close. and confirmation has been received.
				if(DEBUG) Log.d(TAG,"client has request for connection close. and confirmation has been received.");
				disconnect();
			}
		} else if (message instanceof WampMessage.Subscribed) {

			WampMessage.Subscribed subscribed = (WampMessage.Subscribed) message;

			if (DEBUG)
				Log.d(TAG, " Subscribe confirmation received from WAMP server ");
			String topicUri = mRequestTopicMap.get(subscribed.mSubscribeRequestId);
			if (topicUri != null && mSubs.containsKey(topicUri)) {
				SubMeta meta = mSubs.get(topicUri);
				if (meta != null && meta.mEventHandler != null) {
					meta.mEventHandler.onRequestAccepted(" topic : " + topicUri
							+ " subscribed with subscription id : "
							+ subscribed.mSubscriptionId);
					
					meta.mSubscriptionId = subscribed.mSubscriptionId;
				}
			}

		} else if (message instanceof WampMessage.Published) {

			WampMessage.Published published = (WampMessage.Published) message;

			if (DEBUG)
				Log.d(TAG, " Publish confirmation received from WAMP server ");
			String topicUri = mRequestTopicMap.get(published.mPublicationRequestId);
			if (topicUri != null && mPubs.containsKey(topicUri)) {
				PubMeta meta = mPubs.get(topicUri);
				if (meta != null && meta.mEventHandler != null) {
					meta.mEventHandler.onRequestAccepted(" topic : " + topicUri
							+ " published with publication id : "
							+ published.mPublicationtionId);
				}
				 mPubs.remove(topicUri);
				 mRequestTopicMap.remove(published.mPublicationRequestId);
			}
			
		} else if (message instanceof WampMessage.Welcome) {

			WampMessage.Welcome welcome = (WampMessage.Welcome) message;

			// FIXME: safe session ID / fire session opened hook
			if (DEBUG)
				Log.d(TAG, "WAMP session " + welcome.mSessionId
						+ " established (protocol version "
						+ welcome.mProtocolVersion + ", server "
						+ welcome.mServerIdent + ")");

			if (mSessionHandler != null) {
				mSessionHandler.onOpen();

			} else {
				if (DEBUG)
					Log.d(TAG,
							"could not call onOpen() .. handler already NULL");
			}

		} else {

			if (DEBUG)
				Log.d(TAG,
						"unknown WAMP message in AutobahnConnection.processAppMessage");
		}
	}

	/**
	 * Issue a remote procedure call (RPC).
	 * 
	 * @param procUri
	 *            URI or CURIE of procedure to call.
	 * @param resultMeta
	 *            Call result metadata.
	 * @param arguments
	 *            Call arguments.
	 */
	private void call(String procUri, CallMeta resultMeta, Object... arguments) {

		WampMessage.Call call = new WampMessage.Call(newId(), procUri,
				arguments.length);
		for (int i = 0; i < arguments.length; ++i) {
			call.mArgs[i] = arguments[i];
		}
		mCalls.put(call.mCallId, resultMeta);
		mWriter.forward(call);
	}

	/**
	 * Issue a remote procedure call (RPC). This version should be used with
	 * primitive Java types and simple composite (class) types.
	 * 
	 * @param procUri
	 *            URI or CURIE of procedure to call.
	 * @param resultType
	 *            Type we want the call result to be converted to.
	 * @param resultHandler
	 *            Call handler to process call result or error.
	 * @param arguments
	 *            Call arguments.
	 */
	public void call(String procUri, Class<?> resultType,
			CallHandler resultHandler, Object... arguments) {

		call(procUri, new CallMeta(resultHandler, resultType), arguments);
	}

	/**
	 * Issue a remote procedure call (RPC). This version should be used with
	 * result types which are containers, i.e. List<> or Map<>.
	 * 
	 * @param procUri
	 *            URI or CURIE of procedure to call.
	 * @param resultType
	 *            Type we want the call result to be converted to.
	 * @param resultHandler
	 *            Call handler to process call result or error.
	 * @param arguments
	 *            Call arguments.
	 */
	public void call(String procUri, TypeReference<?> resultType,
			CallHandler resultHandler, Object... arguments) {

		call(procUri, new CallMeta(resultHandler, resultType), arguments);
	}

	/**
	 * Subscribe to topic to receive events for.
	 * 
	 * @param topicUri
	 *            URI or CURIE of topic to subscribe to.
	 * @param meta
	 *            Subscription metadata.
	 */
	private void subscribe(String topicUri, SubMeta meta) {

		String uri = mOutgoingPrefixes.resolveOrPass(topicUri);

		String newid = newId();
		if (!mSubs.containsKey(uri)) {

			mRequestTopicMap.put(newid, uri);

			mSubs.put(uri, meta);

			HashMap<String, Object> dict = new HashMap<String, Object>();
			WampMessage.Subscribe msg = new WampMessage.Subscribe(newid, dict,
					mOutgoingPrefixes.shrink(topicUri));
			mWriter.forward(msg);
		}
	}

	/**
	 * Subscribe to topic to receive events for. This version should be used
	 * with result types which are containers, i.e. List<> or Map<>.
	 * 
	 * @param topicUri
	 *            URI or CURIE of topic to subscribe to.
	 * @param eventType
	 *            The type we want events to be converted to.
	 * @param eventHandler
	 *            The event handler to process received events.
	 */
	public void subscribe(String topicUri, Class<?> eventType,
			EventHandler eventHandler) {

		subscribe(topicUri, new SubMeta(eventHandler, eventType));
	}

	/**
	 * Subscribe to topic to receive events for. This version should be used
	 * with primitive Java types and simple composite (class) types.
	 * 
	 * @param topicUri
	 *            URI or CURIE of topic to subscribe to.
	 * @param eventType
	 *            The type we want events to be converted to.
	 * @param eventHandler
	 *            The event handler to process received events.
	 */
	public void subscribe(String topicUri, TypeReference<?> eventType,
			EventHandler eventHandler) {

		subscribe(topicUri, new SubMeta(eventHandler, eventType));
	}

	/**
	 * Unsubscribe from topic.
	 * 
	 * @param topicUri
	 *            URI or CURIE of topic to unsubscribe from.
	 */
	public void unsubscribe(String topicUri) {

		if (mSubs.containsKey(topicUri)) {

			SubMeta meta = mSubs.get(topicUri);
			
			WampMessage.Unsubscribe msg = new WampMessage.Unsubscribe(newId(),meta.mSubscriptionId);
			mWriter.forward(msg);

			mSubs.remove(topicUri);
			mRequestTopicMap.remove(topicUri);
		}
	}

	/**
	 * Unsubscribe from any subscribed topic.
	 */
	public void unsubscribe() {

		for (String topicUri : mSubs.keySet()) {
			
			SubMeta meta = mSubs.get(topicUri);
			
			WampMessage.Unsubscribe msg = new WampMessage.Unsubscribe(newId(),meta.mSubscriptionId);
			mWriter.forward(msg);

		}
		mSubs.clear();
		mRequestTopicMap.clear();
	}

	/**
	 * Establish a prefix to be used in CURIEs.
	 * 
	 * @param prefix
	 *            The prefix to be used in CURIEs.
	 * @param uri
	 *            The full URI this prefix shall resolve to.
	 */
	public void prefix(String prefix, String uri) {

		String currUri = mOutgoingPrefixes.get(prefix);

		if (currUri == null || !currUri.equals(uri)) {

			mOutgoingPrefixes.set(prefix, uri);

			WampMessage.Prefix msg = new WampMessage.Prefix(prefix, uri);
			mWriter.forward(msg);
		}
	}

	/**
	 * Publish to topic for events.
	 * 
	 * @param topicUri
	 *            URI or CURIE of topic to subscribe to.
	 * @param meta
	 *            Subscription metadata.
	 */
	private void publish(String topicUri,  boolean acknowledge,
			PubMeta meta,Object... arguments) {

		String uri = mOutgoingPrefixes.resolveOrPass(topicUri);

		if (!mPubs.containsKey(uri)) {

			String newId = newId();
			mPubs.put(uri, meta);
			mRequestTopicMap.put(newId, uri);

			HashMap<String, Object> dict = new HashMap<String, Object>();
			dict.put("acknowledge", acknowledge);
			WampMessage.Publish msg = new WampMessage.Publish(newId, dict,
					mOutgoingPrefixes.shrink(topicUri), arguments);
			mWriter.forward(msg);
		}
	}

	/**
	 * Publish an event to a topic.
	 * 
	 * @param topicUri
	 *            URI or CURIE of topic to publish event on.
	 * @param event
	 *            Event to be published.
	 */
	public void publish(String topicUri, Object event) {

		HashMap<String, Object> dict = new HashMap<String, Object>();
		WampMessage.Publish msg = new WampMessage.Publish(newId(), dict,
				mOutgoingPrefixes.shrink(topicUri), event);
		mWriter.forward(msg);
	}

	/**
	 * Publish an event to the specified topic.
	 * 
	 * @param topicUri
	 *            The URI or CURIE of the topic the event is to be published
	 *            for.
	 * @param event
	 *            The event to be published.
	 * @param acknowledge
	 *            The acknowledgment to received on publication or error.
	 */
	public void publish(String topicUri,  boolean acknowledge,
			Class<?> eventType, EventHandler eventHandler,Object... arguments) {

		publish(topicUri,  acknowledge, new PubMeta(eventHandler,
				eventType),arguments);
	}
}
