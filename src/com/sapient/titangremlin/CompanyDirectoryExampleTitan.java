package com.sapient.titangremlin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.thinkaurelius.titan.core.Multiplicity;
import com.thinkaurelius.titan.core.PropertyKey;
import com.thinkaurelius.titan.core.TitanException;
import com.thinkaurelius.titan.core.TitanFactory;
import com.thinkaurelius.titan.core.TitanGraph;
import com.thinkaurelius.titan.core.TitanTransaction;
import com.thinkaurelius.titan.core.TitanVertex;
import com.thinkaurelius.titan.core.VertexLabel;
import com.thinkaurelius.titan.core.attribute.Text;
import com.thinkaurelius.titan.core.schema.ConsistencyModifier;
import com.thinkaurelius.titan.core.schema.Mapping;
import com.thinkaurelius.titan.core.schema.TitanGraphIndex;
import com.thinkaurelius.titan.core.schema.TitanManagement;
import com.tinkerpop.blueprints.Compare;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.util.ElementHelper;
import com.tinkerpop.blueprints.util.io.graphml.GraphMLReader;
import com.tinkerpop.blueprints.util.wrappers.batch.BatchGraph;
import com.tinkerpop.blueprints.util.wrappers.batch.VertexIDType;
import com.tinkerpop.gremlin.java.GremlinPipeline;
import com.tinkerpop.pipes.PipeFunction;
import com.tinkerpop.pipes.branch.LoopPipe;
import com.tinkerpop.pipes.util.structures.Row;

public class CompanyDirectoryExampleTitan {
	public static final String INDEX_NAME = "search";

	protected TitanGraph titanGraph;

	// create db configuration programmataically

	@Test
	public void createConfiguration() {
		String directory = "D:/Titan/titan-0.5.0-hadoop2/titan-0.5.0-hadoop2/conf/titan-berkeleydb-es.properties";
		TitanFactory.Builder config = TitanFactory.build();
		config.set("storage.backend", "berkeleyje");
		config.set("storage.directory", directory);
		config.set("index.search.backend", "elasticsearch");
		config.set("index.search.directory", directory + File.separator + "es");
		config.set("index.search.elasticsearch.local-mode", true);
		config.set("index.search.elasticsearch.client-only", false);

		TitanGraph graph = config.open();

	}

	// open TitanFactory instance

	@Before
	public void openInstance() throws IOException {

		titanGraph = TitanFactory
				.open("D:/Titan/titan-0.5.4-hadoop1/titan-0.5.4-hadoop1/conf/titan-berkeleydb-es.properties");
		System.out.println("starting instance ... " + titanGraph);

	}

	@After
	public void after() {
		System.out.println("shutting down..");
		titanGraph.shutdown();
	}

	// creation of Schema
	@Test
	public void createSchema() {
		TitanManagement mgmt = titanGraph.getManagementSystem();
		final PropertyKey empName = mgmt.makePropertyKey("empName")
				.dataType(String.class).make();
		TitanGraphIndex empIdIndex = mgmt
				.buildIndex("empNameIndex", Vertex.class).addKey(empName)
				.buildCompositeIndex();
		mgmt.setConsistency(empIdIndex, ConsistencyModifier.LOCK);

		final PropertyKey empAge = mgmt.makePropertyKey("empAge")
				.dataType(Integer.class).make();
		final PropertyKey empExperience = mgmt.makePropertyKey("empExp")
				.dataType(Integer.class).make();

		final PropertyKey compName = mgmt.makePropertyKey("compName")
				.dataType(String.class).make();
		TitanGraphIndex compNameIndex = mgmt
				.buildIndex("compNameIndex", Vertex.class).addKey(compName)
				.unique().buildCompositeIndex();
		mgmt.setConsistency(compNameIndex, ConsistencyModifier.LOCK);

		final PropertyKey compAddress = mgmt.makePropertyKey("compAddress")
				.dataType(String.class).make();

		final PropertyKey department = mgmt.makePropertyKey("department")
				.dataType(String.class).make();
		final PropertyKey empId = mgmt.makePropertyKey("empId")
				.dataType(String.class).make();
		final PropertyKey designation = mgmt.makePropertyKey("designation")
				.dataType(String.class).make();

		// building edge index

		TitanGraphIndex eindex = mgmt.buildIndex("deptEdgeIndex", Edge.class)
				.addKey(department).addKey(empId).buildMixedIndex(INDEX_NAME);

		// creating edge labels

		mgmt.makeEdgeLabel("manager").multiplicity(Multiplicity.MANY2ONE)
				.make();
		mgmt.makeEdgeLabel("employeeOf").multiplicity(Multiplicity.MANY2ONE)
				.make();
		mgmt.makeEdgeLabel("formerEmployee")
				.multiplicity(Multiplicity.MANY2ONE).make();

		// create Label or type of Vertices
		mgmt.makeVertexLabel("employee").make();
		mgmt.makeVertexLabel("company").make();
		mgmt.makeVertexLabel("friend").make();

		mgmt.commit();

	}

