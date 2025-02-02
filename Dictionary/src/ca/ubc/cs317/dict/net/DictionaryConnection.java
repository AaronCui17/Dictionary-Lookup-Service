package ca.ubc.cs317.dict.net;

import ca.ubc.cs317.dict.model.Database;
import ca.ubc.cs317.dict.model.Definition;
import ca.ubc.cs317.dict.model.MatchingStrategy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;

/**
 * Created by Jonatan on 2017-09-09.
 */
public class DictionaryConnection {
    private static final int DEFAULT_PORT = 2628;
    Socket socket;
    BufferedReader reader;
    PrintWriter writer;

    /** Establishes a new connection with a DICT server using an explicit host and port number, and handles initial
     * welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @param port Port number used by the DICT server
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host, int port) throws DictConnectionException {
        try {
            // Create a socket to connect to the DICT server
            socket = new Socket(host, port);

            // Get input and output streams
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);

            // Process initial welcome message from the server
            String welcomeMessage = reader.readLine();

            // Check if the welcome message matches the expected value
            if (!welcomeMessage.startsWith("220")) {
                throw new DictConnectionException("Unexpected welcome message from the server");
            }

        } catch (IOException e) {
            // Handle IOException, for example, if the host does not exist or the connection can't be established
            throw new DictConnectionException("Error establishing connection", e);
        }
    }

    /** Establishes a new connection with a DICT server using an explicit host, with the default DICT port number, and
     * handles initial welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host) throws DictConnectionException {
        this(host, DEFAULT_PORT);
    }

    /** Sends the final QUIT message and closes the connection with the server. This function ignores any exception that
     * may happen while sending the message, receiving its reply, or closing the connection.
     *
     */
    public synchronized void close() {
        try {
            // Send the QUIT command to the server
            writer.println("QUIT");

            // Receive and print the server's response
            String quitResponse = reader.readLine();
            System.out.println("Server response to QUIT: " + quitResponse);

            // Close the streams and socket
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null) socket.close();

        } catch (IOException e) {
            // Ignore any exceptions that occur while sending the message, receiving its reply, or closing the connection
            e.printStackTrace();
        }
    }

    /** Requests and retrieves all definitions for a specific word.
     *
     * @param word The word whose definition is to be retrieved.
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 definitions in the first database that has a definition for the word should be used
     *                 (database '!').
     * @return A collection of Definition objects containing all definitions returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Collection<Definition> getDefinitions(String word, Database database) throws DictConnectionException {
        Collection<Definition> set = new ArrayList<>();

        try {
            // Get information with DICT command
            String command = "DEFINE " + database.getName() + " " + word;
            writer.println(command);

            // Returns empty collection if no definitions or invalid database
            Status status = Status.readStatus(reader);
            if (status.isNegativeReply()) {
                return set;
            }

            // Process the server's response
            String responseLine;
            while (!(responseLine = reader.readLine()).startsWith("250")) {

                // 151 indicates the start of a new definition
                if (responseLine.startsWith("151")) {
                    System.out.println(responseLine);

                    // Parse to get database name
                    String[] parts = DictStringParser.splitAtoms(responseLine);
                    Definition definition = new Definition(word, parts[2]);

                    // Process definition
                    StringBuilder sb = new StringBuilder();
                    while (!(responseLine = reader.readLine()).equals(".")) {
                        sb.append(responseLine).append(System.lineSeparator());
                    }
                    definition.setDefinition(sb.toString().trim());

                    set.add(definition);
                }
            }

        } catch (IOException e) {
            // Handle IOException, for example, if the connection was interrupted
            throw new DictConnectionException("Error getting definitions", e);
        }

        return set;
    }

    /** Requests and retrieves a list of matches for a specific word pattern.
     *
     * @param word     The word whose definition is to be retrieved.
     * @param strategy The strategy to be used to retrieve the list of matches (e.g., prefix, exact).
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 matches in the first database that has a match for the word should be used (database '!').
     * @return A set of word matches returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<String> getMatchList(String word, MatchingStrategy strategy, Database database) throws DictConnectionException {
        Set<String> set = new LinkedHashSet<>();

        try {
            // Get information with DICT command
            String command = "MATCH " + database.getName() + " " + strategy.getName() + " " + word;
            writer.println(command);

            // Return empty list if no matches or invalid database/strategy
            Status status = Status.readStatus(reader);
            if (status.isNegativeReply()) {
                return set;
            }

            // Process the server's response
            String responseLine;
            while (!(responseLine = reader.readLine()).startsWith("250")) {

                // Only process lines that are matches
                if (responseLine.contains("\"")) {
                    String[] parts = DictStringParser.splitAtoms(responseLine);
                    set.add(parts[1]);
                }
            }

        } catch (IOException e) {
            // Handle IOException, for example, if the connection was interrupted
            throw new DictConnectionException("Error getting match list", e);
        }

        return set;
    }

    /** Requests and retrieves a map of database name to an equivalent database object for all valid databases used in the server.
     *
     * @return A map of Database objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Map<String, Database> getDatabaseList() throws DictConnectionException {
        Map<String, Database> databaseMap = new HashMap<>();

        try {
            // Get information with DICT command
            writer.println("SHOW DB");

            // Return empty map if no databases
            Status status = Status.readStatus(reader);
            if (status.isNegativeReply()) {
                return databaseMap;
            }

            // Process the server's response
            String responseLine;
            while (!(responseLine = reader.readLine()).startsWith("250")) {

                // Only process lines that are databases
                if (responseLine.contains("\"")) {
                    String[] parts = DictStringParser.splitAtoms(responseLine);
                    Database database = new Database(parts[0], parts[1]);
                    databaseMap.put(database.getName(), database);
                }
            }

        } catch (IOException e) {
            // Handle IOException, for example, if the connection was interrupted
            throw new DictConnectionException("Error getting database list", e);
        }

        return databaseMap;
    }

    /** Requests and retrieves a list of all valid matching strategies supported by the server.
     *
     * @return A set of MatchingStrategy objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<MatchingStrategy> getStrategyList() throws DictConnectionException {
        Set<MatchingStrategy> set = new LinkedHashSet<>();

        try {
            // Get information with DICT command
            writer.println("SHOW STRAT");

            // Return empty list if no strategies
            Status status = Status.readStatus(reader);
            if (status.isNegativeReply()) {
                return set;
            }

            // Process the server's response
            String responseLine;
            while (!(responseLine = reader.readLine()).startsWith("250")) {

                // Only process lines that are strategies
                if (responseLine.contains("\"")) {
                    String[] parts = DictStringParser.splitAtoms(responseLine);
                    MatchingStrategy strategy = new MatchingStrategy(parts[0], parts[1]);
                    set.add(strategy);
                }
            }

        } catch (IOException e) {
            // Handle IOException, for example, if the connection was interrupted
            throw new DictConnectionException("Error getting strategy list", e);
        }

        return set;
    }

    /** Requests and retrieves detailed information about the currently selected database.
     *
     * @return A string containing the information returned by the server in response to a "SHOW INFO <db>" command.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized String getDatabaseInfo(Database d) throws DictConnectionException {
	    StringBuilder sb = new StringBuilder();

        try {
            // Get information with DICT command
            writer.println("SHOW INFO " + d.getName());

            // Throw exception if invalid database
            Status status = Status.readStatus(reader);
            if (status.isNegativeReply()) {
                String code = String.valueOf(status.getStatusCode());
                String details = status.getDetails();
                throw new DictConnectionException(code + ": " + details);
            }

            // Process the server's response
            String responseLine;
            while (!(responseLine = reader.readLine()).startsWith("250")) {

                // Append the response to the StringBuilder
                if (!responseLine.equals(".")) {
                    sb.append(responseLine).append(System.lineSeparator());
                }
            }

        } catch (IOException e) {
            // Handle IOException, for example, if the connection was interrupted
            throw new DictConnectionException("Error getting database info", e);
        }

        return sb.toString();
    }
}