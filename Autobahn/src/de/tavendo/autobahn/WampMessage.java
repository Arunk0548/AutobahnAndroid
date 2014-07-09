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

/**
 * The master thread and the background reader/writer threads communicate using
 * these messages for Autobahn WAMP connections.
 */
public class WampMessage {

	
	public static final int MESSAGE_TYPE_HELLO = 1;
	public static final int MESSAGE_TYPE_WELCOME = 2;
	public static final int MESSAGE_TYPE_ABORT = 3;
	public static final int MESSAGE_TYPE_CHALLENGE = 4;
	public static final int MESSAGE_TYPE_AUTHENTICATE = 5;
	public static final int MESSAGE_TYPE_GOODBYE = 6;
	public static final int MESSAGE_TYPE_HEARTBEAT = 7;
	public static final int MESSAGE_TYPE_ERROR = 8;
	public static final int MESSAGE_TYPE_PUBLISH = 16;
	public static final int MESSAGE_TYPE_PUBLISHED = 17;
	public static final int MESSAGE_TYPE_SUBSCRIBE = 32;
	public static final int MESSAGE_TYPE_SUBSCRIBED = 33;
	public static final int MESSAGE_TYPE_UNSUBSCRIBE = 34;
	public static final int MESSAGE_TYPE_UNSUBSCRIBED = 35;
	public static final int MESSAGE_TYPE_EVENT = 36;
	public static final int MESSAGE_TYPE_CALL = 48;
	public static final int MESSAGE_TYPE_CANCEL = 49;
	public static final int MESSAGE_TYPE_RESULT = 50;
	public static final int MESSAGE_TYPE_REGISTER = 64;
	public static final int MESSAGE_TYPE_REGISTERED = 65;
	public static final int MESSAGE_TYPE_UNREGISTER = 66;
	public static final int MESSAGE_TYPE_UNREGISTERED = 67;
	public static final int MESSAGE_TYPE_INVOCATION = 68;
	public static final int MESSAGE_TYPE_INTERRUPT = 69;
	public static final int MESSAGE_TYPE_YIELD = 70;



	/*
	 * public static final int MESSAGE_TYPE_WELCOME = 0; public static final int
	 * MESSAGE_TYPE_PREFIX = 1; public static final int MESSAGE_TYPE_CALL = 2;
	 * public static final int MESSAGE_TYPE_CALL_RESULT = 3; public static final
	 * int MESSAGE_TYPE_CALL_ERROR = 4; public static final int
	 * MESSAGE_TYPE_SUBSCRIBE = 5; public static final int
	 * MESSAGE_TYPE_UNSUBSCRIBE = 6; public static final int
	 * MESSAGE_TYPE_PUBLISH = 7; public static final int MESSAGE_TYPE_EVENT = 8;
	 */

	// / Base message class.
	public static class Message extends WebSocketMessage.Message {

	}

	
	public static class GoodBye extends Message{
		public HashMap<String, Object> Details; 
		public String Reason;
		
		public GoodBye(HashMap<String, Object> dict, String reason)
		{
			Details =dict;
			Reason = reason;
		}
		
	}
	
	public static class Abort extends Message{
		public HashMap<String, Object> Details; 
		public String Reason;
		
		public Abort(HashMap<String, Object> dict, String reason)
		{
			Details =dict;
			Reason = reason;
		}
		
	}
	
	/**
	 * Client to Server to initiate session.
	 * @author arun.k
	 *
	 */
	public static class Hello extends Message {
		
		public Details details = new Details();
		public String realm = "realm1";
		
		public class Details
		{
			public String[] authmethods = {"anonymous","cookie"};
			public WAMP_FEATURES roles = new WampMessage.WAMP_FEATURES();
			
			
		}
		
		
	}
	
	public static class WAMP_FEATURES
	{
		public Caller caller = new Caller();
		public Callee callee = new Callee();
		public Publisher publisher = new Publisher();
		public Subscriber subscriber = new Subscriber();
	}
	
	public static class Caller{
		public HashMap<String, Boolean> features;
		
		public Caller()
		{
			features = new HashMap<String, Boolean>();
			
			features.put("caller_identification", true);
			features.put("progressive_call_results", true);
		}
	}
	
	public static class Callee{
		public HashMap<String, Boolean> features;
		
		public Callee()
		{
			features = new HashMap<String, Boolean>();
			
			features.put("progressive_call_results", true);
		}
	}
	
	public static class Publisher{
		public HashMap<String, Boolean> features;
		
		public Publisher()
		{
			features = new HashMap<String, Boolean>();
			
			features.put("subscriber_blackwhite_listing", true);
			features.put("publisher_exclusion", false);
			features.put("publisher_identification", true);
		}
	}
	