	// Load data using graphml
	// https://github.com/tinkerpop/blueprints/wiki/GraphML-Reader-and-Writer-Library
	@Test
	public void loadGraphMLData() throws IOException {
		InputStream inputStream = TitanTest.class
				.getResourceAsStream("/graph-of-the-gods.xml");
		GraphMLReader.inputGraph(titanGraph, inputStream);

	}

	// Load Data using code TItanGraph
	@Test
	public void loadDataVIaCode() {
		// transaction open automatically but in concurrennt scenrios to handle
		// multiple transaction can be started manually
		TitanTransaction tx = titanGraph.newTransaction();

		// creating vertices data of employee type
		TitanVertex emp1 = tx.addVertexWithLabel("employee");
		emp1.addProperty("empName", "Sunder Pichai");
		emp1.addProperty("empAge", 42);
		emp1.addProperty("empExp", 18);

		TitanVertex emp2 = tx.addVertexWithLabel("employee");
		emp2.addProperty("empName", "Mark");
		emp2.addProperty("empAge", 32);
		emp2.addProperty("empExp", 10);

		Vertex emp3 = tx.addVertexWithLabel("employee");
		ElementHelper.setProperties(emp3, "empName", "Chris", "empAge", 43,
				"empExp", 20);

		Vertex emp5 = tx.addVertexWithLabel("employee");
		ElementHelper.setProperties(emp5, "empName", "Manoj", "empAge", 28);

		Vertex emp6 = tx.addVertexWithLabel("employee");
		ElementHelper.setProperties(emp6, "empName", "Raj", "empAge", 26);

		// creating vertices data of company type
		TitanVertex company1 = tx.addVertexWithLabel("company");
		company1.addProperty("compName", "facebook");
		company1.addProperty("address", "California USA ");

		TitanVertex company2 = tx.addVertexWithLabel("company");
		company2.addProperty("compName", "Google");
		company2.addProperty("address", "Mountain View ,USA");

		// adding edges

		emp6.addEdge("employeeOf", company2).setProperty("designation",
				"Senior Software Engineer");
		emp6.addEdge("formerEmployee", company1).setProperty("desgination",
				"Software Engineer");
		emp6.addEdge("manager", emp3);

		emp5.addEdge("employeeOf", company1).setProperty("designation",
				"Senior Analyst");
		;
		emp5.addEdge("formerEmployee", company2).setProperty("desgination",
				"Senior Software Engineer");

		// emp3.addEdge("Manager", emp1);
		emp3.addEdge("manager", emp1);

		emp3.addEdge("employeeOf", company2).setProperty("designation",
				"Director");

		emp1.addEdge("employeeOf", company2).setProperty("designation",
				"Chairman");

		emp5.addEdge("Manager", emp2);
		emp5.addEdge("manager", emp2);

		emp2.addEdge("employeeOf", company1).setProperty("designation",
				"Chairman");

		tx.commit();

	}

	// load small set of data using file
	// less than 1 million of rows

	@Test
	public void loadDataByUsingFile() {

	}

