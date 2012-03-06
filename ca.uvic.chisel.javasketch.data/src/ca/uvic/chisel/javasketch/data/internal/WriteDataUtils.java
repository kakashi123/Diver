/*******************************************************************************
 * Copyright (c) 2009 the CHISEL group and contributors. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: the CHISEL group - initial API and implementation
 *******************************************************************************/
package ca.uvic.chisel.javasketch.data.internal;

import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;

import org.eclipse.core.runtime.CoreException;

import ca.uvic.chisel.hsqldb.server.HSQLTrigger;
import ca.uvic.chisel.hsqldb.server.IDataPortal;

/**
 * DataUtils used for writing.
 * 
 * @author Del Myers
 * 
 */
public class WriteDataUtils extends DataUtils {

	private static final String CREATE_THREAD_STATEMENT = "INSERT INTO Thread VALUES (?, ?, ?, ?)";
	private static final String CREATE_EVENT_STATEMENT = "INSERT INTO Event VALUES (?, ?, ?)";
	private static final String CREATE_ACTIVATION_STATEMENT = "INSERT INTO Activation VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
	private static final String CREATE_DATA_STATEMENT = "INSERT INTO Data VALUES (?, ?, ?, ?, ?, ?)";
	private static final String CREATE_MESSAGE_STATEMENT = "INSERT INTO Message VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
	private static final String IDENTITY_CALL = "CALL IDENTITY()";
	private long messageID;
	private ExecutableQuery createMessageStatement;
	private long dataID;
	private ExecutableQuery createDataStatement;
	private long activationID;
	private ExecutableQuery createActivationStatement;
	private long eventID;
	private ExecutableQuery createEventStatement;
	private long threadID;
	private ExecutableQuery createThreadStatement;
	private DataTrigger dataTrigger;
	private String triggerName;
	
	/**
	 * @param c
	 * @throws SQLException
	 */
	public WriteDataUtils(IDataPortal portal) throws SQLException {
		super(portal);
		prepareWriteStatements();
		this.dataTrigger = new DataTrigger();
	}