	public static class Subscriber{
		public HashMap<String, Boolean> features;
		
		public Subscriber()
		{
			features = new HashMap<String, Boolean>();
			
			features.put("publisher_identification", true);
		}
	}
	
	/**
	 * RPC request message. Client-to-server message.
	 */
	public static class Call extends Message {
		public String mCallId;
		public HashMap<String, Object> mOptions;
		public String mProcUri;
		public Object[] mArgs;
		public HashMap<String, Object> mArgumentsKw;

		public Call(String callId, String procUri, int argCount) {
			mCallId = callId;
			mOptions = new HashMap<String, Object>();
			mProcUri = procUri;
			mArgs = new Object[argCount];
			mArgumentsKw = new HashMap<String, Object>();
		}
	}

	/**
	 * RPC success response message. Server-to-client message.
	 */
	public static class CallResult extends Message {
		public String mCallId;
		public HashMap<String, Object> mOptions;
		public Object mResult;
		public HashMap<String, Object> mArgumentsKw;

		public CallResult(String callId, HashMap<String, Object> options, Object result, HashMap<String, Object> argumentsKw) {
			mCallId = callId;
			mOptions = options;
			mResult = result;
			mArgumentsKw= argumentsKw;
		}
	}

	public static class Error extends Message {
		public int mRequestType;
		public String mRequestId;
		public Object mDetails;
		public String mErrorUri;
		public Object mArguments;
		public Object mArgumentsKw;

		
		public Error(int requestType, String requestID, Object details, String errorUri, Object argument, Object argumentsKw)
		{
			mRequestType = requestType;
			mRequestId = requestID;
			mDetails = details;
			mErrorUri = errorUri;
			mArguments = argument;
			mArgumentsKw = argumentsKw;
		}
		
	}
	/**
	 * RPC failure response message. Server-to-client message.
	 */
	public static class CallError extends Error {

		public CallError(int requestType, String requestID, Object details,
				String errorUri,  Object argument, Object argumentsKw) {
			super(requestType, requestID, details, errorUri, argument, argumentsKw);
			
		}
				

	}

	/**
	 * Define Welcome message. Server-to-client message.
	 */
	public static class Welcome extends Message {
		public String mSessionId;
		public int mProtocolVersion;
		public String mServerIdent;
		public String mAuthid ;
		public String mAuthrole;
		public String mAuthmethod;
		public String mAuthprovider;
		public WampMessage.WAMP_FEATURES mRoles;
		

		public Welcome(String sessionId, int protocolVersion, String serverIdent) {
			mSessionId = sessionId;
			mProtocolVersion = protocolVersion;
			mServerIdent = serverIdent;
		}
		
		public Welcome(String sessionId, String authid, String authrole, String authmethod, String authprovider,WampMessage.WAMP_FEATURES roles) {
			mSessionId = sessionId;
			mAuthid = authid;
			mAuthrole = authrole;
			mAuthmethod = authmethod;
			mAuthprovider = authprovider;
			mRoles = roles;
		}
	}

	/**
	 * Define CURIE message. Server-to-client and client-to-server message.
	 */
	public static class Prefix extends Message {
		public String mPrefix;
		public String mUri;

		public Prefix(String prefix, String uri) {
			mPrefix = prefix;
			mUri = uri;
		}
	}

	/**
	 * Publish to topic URI request message. Client-to-server message.
	 */
	public static class Publish extends Message {
		public String mRequestId;
		public HashMap<String, Object> mOptions;
		public String mTopicUri;
		public Object[] mArgs;
		public HashMap<String, Object> mArgumentsKw;

		private Publish(HashMap<String, Object> options)
		{
			mOptions = options;
			if(mOptions == null )
				mOptions = new HashMap<String, Object>();
		}
		
		public Publish(String reqestId,HashMap<String, Object> options, String topicUri) {
			this(options);
			mRequestId = reqestId;
			
			mTopicUri = topicUri;		
		}
		
		public Publish(String reqestId,HashMap<String, Object> options, String topicUri, Object... eventsargument) {
			this(options);
			mRequestId = reqestId;
			
			mTopicUri = topicUri;
			
			if(eventsargument != null){
				mArgs = new Object[eventsargument.length];
			for(int i=0;i<eventsargument.length ;i++)
				mArgs[i] = eventsargument[i];
			}
			
		}
		