	// loading bulk data
	// TO Load more than 10 Million rows it needs BatchGraph
	//
	@Test
	public void loadBulkDataUsingBatchGraph() throws IOException {

		BatchGraph bgraph = new BatchGraph(titanGraph, VertexIDType.STRING,
				1000);

		// read csv file and load in array list
		List<String[]> quads = new ArrayList<>();
		String line = "";
		BufferedReader br = new BufferedReader(new FileReader(
				"C:\\correct_email_data.csv"));
		while (((line = br.readLine()) != null)) {
			if (line.length() > 50) {
				System.out.println(line);
				// use comma as separator
				String[] country = line.split(",");

				System.out.println("Country [sender= " + country[1]
						+ " , receiver=" + country[2] + " , Date=" + country[3]
						+ "]");

				quads.add(country);

			}
		}
		System.out.println("reading  complete.. looping starts");

		// The last argument in the constructor is the batch size, that is, the
		// number of vertices and edges to load before committing a transaction
		// and starting a new one.
		int counter = 1;
		for (String[] quad : quads) {

			System.out.println("*********" + quad);
			Vertex vertices1 = bgraph.getVertex(quad[1]);
			if (vertices1 == null) {
				vertices1 = bgraph.addVertex(counter, "emailSender", quad[1]);
				System.out.println(quad[1]);
			}
			counter++;

			Vertex vertices2 = bgraph.getVertex(quad[2]);
			if (vertices2 == null) {
				vertices2 = bgraph.addVertex(counter, "emailReceipient",
						quad[2]);
				System.out.println(quad[2]);
			}
			counter++;

			Edge edge = bgraph.addEdge(null, vertices1, vertices2, "email");
			edge.setProperty("emailDate", quad[3]);
			System.out.println(quad[3]);
			System.out.println("added vertex " + vertices1 + " vertex 2 "
					+ vertices2 + "  edge " + edge + " <<<<<<<<  " + counter
					+ " >>>>>>>>>>>> ");

		}

		// bgraph.commit();

	}

	// get company1 Chairman
	@Test
	public void getChairman() {

		GremlinPipeline gremlinPipeline = new GremlinPipeline();
		gremlinPipeline.start(titanGraph.getVertices("compName", "facebook"))
				.inE("employeeOf").has("designation", "chairman").select();
		// String name = (String) vertex.getProperty("empName");
		// String type = (String) vertex.getProperty("empAge");
		// System.out.println(name+"---"+type );
		Row row = (Row) gremlinPipeline.next();
		Vertex vertex = (Vertex) row.getColumn("tm");
		System.out.println(vertex.getProperty("empName"));

	}

	// get Googles employee name
	@Test
	public void getGoogleEmployeeName() {

		GremlinPipeline gremlinPipeline = new GremlinPipeline();
		gremlinPipeline.start(titanGraph.getVertices("compName", "facebook"))
				.in("employeeOf").map();

		while (gremlinPipeline.hasNext()) {
			Map<String, ? extends Object> map = (Map<String, ? extends Object>) gremlinPipeline
					.next();

			for (Map.Entry<String, ? extends Object> entry : map.entrySet()) {
				System.out.println(entry.getKey() + "-" + entry.getValue());
			}

		}

	}

	// get the manager's manager name of Employee6

	@Test
	public void getManagerHeirarchy() {
		GremlinPipeline gremlinPipeline = new GremlinPipeline();

		System.out.println("Raj's Manager Heirarchy");
		gremlinPipeline = gremlinPipeline.start(
				titanGraph.getVertices("empName", "Raj")).out("manager");
		while (gremlinPipeline.hasNext()) {
			Vertex vertex = (Vertex) gremlinPipeline.next();
			String name = (String) vertex.getProperty("empName");
			// Assert.assertEquals("tartarus", name);
			System.out.println(name);
			gremlinPipeline = gremlinPipeline.start(
					titanGraph.getVertices("empName", "Raj")).out("Manager");
		}

	}

	@Test
	public void addRemoveProperties() {
		GremlinPipeline gremlinPipeline = new GremlinPipeline();
		TitanTransaction tx = titanGraph.newTransaction();

		System.out.println("Raj's Manager Heirarchy");
		gremlinPipeline = gremlinPipeline.start(
				titanGraph.getVertices("empName", "Raj")).out("manager");
		while (gremlinPipeline.hasNext()) {
			Vertex vertex = (Vertex) gremlinPipeline.next();
			String name = (String) vertex.getProperty("empName");
			// Assert.assertEquals("tartarus", name);
			System.out.println(name);
			Integer age = (Integer) vertex.getProperty("empAge");
			System.out.println(age);

			// add new property
			vertex.setProperty("empGender", "Male");

			vertex.removeProperty("empAge");

		}

		tx.commit();
		// test added values
		GremlinPipeline gremlinPipeline1 = new GremlinPipeline();

		gremlinPipeline1 = gremlinPipeline1.start(
				titanGraph.getVertices("empName", "Raj")).out("manager");
		while (gremlinPipeline1.hasNext()) {
			Vertex vertex = (Vertex) gremlinPipeline1.next();
			String name = (String) vertex.getProperty("empGender");
			// Assert.assertEquals("tartarus", name);
			System.out.println(name);
			// add new property
			vertex.setProperty("empGender", "Male");
			Integer age = (Integer) vertex.getProperty("empAge");
			System.out.println("age " + age);

		}
	}

