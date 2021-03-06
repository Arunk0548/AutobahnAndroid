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

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.MappingJsonFactory;

/**
 * Autobahn WAMP writer, the transmitting leg of a WAMP connection.
 * This writer serializes WAMP messages forwarded from the foreground thread
 * (the master) to this object running on the writer thread. WAMP messages are
 * serialized to JSON, and then sent via WebSockets.
 */
public class WampWriter extends WebSocketWriter {

	
   private static final boolean DEBUG = true;
   private static final String TAG = WampWriter.class.getName();

   /**
    * This is the Jackson JSON factory we use to create JSON generators.
    */
   private final JsonFactory mJsonFactory;

   /**
    * This is where we buffer the JSON serialization of WAMP messages.
    */
   private final NoCopyByteArrayOutputStream mPayload;

   /**
    * A writer object is created in AutobahnConnection.
    *
    * @param looper     The message looper associated with the thread running this object.
    * @param master     The message handler associated with the master thread (running AutobahnConnection).
    * @param socket     The TCP socket (channel) the WebSocket connection runs over.
    * @param options    WebSockets options for the underlying WebSockets connection.
    */
   public WampWriter(Looper looper, Handler master, SocketChannel socket,
         WebSocketOptions options) {

      super(looper, master, socket, options);

      mJsonFactory = new MappingJsonFactory();
      mPayload = new NoCopyByteArrayOutputStream();

      if (DEBUG) Log.d(TAG, "created");
   }

   /**
    * Called from WebSocketWriter when it receives a message in it's
    * message loop it does not recognize.
    */
   protected void processAppMessage(Object msg) throws WebSocketException, IOException {

      mPayload.reset();

      // creating a JSON generator is supposed to be a light-weight operation
      JsonGenerator generator = mJsonFactory.createGenerator(mPayload); //mJsonFactory.createJsonGenerator(mPayload);

      try {

         // serialize WAMP messages to JSON: the code here needs to understand
         // any client-to-server WAMP messages forward from the foreground thread

         if (msg instanceof WampMessage.Call) {

            WampMessage.Call call = (WampMessage.Call) msg;

            generator.writeStartArray();
            generator.writeNumber(WampMessage.MESSAGE_TYPE_CALL);
            generator.writeNumber(Long.valueOf(call.mCallId));
            generator.writeObject(call.mOptions);
            generator.writeString(call.mProcUri);
           if( call.mArgs != null) {
               generator.writeObject(call.mArgs);
            }
           if(call.mArgumentsKw != null)
        	   generator.writeObject(call.mArgumentsKw);
           
            generator.writeEndArray();

         } else if (msg instanceof WampMessage.Prefix) {

           /* WampMessage.Prefix prefix = (WampMessage.Prefix) msg;

            generator.writeStartArray();
            generator.writeNumber(WampMessage.MESSAGE_TYPE_PREFIX);
            generator.writeString(prefix.mPrefix);
            generator.writeString(prefix.mUri);
            generator.writeEndArray();*/

         } else if (msg instanceof WampMessage.Subscribe) {

            WampMessage.Subscribe subscribe = (WampMessage.Subscribe) msg;

            generator.writeStartArray();
            generator.writeNumber(WampMessage.MESSAGE_TYPE_SUBSCRIBE);
            generator.writeNumber(Long.valueOf(subscribe.mRequestId));
            generator.writeObject(subscribe.mOptions);
            generator.writeString(subscribe.mTopicUri);
            generator.writeEndArray();

         } else if (msg instanceof WampMessage.Unsubscribe) {

            WampMessage.Unsubscribe unsubscribe = (WampMessage.Unsubscribe) msg;

            generator.writeStartArray();
            generator.writeNumber(WampMessage.MESSAGE_TYPE_UNSUBSCRIBE);
            generator.writeNumber(Long.valueOf(unsubscribe.mRequestId));
            generator.writeNumber(Long.valueOf(unsubscribe.mSubscriptionId));
            generator.writeEndArray();

         } else if (msg instanceof WampMessage.Publish) {

            WampMessage.Publish publish = (WampMessage.Publish) msg;

            generator.writeStartArray();
            generator.writeNumber(WampMessage.MESSAGE_TYPE_PUBLISH);
            generator.writeNumber(Long.valueOf(publish.mRequestId));
            generator.writeObject(publish.mOptions);
            generator.writeString(publish.mTopicUri);
            if(publish.mArgs != null){
             	generator.writeObject(publish.mArgs);
            }
            if(publish.mArgumentsKw != null)
                generator.writeObject(publish.mArgumentsKw);
            generator.writeEndArray();

         }
         else if(msg instanceof WampMessage.Hello)
         {
        	 WampMessage.Hello hello = (WampMessage.Hello)msg;
        	  generator.writeStartArray();
              generator.writeNumber(WampMessage.MESSAGE_TYPE_HELLO);
              generator.writeString(hello.realm);
              generator.writeObject(hello.details);
              generator.writeEndArray();
         } 
         else if(msg instanceof WampMessage.GoodBye)
         {
        	 WampMessage.GoodBye goodbye = (WampMessage.GoodBye)msg;
        	  generator.writeStartArray();
              generator.writeNumber(WampMessage.MESSAGE_TYPE_GOODBYE);
              generator.writeObject(goodbye.Details);
              generator.writeString(goodbye.Reason);             
              generator.writeEndArray();
         } 
         else {

            // this should not happen, but to be sure
            throw new WebSocketException("invalid message received by AutobahnWriter");
         }
      } catch (JsonGenerationException e) {

         // this may happen, and we need to wrap the error
         throw new WebSocketException("JSON serialization error (" + e.toString() + ")");

      } catch (JsonMappingException e) {

         // this may happen, and we need to wrap the error
         throw new WebSocketException("JSON serialization error (" + e.toString() + ")");
      }

      // make sure the JSON generator has spit out everything
      generator.flush();

      // Jackson's JSON generator produces UTF-8 directly, so we send
      // a text message frame using the raw sendFrame() method
      sendFrame(1, true, mPayload.getByteArray(), 0, mPayload.size());

      
      // cleanup generators resources
      generator.close();
   }
}
