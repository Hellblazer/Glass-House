/** 
 * (C) Copyright 2012 Hal Hildebrand, All Rights Reserved
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

package com.hellblazer.glassHouse.rest.service.impl;

import static junit.framework.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hellblazer.glassHouse.rest.domain.jaxb.jmx.MBeanAttributeJaxBeans;
import com.hellblazer.glassHouse.rest.domain.jaxb.jmx.MBeanAttributeValueJaxBean;
import com.hellblazer.glassHouse.rest.domain.jaxb.jmx.MBeanAttributeValueJaxBeans;
import com.hellblazer.glassHouse.rest.domain.jaxb.jmx.MBeanShortJaxBeans;
import com.hellblazer.glassHouse.rest.domain.jaxb.jmx.OperationReturnValueJaxBean;
import com.hellblazer.glassHouse.rest.domain.jaxb.jmx.OperationReturnValueJaxBeans;
import com.hellblazer.glassHouse.rest.service.AggregateService;
import com.hellblazer.glassHouse.rest.service.impl.AggregateServiceImpl;
import com.hellblazer.jmx.cascading.CascadingService;

public class AggregateServiceFunctionalTest {
    private static final Logger log = LoggerFactory
	    .getLogger(AggregateServiceFunctionalTest.class);

    public static final String ATTRIBUTE_1 = "Attribute1";
    public static final String ATTRIBUTE_2 = "Attribute2";
    public static final String OPERATION_1 = "Operation1";
    public static final String OPERATION_2 = "Operation1";
    public static final String TEST_1_BEAN = "mydomain:type=Test1";
    public static final String TEST_2_BEAN = "mydomain:type=Test2";
    public static final String TEST_3_BEAN = "mydomain:type=Test3";

    private static int allocatePort() {
	InetSocketAddress address = new InetSocketAddress("localhost", 0);
	ServerSocket socket = null;
	try {
	    socket = new ServerSocket();
	    socket.bind(address);
	    return socket.getLocalPort();
	} catch (IOException e) {
	} finally {
	    if (socket != null) {
		try {
		    socket.close();
		} catch (IOException e) {
		}
	    }
	}
	return -1;
    }

    private static JMXConnectorServer contruct(InetSocketAddress jmxEndpoint,
	    MBeanServer mbs) throws IOException {
	System.setProperty("java.rmi.server.randomIDs", "true");

	Map<String, Object> env = new HashMap<String, Object>();
	JMXServiceURL url = new JMXServiceURL("rmi", jmxEndpoint.getHostName(),
		jmxEndpoint.getPort());
	return JMXConnectorServerFactory.newJMXConnectorServer(url, env, mbs);
    }

    private AggregateService aggregateService;
    private CascadingService cascadingService;
    private static Set<String> jmxNodes;
    private MBeanServer mBeanServer;
    private static MBeanServer mbs1;
    private static MBeanServer mbs2;
    private static MBeanServer mbs3;
    private static String node1;
    private static String node2;
    private static String node3;
    private static JMXConnectorServer server1;
    private static JMXConnectorServer server2;
    private static JMXConnectorServer server3;
    private UriInfo uriInfo;
    private UriBuilder uriBuilder;

    @BeforeClass
    public static void initialize() throws IOException,
	    InstanceAlreadyExistsException, MBeanRegistrationException,
	    NotCompliantMBeanException, MalformedObjectNameException,
	    NullPointerException, InterruptedException {

	mbs1 = MBeanServerFactory.newMBeanServer();
	mbs2 = MBeanServerFactory.newMBeanServer();
	mbs3 = MBeanServerFactory.newMBeanServer();

	int port1 = allocatePort();
	InetSocketAddress jmxEndpoint = new InetSocketAddress("localhost",
		port1);
	server1 = contruct(jmxEndpoint, mbs1);
	server1.start();

	int port2 = allocatePort();
	jmxEndpoint = new InetSocketAddress("localhost", port2);
	server2 = contruct(jmxEndpoint, mbs2);
	server2.start();

	int port3 = allocatePort();
	jmxEndpoint = new InetSocketAddress("localhost", port3);
	server3 = contruct(jmxEndpoint, mbs3);
	server3.start();

	node1 = String.format("%s|%s", "localhost", port1);
	node2 = String.format("%s|%s", "localhost", port2);
	node3 = String.format("%s|%s", "localhost", port3);

	log.trace(String.format("Node 1 label: %s", node1));
	log.trace(String.format("Node 2 label: %s", node2));
	log.trace(String.format("Node 3 label: %s", node3));

	jmxNodes = new HashSet<String>();
	jmxNodes.add(node1);
	jmxNodes.add(node2);
	jmxNodes.add(node3);

	Test1 test1 = new Test1();
	test1.setAttribute1(1);
	test1.setAttribute2(2);

	Test2 test2 = new Test2();
	test2.setAttribute1(1);
	test2.setAttribute2(2);

	mbs1.registerMBean(test1, ObjectName.getInstance(TEST_1_BEAN));
	mbs1.registerMBean(test2, ObjectName.getInstance(TEST_2_BEAN));

	mbs2.registerMBean(test1, ObjectName.getInstance(TEST_1_BEAN));
	mbs2.registerMBean(test2, ObjectName.getInstance(TEST_2_BEAN));

	mbs3.registerMBean(test1, ObjectName.getInstance(TEST_1_BEAN));
	mbs3.registerMBean(test2, ObjectName.getInstance(TEST_2_BEAN));
	mbs3.registerMBean(test2, ObjectName.getInstance(TEST_3_BEAN));

	Thread.sleep(100);
    }

    @Before
    public void setUp() throws Exception {

	mBeanServer = MBeanServerFactory.newMBeanServer();
	cascadingService = new CascadingService(mBeanServer);

	cascadingService.mount(server1.getAddress().toString(), "*:*", node1);
	cascadingService.mount(server2.getAddress().toString(), "*:*", node2);
	cascadingService.mount(server3.getAddress().toString(), "*:*", node3);

	aggregateService = new AggregateServiceImpl(mBeanServer);

	uriInfo = mock(UriInfo.class);
	uriBuilder = mock(UriBuilder.class);

	when(uriInfo.getAbsolutePathBuilder()).thenReturn(uriBuilder);
	when(uriBuilder.path(any(String.class))).thenReturn(uriBuilder);
	when(uriBuilder.build()).thenReturn(new URI("http://testuri/test"));
    }

    @Test
    public void testGetAllAttributeValues() throws Exception {
	MBeanAttributeValueJaxBeans attributes = aggregateService
		.getAllAttributeValues(jmxNodes, TEST_1_BEAN);

	assertEquals("expected 2 values for each node", 6,
		attributes.mBeanAttributeValueJaxBeans.size());
    }

    @Test
    public void testGetAttributesMetaData() throws Exception {
	MBeanAttributeJaxBeans mBeanAttributesInfoJaxBean = aggregateService
		.getAttributesMetaData(uriInfo, jmxNodes, TEST_1_BEAN);

	assertEquals(2,
		mBeanAttributesInfoJaxBean.mBeanAttributeJaxBeans.size());
    }

    @Test
    public void testGetAttributeValues() throws Exception {
	MBeanAttributeValueJaxBeans mBeanAttributeValuesJaxBean = aggregateService
		.getAttributeValues(jmxNodes, TEST_1_BEAN, ATTRIBUTE_1);
	assertEquals(jmxNodes.size(),
		mBeanAttributeValuesJaxBean.mBeanAttributeValueJaxBeans.size());
	for (MBeanAttributeValueJaxBean mBeanAttributeValueJaxBean : mBeanAttributeValuesJaxBean.mBeanAttributeValueJaxBeans) {
	    if (node1.equals(mBeanAttributeValueJaxBean.nodeName)) {
		assertEquals(Integer.toString(1),
			mBeanAttributeValueJaxBean.value);
	    }
	}
    }

    @Test
    public void testGetObjectNames() throws Exception {
	MBeanShortJaxBeans mBeanShortJaxBeans = aggregateService
		.getMBeanShortJaxBeans(uriInfo, jmxNodes);

	assertEquals(
		"Expected to get three common mBeanShortJaxBeans as they exist on all nodes",
		2, mBeanShortJaxBeans.mbeans.size());
    }

    @Test
    public void testGetObjectNamesWithAnAdditionalObjectNameOnOneNode() {
	MBeanShortJaxBeans mBeanShortJaxBeans = aggregateService
		.getMBeanShortJaxBeans(uriInfo, jmxNodes);

	assertEquals(
		"Expected to get two common mBeanShortJaxBeans as the TEST_3_BEAN only exists on two nodes",
		2, mBeanShortJaxBeans.mbeans.size());
    }

    @Test
    public void testInvokeOperation() throws Exception {
	OperationReturnValueJaxBeans values = aggregateService.invokeOperation(
		jmxNodes, TEST_1_BEAN, "operation1");
	assertEquals(3, values.operationReturnValueJaxBeans.size());
	for (OperationReturnValueJaxBean bean : values.operationReturnValueJaxBeans) {
	    assertEquals(Integer.toString(1), bean.returnValue);
	}
    }

    @Test
    public void testInvokeOperationWithParameters() throws Exception {
	String value = "Foo Me, Baby";

	String[] params = new String[] { value };

	String[] signature = new String[] { String.class.getCanonicalName() };

	OperationReturnValueJaxBeans values = aggregateService.invokeOperation(
		jmxNodes, TEST_2_BEAN, "operationFoo", params, signature);
	assertEquals(3, values.operationReturnValueJaxBeans.size());
	for (OperationReturnValueJaxBean bean : values.operationReturnValueJaxBeans) {
	    assertEquals(value, bean.returnValue);
	}
    }
}