	// employee name having age greater than 20
	@Test
	public void getEmployees() {

	}

	// employee name company name and designation having age greater 40

	// add one more relationship of friends between emp6 and emp5
	// chain of people who shares same manager
	@Test
	public void addEmployees() {

		try {
			TitanTransaction tx = titanGraph.newTransaction();

			Vertex emp1 = tx.getVertices("empName", "Sunder Pichai").iterator()
					.next();
			Vertex emp6 = tx.getVertices("empName", "Raj").iterator().next();

			Vertex emp7 = tx.addVertexWithLabel("employee");
			ElementHelper.setProperties(emp7, "empName", "Tom", "empAge", 32,
					"empExp", 12);

			Vertex emp8 = tx.addVertexWithLabel("employee");
			ElementHelper.setProperties(emp8, "empName", "Rajesh", "empAge",
					35, "empExp", 13);

			emp7.addEdge("manager", emp1);

			emp8.addEdge("manager", emp1);

			Vertex emp9 = tx.addVertexWithLabel("employee");
			ElementHelper.setProperties(emp9, "empName", "Martina", "empAge",
					31, "empExp", 8);

			Vertex emp10 = tx.addVertexWithLabel("employee");
			ElementHelper.setProperties(emp10, "empName", "Abhishek", "empAge",
					29, "empExp", 7);

			emp9.addEdge("manager", emp7);

			emp10.addEdge("manager", emp8);

			Vertex emp11 = tx.addVertexWithLabel("employee");
			ElementHelper.setProperties(emp11, "empName", "Rex", "empAge", 25,
					"empExp", 2);

			Vertex emp12 = tx.addVertexWithLabel("employee");
			ElementHelper.setProperties(emp12, "empName", "Dalet", "empAge",
					26, "empExp", 3);

			emp11.addEdge("manager", emp9);
			emp12.addEdge("manager", emp9);

			Vertex emp13 = tx.addVertexWithLabel("employee");
			ElementHelper.setProperties(emp13, "empName", "Gaurav", "empAge",
					24, "empExp", 1);

			// emp13.addEdge("manager", emp6);
			// emp3.addEdge("manager", emp1);

			// TitanVertex company3 = tx.addVertexWithLabel("company");
			// company3.addProperty("compName", "facebook");
			// company3.addProperty("address", "India ");
			// emp13.addEdge("employeeOf", company3).setProperty("designation",
			// "Senior Analyst");

			tx.commit();

			TitanManagement mgmt = titanGraph.getManagementSystem();

			final PropertyKey department = mgmt.makePropertyKey("department")
					.dataType(String.class).make();
		} catch (TitanException te) {
			System.out.println("In catch block");
			te.printStackTrace();
		}
	}

	@Test
	public void getFirstLEvelDownOfGoogle() {
		GremlinPipeline gremlinPipeline = new GremlinPipeline();

		System.out.println("Sundar's subordinates /down Heirarchy");
		gremlinPipeline.start(
				titanGraph.getVertices("empName", "Sunder Pichai")).in(
				"manager");

		int level = 0;

		while (gremlinPipeline.hasNext()) {
			Vertex vertex = (Vertex) gremlinPipeline.next();
			String name = (String) vertex.getProperty("empName");
			Integer age = (Integer) vertex.getProperty("empAge");
			Integer exp = (Integer) vertex.getProperty("empExp");

			System.out.println("Name : " + name + "- Age : " + age
					+ "- Experience : " + exp + " - level -" + level);
			// gremlinPipeline=gremlinPipeline.in("manager");

			if (!gremlinPipeline.hasNext()) {
				gremlinPipeline = gremlinPipeline.in("manager");
				level++;
			}
		}

	}