		public Publish(String reqestId,HashMap<String, Object> options, String topicUri,HashMap<String, Object> arguments,Object... eventsargument) {
			this(options);
			mRequestId = reqestId;
	
			mTopicUri = topicUri;
			if(eventsargument != null){
				mArgs = new Object[eventsargument.length];
			for(int i=0;i<eventsargument.length ;i++)
				mArgs[i] = eventsargument[i];
			}
			mArgumentsKw = arguments;
		}
	}
	
	
	public static class Published extends Message {
		public String mPublicationRequestId;
		public String mPublicationtionId;
		
		public Published(String requestId,String publicationId) {
			mPublicationRequestId = requestId;
			mPublicationtionId = publicationId;
			
		}
	}
	
	/**
	 * When the request for publication cannot be fulfilled by the Broker,
	 *  the Broker sends back an ERROR message to the publisher
	 *  
	 *  Broker - to - client
	 * @author arun.k
	 *
	 */
	public static class PublishError extends Error{

		public PublishError(int requestType, String requestID, Object details,
				String errorUri, Object argument, Object argumentsKw) {
			super(requestType, requestID, details, errorUri, argument, argumentsKw);
			
		}
		
	}

	/**
	 * Subscribe to topic URI request message. Client-to-server message.
	 */
	public static class Subscribe extends Message {
		public String mRequestId;
		public HashMap<String, Object> mOptions;
		public String mTopicUri;
		
		public Subscribe(String requestId,HashMap<String, Object> dict,String topicUri) {
			mRequestId = requestId;
			mOptions = dict;
			mTopicUri = topicUri;
			
		}
	}
	
	/**
	 * Confirmation for subscribe to topic URI request message. Server - to - client.
	 * @author arun.k
	 *
	 */
	public static class Subscribed extends Message {
		public String mSubscribeRequestId;
		public String mSubscriptionId;
		
		public Subscribed(String requestId,String subscriptionId) {
			mSubscribeRequestId = requestId;
			mSubscriptionId = subscriptionId;
			
		}
	}
	
	/**
	 * When the request for subscription cannot be fulfilled by the Broker, 
	 * the Broker sends back an ERROR message to the Subscriber
	 * 
	 * Server to Client
	 * @author arun.k
	 *
	 */
	public static class SubscribeError extends Error
	{

		public SubscribeError(int requestType, String requestID,
				Object details, String errorUri,  Object argument, Object argumentsKw) {
			super(requestType, requestID, details, errorUri, argument, argumentsKw);
			
		}
		
	}

	/**
	 * Unsubscribe from topic URI request message. Client-to-server message.
	 */
	public static class Unsubscribe extends Message {
		public String mRequestId;
		public String mSubscriptionId;

		public Unsubscribe(String requestid, String subscriptionid) {
			mRequestId = requestid;
			mSubscriptionId = subscriptionid;
		}
	}
	
	/**
	 * Unsubscribed from topic URI request message. Server-to-Client message.
	 */
	public static class Unsubscribed extends Message {
		public String mUnsubscribedRequestId;


		public Unsubscribed(String unsubscribedRequestId) {
			mUnsubscribedRequestId = unsubscribedRequestId;
		}
	}
	
	/**
	 * When the request for unsubscribe cannot be fulfilled by the Broker,
	 *  the Broker sends back an ERROR message to the unsubscriber.
	 *  
	 *  Server to Client.
	 * @author arun.k
	 *
	 */
	public static class UnsubscribeError extends Error
	{

		public UnsubscribeError(int requestType, String requestID,
				Object details, String errorUri,  Object argument, Object argumentsKw) {
			super(requestType, requestID, details, errorUri, argument, argumentsKw);
		
		}
		
	}

	/**
	 * Event on topic URI message. Server-to-client message.
	 */
	public static class Event extends Message {
		public String mSubscriptionId;
		public String mPublicationId;
		public HashMap<String, Object> mOptions;
		public Object mEvent;
		public HashMap<String, Object> 	mArgumentsKw;
		public String mTopicUri;

		public Event(String topicUri,String subscriptionid, String publicationid, HashMap<String, Object> Options)
		{
			mTopicUri = topicUri;
			mSubscriptionId = subscriptionid;
			mPublicationId = publicationid;
			mOptions = Options;
		}
		public Event(String topicUri,String subscriptionid, String publicationid, HashMap<String, Object> Options,  Object event) {
			
			mTopicUri = topicUri;
			mSubscriptionId = subscriptionid;
			mPublicationId = publicationid;
			mOptions = Options;
			mEvent = event;
		}
		public Event(String topicUri,String subscriptionid, String publicationid, HashMap<String, Object> Options,  Object event,HashMap<String, Object> argumentskw) {
			
			mTopicUri = topicUri;
			mSubscriptionId = subscriptionid;
			mPublicationId = publicationid;
			mOptions = Options;
			mEvent = event;
			mArgumentsKw = argumentskw;
		}
	}
}