	/**
	 * Drops and recreates all the tables required for storing the trace data. A
	 * new entry for the trace is created.
	 * @throws CoreException 
	 */
	public synchronized void initializeDB(String launchId, Date timestamp)
			throws SQLException, IOException {
		messageID = dataID = activationID = eventID = threadID = 0;
		getPortal().setWritable(true);
		Statement s = getWriteConnection().createStatement();
		dropTrigger(s, launchId);
		dropView("TraceClass");
		dropView("Method");
		dropTable("Event");
		dropTable("Activation");
		dropTable("Message");
		dropTable("Data");
		dropTable("Thread");
		dropTable("Trace");
		dropTable("Method");
		dropTable("TraceClass");
		dropTable("TraceClassTemp");
		dropTable("MethodTemp");
		this.order_num = 0L;
		Timestamp time = new Timestamp(timestamp.getTime());
		// create the tables.

		s.execute("CREATE TABLE Trace (" + "model_id BIGINT PRIMARY KEY,"
				+ "launch_id VARCHAR(256)," + "time TIMESTAMP,"
				+ "max_id BIGINT, data_time TIMESTAMP)");

		// insert a row for the trace
		String sql = "INSERT INTO Trace VALUES(0, '" + launchId + "', '"
				+ time.toString() + "', 0, '"
				+ new Timestamp(System.currentTimeMillis()) + "')";
		s.execute(sql);

		// s.execute("CREATE TABLE TraceClass ("
		// + "model_id BIGINT,"
		// + "name VARCHAR(512),"
		// + "CONSTRAINT UNIQUE_NAME UNIQUE (name)"
		// + ")");
		//		
		// s.execute("CREATE TABLE Method ("
		// + "model_id BIGINT PRIMARY KEY,"
		// + "type_id BIGINT,"
		// + "name VARCHAR(256),"
		// + "signature VARCHAR(512))");

		s
			.execute("CREATE TABLE Event ("
					+ "model_id BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1),"
					+ "time BIGINT," + "text VARCHAR(128)" + ")");

		s
			.execute("CREATE TABLE Thread ("
					+ "model_id BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1),"
					+ "thread_id INT," + "thread_name VARCHAR(128),"
					+ "root_id BIGINT,"
					+ "CONSTRAINT UNIQUE_THREAD UNIQUE (thread_id),"
					+ "CONSTRAINT UNIQUE_ROOT UNIQUE (root_id))");

		s
			.execute("CREATE TABLE Message ("
					+ "model_id BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1), "
					+ "kind VARCHAR(10), " + "activation_id BIGINT, "
					+ "opposite_id BIGINT, " + "order_num BIGINT, "
					+ "time BIGINT, " + "code_line INT, "
					+ "sequence VARCHAR(8000))");
		s.execute("CREATE INDEX IDX_ACTIVATION ON Message (activation_id)");
		s.execute("CREATE INDEX IDX_KIND ON Message (kind)");
		s.execute("CREATE INDEX IDX_TIME ON Message (time)");
		s.execute("CREATE INDEX IDX_ORDER ON Message (order_num)");
		s.execute("CREATE INDEX IDX_OPPOSITE ON Message (opposite_id)");
		s
			.execute("CREATE TABLE Data ("
					+ "model_id BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1),"
					+ "type_id BIGINT," + "ref_id BIGINT,"
					+ "instance VARCHAR(512), " + "value VARCHAR(512),"
					+ "order_num INT)");
		s.execute("CREATE INDEX IDX_TYPE ON Data (type_id)");
		s.execute("CREATE INDEX IDX_REFERENCE ON Data (ref_id)");
		s.execute("CREATE INDEX IDX_INSTANCE ON Data (instance)");
		s
			.execute("CREATE TABLE Activation ("
					+ IActivationTable.columnNames[IActivationTable.MODEL_ID]
					+ " BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1),"
					+ IActivationTable.columnNames[IActivationTable.ARRIVAL_ID]
					+ " BIGINT, "
					+ IActivationTable.columnNames[IActivationTable.TYPE_NAME]
					+ " VARCHAR(128),"
					+ IActivationTable.columnNames[IActivationTable.METHOD_NAME]
					+ " VARCHAR(128),"
					+ IActivationTable.columnNames[IActivationTable.METHOD_SIGNATURE]
					+ " VARCHAR(512),"
					+ IActivationTable.columnNames[IActivationTable.THREAD_ID]
					+ " BIGINT,"
					+ IActivationTable.columnNames[IActivationTable.THIS_TYPE]
					+ " VARCHAR(128),"
					+ IActivationTable.columnNames[IActivationTable.INSTANCE]
					+ " VARCHAR(512))");
		s.execute("CREATE INDEX IDX_CLASS ON Activation (type_name)");
		s.execute("CREATE INDEX IDX_THIS ON Activation (this_type)");
		s
			.execute("CREATE INDEX IDX_METHOD ON Activation (type_name, method_name, method_signature)");
		s.execute("CREATE INDEX IDX_THREAD ON Activation (thread_id)");
		s.execute("CREATE INDEX IDX_ARRIVAL ON Activation (arrival_id)");
		s
			.execute("CREATE VIEW TraceClass AS SELECT DISTINCT type_name FROM Activation AS type_name UNION (SELECT DISTINCT this_type FROM Activation AS type_name)");
		s
			.execute("CREATE VIEW Method AS SELECT DISTINCT type_name, method_name, method_signature FROM Activation");
		createTrigger(s, launchId);
		// System.out.println(launchId);
	}

	/**
	 * @param s
	 * @param launchId
	 * @throws SQLException
	 */
	private void createTrigger(Statement s, String launchId)
			throws SQLException {
		triggerName = launchId
			.replaceAll(
				"(\\s|[\\.\\-~\\!@#\\$;%^\\*\\(\\)\\+=\\{\\}\\[\\]\\|\\<\\>])",
				"_");
		triggerName = triggerName.toUpperCase();
		HSQLTrigger.registerWeakTrigger(triggerName, "ACTIVATION", dataTrigger);
		s
			.execute("CREATE TRIGGER "
					+ triggerName
					+ " AFTER INSERT ON ACTIVATION FOR EACH ROW CALL \"ca.uvic.chisel.hsqldb.server.HSQLTrigger\"");
	}