	@Test
	public void DepthFirstTraversal() {

		final Vertex saturnVertex = titanGraph
				.getVertices("empName", "Sunder Pichai").iterator().next();
		final GremlinPipeline<Vertex, ?> pipe = new GremlinPipeline<Vertex, Vertex>(
				saturnVertex).inE("manager").gather().scatter().outV().gather()
				.scatter().inE("father").gather().scatter().outV()
				.property("empName");
		final String grandchildName = pipe.next().toString();
		System.out.println(grandchildName);

	}

	// Get Manager's chain deapthfirst
	@Test
	public void getManagerHierarchy() {
		GremlinPipeline gremlinPipeline = new GremlinPipeline();

		System.out.println("Sundar's subordinates /down Heirarchy");
		gremlinPipeline = gremlinPipeline
				.start(titanGraph.getVertices("empName", "Sunder Pichai"))
				.as("tmp").in()
				.loop("tmp", new PipeFunction<LoopPipe.LoopBundle, Boolean>() {
					public Boolean compute(LoopPipe.LoopBundle bundle) {
						return bundle.getLoops() < 4;
					}
				}).path();

		while (gremlinPipeline.hasNext()) {

			List list = (List) gremlinPipeline.next();

			Iterator<Vertex> it = list.iterator();
			System.out.println(list.size());
			while (it.hasNext()) {
				Vertex vertex = (Vertex) it.next();
				String name = (String) vertex.getProperty("empName");
				Integer age = (Integer) vertex.getProperty("empAge");
				Integer exp = (Integer) vertex.getProperty("empExp");

				System.out.println("Name : " + name + "- Age : " + age
						+ "- Experience : " + exp);
			}
		}
	}

	@Test
	public void testBatchLoad() {

		Iterator<Vertex> itr1 = titanGraph.query()
				.has("empName", Text.CONTAINS, "Mark").vertices().iterator();

		while (itr1.hasNext()) {
			Vertex vertex = (Vertex) itr1.next();

			String name = (String) vertex.getProperty("empName");
			Integer age = (Integer) vertex.getProperty("empAge");
			Integer exp = (Integer) vertex.getProperty("empExp");

			System.out.println("Name : " + name + "- Age : " + age
					+ "- Experience : " + exp);

		}

		Iterator<Vertex> itr = titanGraph.query()
				.has("empName", Compare.EQUAL, "Rajesh").vertices().iterator();
		while (itr.hasNext()) {
			Vertex vertex = (Vertex) itr.next();
			String name = (String) vertex.getProperty("empName");
			Integer age = (Integer) vertex.getProperty("empAge");
			Integer exp = (Integer) vertex.getProperty("empExp");

			System.out.println("Name : " + name + "- Age : " + age
					+ "- Experience : " + exp);

		}
		/*
		 * System.out.println("Vertices of " + titanGraph); for (Object vertex :
		 * titanGraph.getVertices()) { System.out.println((Vertex) vertex); }
		 * System.out.println("Edges of " + titanGraph); for (Object edge :
		 * titanGraph.getEdges()) { System.out.println((Edge) edge); }
		 */
	}

	@Test
	public void loadHighVolumeDataForMixedasString() throws IOException {

		TitanManagement mgmt = titanGraph.getManagementSystem();

		// creating schema
		mgmt.makeVertexLabel("organization11").make();

		final PropertyKey orgName = mgmt.makePropertyKey("orgName11")
				.dataType(String.class).make();
		TitanGraphIndex orgNameIndex11 = mgmt
				.buildIndex("orgNameIndex11", Vertex.class)
				.addKey(orgName, Mapping.TEXT.getParameter())
				.buildMixedIndex(INDEX_NAME);

		TitanGraphIndex orgNameIndex12 = mgmt
				.buildIndex("orgNameIndex12", Vertex.class)
				.addKey(orgName, Mapping.STRING.getParameter())
				.buildMixedIndex(INDEX_NAME);

		mgmt.commit();
		TitanTransaction tx = titanGraph.newTransaction();

		BatchGraph bgraph = new BatchGraph(titanGraph, VertexIDType.STRING,
				1000);

		// read csv file and load in array list
		List<String> quads = new ArrayList<>();
		String line = "";
		BufferedReader br = new BufferedReader(new FileReader(
				"C://entities.txt"));
		while ((line = br.readLine()) != null) {

			// use comma as separator

			quads.add(line);
		}

		System.out.println("reading complete.. looping starts");

		// The last argument in the constructor is the batch size, that is, the
		// number of vertices and edges to load before committing a transaction
		// and starting a new one.
		for (String quad : quads) {
			TitanVertex company1 = tx.addVertexWithLabel("organization11");
			company1.addProperty("orgName11", quad);

			System.out.println("loading org name " + quad);

		}

		// bgraph.commit();
		tx.commit();

	}

