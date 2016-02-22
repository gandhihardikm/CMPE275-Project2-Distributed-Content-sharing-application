/*
 * copyright 2012, gash
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
package poke.demo;

import poke.client.ClientCommand;
import poke.client.ClientPrintListener;
import poke.client.comm.CommListener;
import java.util.Scanner;


/**
 * DEMO: how to use the command class
 * 
 * @author gash
 * 
 */
public class Jab {
	private String tag;
	private int count;

	public Jab(String tag) {
		this.tag = tag;
	}

	public void run() {

		// Read IP address and port number of server, to send request
		Scanner server = new Scanner(System.in);
		System.out.println("Enter ip Address:");
		String ip = server.nextLine();
		System.out.println("Enter port number");
		int port = Integer.parseInt(server.nextLine());

		// Internally it will create channel between client and server (specified ip)
		ClientCommand cc = new ClientCommand(ip, port);
		CommListener listener = new ClientPrintListener("jab demo");
		cc.addListener(listener);


		System.out.println("1.Search User \n2.Get Course List\n3.Get User List\n\nEnter your Choice:");
		Scanner in = new Scanner(System.in);
		int choice = in.nextInt();

		switch(choice)
		{
			case 1:
				System.out.println("Enter User to be search:");
				int num = in.nextInt();
				cc.getUser(num);
				break;

			case 2:
				cc.getCourseDetails();
				break;

			case 3:
				cc.getUserList();
				break;

			default:
				System.out.println("Invalid choice!!!");
		}

		/*for (int i = 0; i < 100000; i++) {

			cc.getCourseDetails();
		}*/
	}

	public static void main(String[] args) {
		try {
			Jab jab = new Jab("jab");
			jab.run();

			// we are running asynchronously
			System.out.println("\nExiting in 5 seconds");
			Thread.sleep(5000);
			System.exit(0);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