	/**
	 * @param launchId
	 */
	private void dropTrigger(Statement s, String launchId) {
		String triggerName = launchId.replace(" ", "_");
		triggerName = triggerName.replace(".", "_");
		triggerName = triggerName.toUpperCase();
		try {
			s.execute("DROP TRIGGER " + triggerName);
		} catch (SQLException e) {
		}
	}

	/**
	 * Commits data that is currently stored in views into tables so that they
	 * can be queried more quickly later. The database isn't committed after
	 * this call, clients should call {@link #commit()}.
	 * 
	 * @throws SQLException
	 */
	public synchronized void storeViews() throws SQLException {
		Statement s = getWriteConnection().createStatement();

		s.execute("SELECT * INTO TraceClassTemp FROM TraceClass");
		s.execute("SELECT * INTO MethodTemp FROM Method");
		// s.execute("SELECT * INTO TraceClassTemp FROM TraceClass");
		// s.execute("SELECT * INTO MethodTemp FROM MethodView");
		dropView("TraceClass");
		dropView("Method");
		s.execute("ALTER TABLE TraceClassTemp RENAME TO TraceClass");
		s.execute("ALTER TABLE MethodTemp RENAME TO Method");
		s.execute("CREATE INDEX IDX_METHOD_TYPE ON Method(type_name)");
		s.execute("CREATE INDEX IDX_CLASS_TYPE ON TraceClass(type_name)");
		// re-prepare the statements to match the tables.
		methodBySignatureStatement = new ExecutableQuery("SELECT type_name, method_name, method_signature from Method where type_name=? and method_name=? and method_signature=?");
		methodsByTypeStatement = new ExecutableQuery("select type_name, method_name, method_signature from Method where type_name=?");
//		ResultSet results2 = getWriteConnection().createStatement()
//			.executeQuery("Select type_name from TraceClass");
//		TreeSet<String> typeNames = new TreeSet<String>();
//		while (results2.next()) {
//			typeNames.add(results2.getString(1));
//		}
//		results2 = getWriteConnection().createStatement().executeQuery(
//			"Select DISTINCT type_name from Method");
//		while (results2.next()) {
//			typeNames.remove(results2.getString(1));
//		}
//		ResultSet results = getWriteConnection().createStatement().executeQuery("Select * from Method");
//		while (results.next()) {
//			String name = results.getString("method_name");
//			System.out.println(name);
//		}
	}

	/**
	 * @param string
	 */
	private void dropTable(String tableName) throws SQLException {
		try {
			getWriteConnection().createStatement().execute(
				"DROP TABLE " + tableName);
		} catch (SQLException e) {
			// just ignore it.
		}
	}

	private void dropView(String tableName) throws SQLException {
		try {
			getWriteConnection().createStatement().execute(
				"DROP VIEW " + tableName);
		} catch (SQLException e) {
			// just ignore it.
		}

	}

	/**
	 * Creates a new message. The order number of the messages is automatically
	 * updated.
	 * 
	 * @param kind
	 *            must be one of {@link #MESSAGE_KIND_ARRIVE},
	 *            {@link #MESSAGE_KIND_CALL}, {@link #MESSAGE_KIND_CATCH},
	 *            {@link #MESSAGE_KIND_CATCH}, {@link #MESSAGE_KIND_REPLY},
	 *            {@link #MESSAGE_KIND_RETURN}, or {@value #MESSAGE_KIND_THROW}
	 * @param activation_id
	 * @param opposite_id
	 *            the id of the opposite end of this message. May be null in the
	 *            case of an arrival.
	 * @param time
	 *            the time from the start of the trace (in milliseconds).
	 * @param codeLine
	 *            the line of code that this message refers to.
	 * @param sequence
	 *            (for calls, replys, and thows only) the sequence that this
	 *            call occurs in in the thread.
	 * @return the id of the newly created message.
	 * @throws SQLException
	 */
	public synchronized long createMessage(String kind, long activation_id,
			Long opposite_id, long time, int codeLine, String sequence)
			throws SQLException {
		/*
		 * "model_id", "kind", "activation_id", "opposite_id", "order", "time",
		 * "code_line", "sequence"
		 */
		String storedSequence = toStoredSequence(sequence);
		messageID++;
		createMessageStatement.setLong(1, messageID);
		createMessageStatement.setString(2, kind);
		createMessageStatement.setLong(3, activation_id);
		createMessageStatement.setObject(4, opposite_id);
		createMessageStatement.setLong(5, order_num++);
		createMessageStatement.setLong(6, time);
		createMessageStatement.setInt(7, codeLine);
		createMessageStatement.setString(8, storedSequence);
		createMessageStatement.execute();
		// incrementModelNumStatement.execute();
		return messageID;
	}