	@Test
	public void createIndex() {
		// TitanManagement mgmt = titanGraph.getManagementSystem();

	}

	// composite keys supports only exact search .We can use COmapre enum
	// if Full text search is used then it will throw exception that index is
	// not defined
	@Test
	public void findOrgName_String_Search_test1() {

		Long t1 = Calendar.getInstance().getTimeInMillis();
		System.out.println("starting time >>" + t1);
		Iterator<Vertex> itr1 = titanGraph.query()
				.has("orgName1", Text.CONTAINS, "Sai").vertices().iterator();
		int count = 0;
		while (itr1.hasNext()) {
			Vertex vertex = (Vertex) itr1.next();

			String name = (String) vertex.getProperty("orgName1");

			System.out.println("Name : " + name);
			count++;
		}

		Long t2 = Calendar.getInstance().getTimeInMillis();
		System.out.println("time taken in milliseconds >>" + (t2 - t1));
		System.out.println("totak no of records " + count);
	}

	@Test
	public void findOrgName_FullTextSearch_Search_test1() {

		Long t1 = Calendar.getInstance().getTimeInMillis();
		System.out.println("starting time >>" + t1);
		Iterator<Vertex> itr1 = titanGraph.query()
				.has("orgName1", Compare.EQUAL, "Saint Paul").vertices()
				.iterator();
		int count = 0;
		while (itr1.hasNext()) {
			Vertex vertex = (Vertex) itr1.next();

			String name = (String) vertex.getProperty("orgName1");

			System.out.println("Name : " + name);
			count++;
		}

		Long t2 = Calendar.getInstance().getTimeInMillis();
		System.out.println("time taken in milliseconds >>" + (t2 - t1));
		System.out.println("totak no of records " + count);
	}

	// only supports casandra and hbase in old versions
	@Test
	public void reindexProcedure() {

	}

	@Test
	public void loadAndCreateSchemaMixedIndexHigVolumeData() throws IOException {

		TitanManagement mgmt = titanGraph.getManagementSystem();

		// creating schema
		mgmt.makeVertexLabel("organization2").make();

		final PropertyKey orgName = mgmt.makePropertyKey("orgName2")
				.dataType(String.class).make();
		mgmt.buildIndex("orgNameMixedIndex2", Vertex.class).addKey(orgName)
				.buildMixedIndex("search");

		mgmt.commit();
		TitanTransaction tx = titanGraph.newTransaction();

		BatchGraph bgraph = new BatchGraph(titanGraph, VertexIDType.STRING,
				1000);

		// read csv file and load in array list
		List<String> quads = new ArrayList<>();
		String line = "";
		BufferedReader br = new BufferedReader(new FileReader(
				"C://entities.txt"));
		while ((line = br.readLine()) != null) {

			// use comma as separator

			quads.add(line);
		}

		System.out.println("reading complete.. looping starts");

		// The last argument in the constructor is the batch size, that is, the
		// number of vertices and edges to load before committing a transaction
		// and starting a new one.
		for (String quad : quads) {
			TitanVertex company1 = tx.addVertexWithLabel("organization2");
			company1.addProperty("orgName2", quad);

			System.out.println("loading org name " + quad);

		}

		// bgraph.commit();
		tx.commit();
	}

	@Test
	public void createStaticVertices() {

		TitanManagement mgmt = titanGraph.getManagementSystem();

		mgmt.makeVertexLabel("student").setStatic().make();

		mgmt.commit();

		// mgmt.ma

	}

	@Test
	public void testStaticVertices() {
		GremlinPipeline gremlinPipeline = new GremlinPipeline();

		System.out.println("Sundar's subordinates /down Heirarchy");
		gremlinPipeline.start(titanGraph.getVertices("stuName", "GT"));

		int level = 0;

		while (gremlinPipeline.hasNext()) {
			Vertex vertex = (Vertex) gremlinPipeline.next();
			vertex.setProperty("stuAge", 23);
		}
	}
}
