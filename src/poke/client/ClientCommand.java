/*
 * copyright 2014, gash
 *
 * Gash licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package poke.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.client.comm.CommConnection;
import poke.client.comm.CommListener;
import poke.comm.App.ClientMessage;
import poke.comm.App.Details;
import poke.comm.App.ClientMessage.Functionalities;
import poke.comm.App.ClientMessage.MessageType;
import poke.comm.App.ClientMessage.Requester;
import poke.comm.App.Header;
import poke.comm.App.Header.Routing;
import poke.comm.App.Payload;
import poke.comm.App.Ping;
import poke.comm.App.Request;
/**
 * The command class is the concrete implementation of the functionality of our
 * network. One can view this as a interface or facade that has a one-to-one
 * implementation of the application to the underlining communication.
 *
 * IN OTHER WORDS (pay attention): One method per functional behavior!
 *
 * @author gash
 *
 */
public class ClientCommand {
	protected static Logger logger = LoggerFactory.getLogger("client");

	private String host;
	private int port;
	private CommConnection comm;

	public ClientCommand(String host, int port) {
		this.host = host;
		this.port = port;

		init();
	}

	private void init() {
		comm = new CommConnection(host, port);
	}

	/**
	 * add an application-level listener to receive messages from the server (as
	 * in replies to requests).
	 *
	 * @param listener
	 */
	public void addListener(CommListener listener) {

		comm.addListener(listener);
	}

	public void getUserList(){
		System.out.println("Getting User details");
		Request.Builder r = Request.newBuilder();
		//Build Header
		Header.Builder h = Header.newBuilder();
		h.setRoutingId(Routing.JOBS);
		h.setOriginator(1000);
		h.setTime(System.currentTimeMillis());
		//NOt setting Poke Status, Reply Message, ROuting Path, Name Value set
		//TO Node is the node to which this client will get connected
		h.setToNode(0);

		ClientMessage.Builder clBuilder  = ClientMessage.newBuilder();

		clBuilder.setFunctionality(Functionalities.ADDCOURSE);
		clBuilder.setMessageType(MessageType.REQUEST);
		Details.Builder db = Details.newBuilder();
		//db.setCourseId();
		clBuilder.setDetails(db);


		//Build Payload
		Payload.Builder p = Payload.newBuilder();
		//Not adding anything as it is just a register message
		p.setClientMessage(clBuilder);

		r.setBody(p);
		r.setHeader(h);
		Request req = r.build();
		//System.out.println(req);
		try {
			comm.sendMessage(req);
			System.out.println("Get user details request sent");
		} catch (Exception e) {
			logger.warn("Unable to deliver message, queuing");
		}
	}


	public void getCourseDetails(){
		System.out.println("Getting Course details");
		Request.Builder r = Request.newBuilder();
		//Build Header
		Header.Builder h = Header.newBuilder();
		h.setRoutingId(Routing.JOBS);
		h.setOriginator(1000);
		h.setTime(System.currentTimeMillis());
		//NOt setting Poke Status, Reply Message, ROuting Path, Name Value set
		//TO Node is the node to which this client will get connected
		h.setToNode(0);

		ClientMessage.Builder clBuilder  = ClientMessage.newBuilder();

		clBuilder.setFunctionality(Functionalities.GETCOURSEDESCRIPTION);
		clBuilder.setMessageType(MessageType.REQUEST);
		Details.Builder db = Details.newBuilder();
		//db.setCourseId();
		clBuilder.setDetails(db);


		//Build Payload
		Payload.Builder p = Payload.newBuilder();
		//Not adding anything as it is just a register message
		p.setClientMessage(clBuilder);

		r.setBody(p);
		r.setHeader(h);
		Request req = r.build();
		//System.out.println(req);
		try {
			comm.sendMessage(req);
			System.out.println("Get user details request sent");
		} catch (Exception e) {
			logger.warn("Unable to deliver message, queuing");
		}
	}

	public void getUser(int userId){
		System.out.println("Getting user details");
		Request.Builder r = Request.newBuilder();
		//Build Header
		Header.Builder h = Header.newBuilder();
		h.setRoutingId(Routing.JOBS);
		h.setOriginator(1000);
		h.setTime(System.currentTimeMillis());
		//NOt setting Poke Status, Reply Message, ROuting Path, Name Value set
		//TO Node is the node to which this client will get connected
		h.setToNode(0);

		ClientMessage.Builder clBuilder  = ClientMessage.newBuilder();

		clBuilder.setFunctionality(Functionalities.GETSUSER);
		clBuilder.setMessageType(MessageType.REQUEST);
		clBuilder.setClientId(2);
		Details.Builder db = Details.newBuilder();

		db.setUserId(userId);
		clBuilder.setDetails(db);


		//Build Payload
		Payload.Builder p = Payload.newBuilder();
		//Not adding anything as it is just a register message
		p.setClientMessage(clBuilder);

		r.setBody(p);
		r.setHeader(h);
		Request req = r.build();
		//System.out.println(req);
		try {
			comm.sendMessage(req);
			System.out.println("Get user details request sent");
		} catch (Exception e) {
			logger.warn("Unable to deliver message, queuing");
		}
	}



	/**
	 * Our network's equivalent to ping
	 *
	 * @param tag
	 * @param num
	 */
	public void poke(String tag, int num) {
		// data to send
		Ping.Builder f = Ping.newBuilder();
		f.setTag(tag);
		f.setNumber(num);

		// payload containing data
		Request.Builder r = Request.newBuilder();
		Payload.Builder p = Payload.newBuilder();
		p.setPing(f.build());
		r.setBody(p.build());

		// header with routing info
		Header.Builder h = Header.newBuilder();
		h.setOriginator(1000);
		h.setTag("test finger");
		h.setTime(System.currentTimeMillis());
		h.setRoutingId(Header.Routing.PING);
		r.setHeader(h.build());

		Request req = r.build();

		try {
			comm.sendMessage(req);
		} catch (Exception e) {
			logger.warn("Unable to deliver message, queuing");
		}
	}
}