	/**
	 * Takes a string of the form 'x.y.z'... and translates it to a form
	 * that can be stored in the database.
	 * @param sequence
	 * @return
	 */
	public static String toStoredSequence(String sequence) {
		if (sequence == null) {
			return null;
		}
		long[] sequenceNumbers = splitSequence(sequence);
		if (sequenceNumbers == null) {
			return null;
		}
		return toStoredSequence(sequenceNumbers);
	}

	/**
	 * Translates the array of numbers to a string that can be stored in the
	 * database.
	 * @param sequenceNumbers
	 * @return
	 */
	public static String toStoredSequence(long[] sequenceNumbers) {
		StringBuilder builder = new StringBuilder();
		long _32bit = (long)Math.pow(2, 32);
		for (long x : sequenceNumbers) {
			long y = x+2;
			if (y > _32bit) {
				builder.append('\1');
				char a = (char)((y & 0xFFFF00000000L) >> 32);
				char b = (char)((y & 0xFFFF0000L) >> 16);
				char c = (char)(y & 0xFFFFL);
				builder.append(a);
				builder.append(b);
				builder.append(c);
			} else if (y > Character.MAX_VALUE) {
				builder.append('\0');
				char a = (char)((y & 0xFFFF0000L) >> 16);
				char b = (char)((y & 0xFFFF));
				builder.append(a);
				builder.append(b);
			} else {
				builder.append((char)y);
			}
		}
		return builder.toString();
	}
	
	/**
	 * Takes the stored sequence in storedSequence (from a database) and
	 * converts it to a list of numbers.
	 * @param storedSequence
	 * @return
	 */
	public static List<Long> fromStoredSequence(String storedSequence) {
		StringReader reader = new StringReader(storedSequence);
		LinkedList<Long> sequence = new LinkedList<Long>();
		int c = -1;
		try {
			while ((c = reader.read()) > -1) {
				if (c == 0) {
					//read the next two characters
					int a = reader.read();
					int b = reader.read();
					if (b < 0) {
						return null;
					}
					long x = (long)(((a << 16) + b)-2);
					sequence.add(x);
				} else if (c == 1) {
					//read the next two characters
					int x = reader.read();
					int y = reader.read();
					int z = reader.read();
					if (c < 0) {
						return null;
					}
					long w = (long)(((x << 32) + (y << 16) + z)-2);
					sequence.add(w);
				} else {
					sequence.add((long)(c-2));
				}
			}
		} catch (IOException e) {
			return null;
		}
		return sequence;
	}
	
	/**
	 * Joins the list of numbers as a dot-separated string.
	 * @param sequence
	 * @return
	 */
	public static String joinSequence(Collection<Long> sequence) {
		if (sequence == null) {
			return null;
		}
		StringBuilder builder = new StringBuilder();
		int i = 0;
		for (long l : sequence) {
			builder.append(l);
			i++;
			if (i < sequence.size()) {
				builder.append('.');
			}
		}
		return builder.toString();
	}
	
	/**
	 * Returns the stored sequence as a dot-separated string.
	 * @param storedSequence
	 * @return
	 */
	public static String fromStoredSequenceString(String storedSequence) {
		return joinSequence(fromStoredSequence(storedSequence));
	}
	

	/**
	 * @param sequence
	 * @return
	 */
	private static long[] splitSequence(String sequence) {
		if (sequence.isEmpty()) {
			return new long[0];
		}
		String[] split = sequence.split("\\.");
		long[] numbers = new long[split.length];
		for (int i = 0; i < split.length; i++) {
			try {
				numbers[i] = Long.parseLong(split[i]);
			} catch (NumberFormatException e) {
				return null;
			}
		}
		return numbers;
	}

	public synchronized long createThread(String thread_id, String thread_name,
			long first_arrival_id) throws SQLException {
		threadID++;
		createThreadStatement.setLong(1, threadID);
		createThreadStatement.setString(2, thread_id);
		createThreadStatement.setString(3, thread_name);
		createThreadStatement.setLong(4, first_arrival_id);
		createThreadStatement.execute();
		// incrementModelNumStatement.execute();
		return threadID;

	}

	public synchronized long createActivation(long arrival_id,
			String type_name, String method_name, String method_signature,
			long thread_id, String this_type, String instance)
			throws SQLException {
		activationID++;
		createActivationStatement.setLong(1, activationID);
		createActivationStatement.setLong(2, arrival_id);
		createActivationStatement.setString(3, type_name);
		createActivationStatement.setString(4, method_name);
		createActivationStatement.setString(5, method_signature);
		createActivationStatement.setLong(6, thread_id);
		createActivationStatement.setString(7, this_type);
		createActivationStatement.setString(8, instance);

		createActivationStatement.execute();
		// incrementModelNumStatement.execute();
		return activationID;
	}

	/**
	 * @param typeId
	 * @param modelID
	 * @param instance
	 * @param string
	 * @param i
	 * @throws SQLException
	 */
	public synchronized long createData(long typeId, long refId,
			String instance, String value, int order) throws SQLException {
		dataID++;
		createDataStatement.setLong(1, dataID);
		createDataStatement.setLong(2, typeId);
		createDataStatement.setLong(3, refId);
		createDataStatement.setString(4, instance);
		createDataStatement.setString(5, value);
		createDataStatement.setInt(6, order);
		createDataStatement.execute();
		return dataID;

	}

	/**
	 * @return
	 * @throws SQLException
	 */
	private long getLastIdentity() throws SQLException {
		// if (true) return 1;
		ResultSet results = getPortal().prepareCall(IDENTITY_CALL).executeQuery();
		if (results.next()) {
			return results.getLong(1);
		}
		return -1;
	}

	/**
	 * Forces a commit in the database.
	 * 
	 * @throws SQLException
	 *             if an error occurred in the commit
	 */
	public void commit() throws SQLException {
		createActivationStatement.commit();
		createDataStatement.commit();
		createEventStatement.commit();
		createMessageStatement.commit();
		createThreadStatement.commit();
		getWriteConnection().commit();
	}

	public void compact() throws SQLException {
		commit();
		// set a checkPoint
		getWriteConnection().createStatement().execute("CHECKPOINT DEFRAG");
	}

	public synchronized long nextMessageID() throws SQLException {
		return messageID + 1;
	}

	public synchronized long nextActivationID() throws SQLException {
		return activationID + 1;
	}

	public synchronized long createEvent(long time, String text)
			throws SQLException {
		eventID++;
		createEventStatement.setLong(1, eventID);
		createEventStatement.setLong(2, time);
		createEventStatement.setString(3, text);
		createEventStatement.execute();
		return eventID;
	}

	public void addTriggerListener(IDataTriggerListener listener) {
		dataTrigger.addDataListener(listener);
	}

	/**
	 * @param traceImpl
	 */
	public void removeTriggerListener(IDataTriggerListener listener) {
		dataTrigger.removeDataListener(listener);
	}

	private synchronized Connection getWriteConnection() throws SQLException {
		return getPortal().getDefaultConnection(false);
	}

	/**
	 * @throws SQLException
	 */
	private void prepareWriteStatements() throws SQLException {
		createMessageStatement = new ExecutableQuery(CREATE_MESSAGE_STATEMENT);
		createDataStatement = new ExecutableQuery(CREATE_DATA_STATEMENT);
		createActivationStatement = new ExecutableQuery(CREATE_ACTIVATION_STATEMENT);
		createEventStatement = new ExecutableQuery(CREATE_EVENT_STATEMENT);
		createThreadStatement = new ExecutableQuery(CREATE_THREAD_STATEMENT);
	}
	/* (non-Javadoc)
	 * @see ca.uvic.chisel.javasketch.data.internal.DataUtils#reset()
	 */
	@Override
	public void reset() {
		super.reset();
	}
	
	public void dispose() {
	}
}
