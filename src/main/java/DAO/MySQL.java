package DAO;

import appointmentManager.Customer;
import appointmentManager.Appointment;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.TimeZone;

public class MySQL {
    private final String DB_CONN_PATH = "/db.properties";
    private Connection conn;
    private PreparedStatement ps;
    private ResultSet rs;

    // A no arg Constructor that opens a connection to the database.
    public MySQL() {
        try {
            Properties connectionProps = readPropertiesFile(DB_CONN_PATH);
            conn = DriverManager.getConnection(
                    "jdbc:mysql://" + connectionProps.getProperty("server") + "/" + connectionProps.getProperty("database"),
                    connectionProps.getProperty("username"), connectionProps.getProperty("password")
            );
        } catch(SQLException e) {
            e.printStackTrace();
            //System.out.println("Unable to connect to Database.");
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    // Get all customers from the database, parsing each into a Customer object.
    public ObservableList<Customer> getAllCustomers() {
        ObservableList<Customer> returnList = FXCollections.observableArrayList();

        try {
            ps = conn.prepareStatement(
                    "SELECT c.customerId, c.customerName, a.address, a.address2, ci.city, co.country, a.postalCode, a.phone" +
                    "           FROM customer AS c, address AS a, city AS ci, country AS co" +
                    "               WHERE c.addressId = a.addressId AND" +
                    "                   a.cityId = ci.cityId AND" +
                    "                   ci.countryId = co.countryId"
            );

            rs = ps.executeQuery();

            while (rs.next()) {
                returnList.add(
                        new Customer(
                                rs.getInt("customerId"),
                                rs.getString("customerName"),
                                rs.getString("address"),
                                rs.getString("address2"),
                                rs.getString("city"),
                                rs.getString("country"),
                                rs.getString("postalCode"),
                                rs.getString("phone")
                        )
                );
            }
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        }

        return returnList;
    }

    // Get all appointments from the database, within the date range specified, and parse each into an Appointment object.
    public ObservableList<Appointment> getAppointmentsInRange(LocalDate start, LocalDate end, int userId) {
        DateTimeFormatter dtfSqlDate = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        ObservableList<Appointment> returnList = FXCollections.observableArrayList();

        try {
            ps = conn.prepareStatement(
                    "SELECT a.appointmentId, c.customerName, a.userId, a.title, a.description, a.location, a.contact, a.type, a.url, a.start, end" +
                            "   FROM appointment AS a, customer AS c" +
                            "   WHERE a.customerId = c.customerId AND ((a.start BETWEEN ? AND ?) OR (a.end BETWEEN ? AND ?)) AND a.userId = ?"
            );
            ps.setString(1, start.format(dtfSqlDate));
            ps.setString(2, end.format(dtfSqlDate));
            ps.setString(3, start.format(dtfSqlDate));
            ps.setString(4, end.format(dtfSqlDate));
            ps.setInt(5, userId);

            rs = ps.executeQuery();

            while (rs.next()) {
                returnList.add(resultSetToAppointment(rs));
            }
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        }

        return returnList;
    }

    // Check if this user has any Appointments occurring within the next 15 minutes.
    public Appointment checkForUpcomingAppointment(Integer userId) {
        Appointment appointment = null;
        try {
            ps = conn.prepareStatement(
                    "SELECT a.appointmentId, c.customerName, a.userId, a.title, a.description, a.location, a.contact, a.type, a.url, a.start, end " +
                            "FROM appointment AS a, customer AS c " +
                            "WHERE (a.start BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL 15 MINUTE)) AND a.userId = ?"
            );
            ps.setInt(1, userId);

            rs = ps.executeQuery();
            if(rs.next()) {
                appointment = resultSetToAppointment(rs);
            }
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        }

        return appointment;
    }

    public Appointment checkForOverlappingAppointment(LocalDateTime ldtStart, LocalDateTime ldtEnd, int userId) {
        Appointment returnAppointment = null;
        try {
            ps = conn.prepareStatement(
                    "SELECT  a.appointmentId, c.customerName, a.userId, a.title, a.description, a.location, a.contact, a.type, a.url, a.start, end \n" +
                            "\tFROM appointment AS a, customer AS c \n" +
                            "\tWHERE (a.customerId = c.customerId) \n" +
                            "    AND (userId = ?) \n" +
                            "    AND (\n" +
                            "\t\tstart < ?\n" +
                            "\t\tAND end > ?\n" +
                            "\t) \n" +
                            "    OR \n" +
                            "    (\n" +
                            "\t\t(start between ? AND ?) \n" +
                            "        OR \n" +
                            "        (end between ? AND ?)\n" +
                            "\t)\n" +
                            "    LIMIT 1;"
            );

            ps.setInt(1, userId);
            ps.setString(2,
                convertTimeZone(ldtStart, TimeZone.getDefault().toZoneId(), ZoneOffset.UTC)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );
            ps.setString(3,
                    convertTimeZone(ldtEnd, TimeZone.getDefault().toZoneId(), ZoneOffset.UTC)
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );
            ps.setString(4,
                    convertTimeZone(ldtStart, TimeZone.getDefault().toZoneId(), ZoneOffset.UTC)
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );
            ps.setString(5,
                    convertTimeZone(ldtEnd, TimeZone.getDefault().toZoneId(), ZoneOffset.UTC)
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );

            ps.setString(6,
                    convertTimeZone(ldtStart, TimeZone.getDefault().toZoneId(), ZoneOffset.UTC)
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );
            ps.setString(7,
                    convertTimeZone(ldtEnd, TimeZone.getDefault().toZoneId(), ZoneOffset.UTC)
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );

            System.out.println(ps);

            rs = ps.executeQuery();

            if (rs.next()) {
                do {
                    returnAppointment = resultSetToAppointment(rs);
                } while (rs.next());
            }
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        }

        return returnAppointment;
    }

    // Check if the username and password combo exist in the database.
    public Integer checkUser(String username, String password) throws SQLException {
        ps = conn.prepareStatement("SELECT userId, password FROM user WHERE username = ?");
        ps.setString(1, username);
        ResultSet rs = ps.executeQuery();

        if(rs.next()) {
            if(password.equals(rs.getString("password"))) {
                return rs.getInt("userId");
            }
        }

        return null;
    }

    // Get the username associated to the referenced userId
    public String getUsername(int userId) throws SQLException {
        String username = null;
        ps = conn.prepareStatement("SELECT userName FROM user WHERE userId = ?");
        ps.setInt(1, userId);
        rs = ps.executeQuery();

        while(rs.next()) {
           username = rs.getString("username");
        }

        return username;
    }

    // Get all Appointments and group them by type. This is used for the "Appointments By Type report."
    public String getAppointmentsByType() throws SQLException {
        StringBuilder alertBody = new StringBuilder();
        ps = conn.prepareStatement(
                "SELECT type, COUNT(type) AS Count from appointment" +
                        "   WHERE MONTH(start) = ?" +
                        "   GROUP BY type;"
        );

        ps.setInt(1, LocalDate.now().getMonth().getValue());
        rs = ps.executeQuery();

        while (rs.next()) {
            alertBody.append(rs.getString("type")).append(":  ").append(rs.getInt("Count")).append("\r\n");
        }
        return alertBody.toString();
    }

    // If a country with this name already exists, grab it's ID, else create it and return the newly created ID
    public int insertCountry(String countryName, String username) throws SQLException {
        int countryId = 0;
        ps = conn.prepareStatement("SELECT countryId FROM country WHERE country = ? LIMIT 1");
        ps.setString(1, countryName);
        rs = ps.executeQuery();

        if (rs.next()) {
            countryId = rs.getInt("countryId");
        } else {
            ps = conn.prepareStatement(
                    "INSERT INTO country (country, createDate, createdBy, lastUpdateBy)" +
                            "    VALUES(?, ?, ?, ?)"
            );
            ps.setString(1, countryName);
            ps.setString(2, LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            ps.setString(3, username);
            ps.setString(4, username);
            ps.execute();

            ps = conn.prepareStatement("SELECT countryId FROM country WHERE country = ? LIMIT 1");
            ps.setString(1, countryName);
            rs = ps.executeQuery();
            rs.next();
            countryId = rs.getInt("countryId");
        }

        return countryId;
    }

    // If a city with this name already exists, grab it's ID, else create it and return the newly created ID
    public int insertCity(String cityName, int countryId, String username) throws SQLException {
        int cityId = 0;
        ps = conn.prepareStatement("SELECT cityId FROM city WHERE city = ? LIMIT 1");
        ps.setString(1, cityName);
        rs = ps.executeQuery();

        if (rs.next()) {
            cityId = rs.getInt("cityId");
        } else {
            ps = conn.prepareStatement(
                    "INSERT INTO city (city, countryId, createDate, createdBy, lastUpdateBy)" +
                            "    VALUES(?, ?, ?, ?, ?)"
            );
            ps.setString(1, cityName);
            ps.setInt(2, countryId);
            ps.setString(3, LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            ps.setString(4, username);
            ps.setString(5, username);
            ps.execute();

            ps = conn.prepareStatement("SELECT cityId FROM city WHERE city = ? LIMIT 1");
            ps.setString(1, cityName);
            rs = ps.executeQuery();
            rs.next();
            cityId = rs.getInt("cityId");
        }

        return cityId;
    }

    // If an address with these details already exists, grab it's ID, else create it and return the newly created ID
    public int insertAddress(String address, String address2, int cityId, String postalCode, String phone, String username) throws SQLException {
        int addressId = 0;
        ps = conn.prepareStatement(
                "SELECT addressId FROM address " +
                        "    WHERE address = ? AND address2 = ? AND cityId = ? AND postalCode = ? AND phone = ?" +
                        "    LIMIT 1"
        );
        ps.setString(1, address);
        ps.setString(2, address);
        ps.setInt(3, cityId);
        ps.setString(4, postalCode);
        ps.setString(5, phone);
        rs = ps.executeQuery();

        if (rs.next()) {
            addressId = rs.getInt("addressId");
        } else {
            ps = conn.prepareStatement(
                    "INSERT INTO address (address, address2, cityId, postalCode, phone, createDate, createdBy, lastUpdateBy)" +
                            "    VALUES(?, ?, ?, ?, ?, ?, ?, ?)"
            );
            ps.setString(1, address);
            ps.setString(2, address2);
            ps.setInt(3, cityId);
            ps.setString(4, postalCode);
            ps.setString(5, phone);
            ps.setString(6, LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            ps.setString(7, username);
            ps.setString(8, username);
            ps.execute();

            ps = conn.prepareStatement(
                    "SELECT addressId FROM address " +
                            "    WHERE address = ? AND address2 = ? AND cityId = ? AND postalCode = ? AND phone = ?" +
                            "    LIMIT 1"
            );
            ps.setString(1, address);
            ps.setString(2, address2);
            ps.setInt(3, cityId);
            ps.setString(4, postalCode);
            ps.setString(5, phone);
            rs = ps.executeQuery();

            rs.next();
            addressId = rs.getInt("addressId");
        }

        return addressId;
    }

    // Create a new Customer using the data passed.
    public void insertCustomer(String name, int addressId, String username) throws SQLException {
        ps = conn.prepareStatement(
                "INSERT INTO customer (customerName, addressId, active, createDate, createdBy, lastUpdateBy)" +
                        "   VALUES(?, ?, ?, ?, ?, ?)"
        );
        ps.setString(1, name);
        ps.setInt(2, addressId);
        ps.setBoolean(3, true);
        ps.setString(4, LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        ps.setString(5, username);
        ps.setString(6, username);
        ps.execute();
    }

    // Create a new Appointment in the database.
    public void insertAppointment(String customerName, int userId, String title, String description, String location,
                                  String contact, String type, String url, LocalDateTime ldtStart, LocalDateTime ldtEnd, String username) throws SQLException {
        ps = conn.prepareStatement(
                "INSERT INTO appointment (customerId, userId, title, description, location, contact, type, url, start, end, createDate, createdBy, lastUpdateBy)" +
                        "    VALUES ((SELECT customerId FROM customer WHERE customerName = ?), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        );

        ps.setString(1, customerName);
        ps.setInt(2, userId);
        ps.setString(3, title);
        ps.setString(4, description);
        ps.setString(5, location);
        ps.setString(6, contact);
        ps.setString(7, type);
        ps.setString(8, url);
        ps.setString(9,
                convertTimeZone(ldtStart, TimeZone.getDefault().toZoneId(), ZoneOffset.UTC)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
        ps.setString(10,
                convertTimeZone(ldtEnd, TimeZone.getDefault().toZoneId(), ZoneOffset.UTC)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
        ps.setString(11, LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        ps.setString(12, username);
        ps.setString(13, username);
        ps.execute();
    }

    // Update the countryName for the id provided.
    public void updateCountry(String countryName, int countryId, String username) throws SQLException {
        ps = conn.prepareStatement("UPDATE country SET country = ?, lastUpdateBy = ? WHERE countryId = ?");
        ps.setString(1, countryName);
        ps.setString(2, username);
        ps.setInt(3, countryId);
        ps.execute();
    }

    // Update the cityName for the id provided.
    public void updateCity(String cityName, int cityId, String username) throws SQLException {
        ps = conn.prepareStatement("UPDATE city SET city = ?, lastUpdateBy = ? WHERE cityId = ?");
        ps.setString(1, cityName);
        ps.setString(2, username);
        ps.setInt(3, cityId);
        ps.execute();
    }

    // Update the address details for the id provided.
    public void updateAddress(String address, String address2, String postalCode, String phone, int addressId, String username) throws SQLException {
        ps = conn.prepareStatement("UPDATE address SET address = ?, address2 = ?, postalCode = ?, phone = ?, lastUpdateBy = ? WHERE addressId = ?");
        ps.setString(1, address);
        ps.setString(2, address2);
        ps.setString(3, postalCode);
        ps.setString(4, phone);
        ps.setString(5, username);
        ps.setInt(6, addressId);
        ps.execute();
    }

    // Update the Customer details for the id provided.
    public void updateCustomer(String customerName, int customerId, String username) throws SQLException {
        ps = conn.prepareStatement("UPDATE customer SET customerName = ?, lastUpdateBy = ? WHERE customerId = ?");
        ps.setString(1, customerName);
        ps.setString(2, username);
        ps.setInt(3, customerId);
        ps.execute();
    }

    // Update the Appointment details for the id provided.
    public void updateAppointment(int appointmentId, String customerName, int userId, String title, String description, String location,
                                  String contact, String type, String url, LocalDateTime ldtStart, LocalDateTime ldtEnd, String username) throws SQLException {
        ps = conn.prepareStatement(
                "UPDATE appointment " +
                        "    SET customerId = (SELECT customerId FROM customer WHERE customerName = ?)," +
                        "    userId = ?, " +
                        "    title = ?, " +
                        "    description = ?," +
                        "    location = ?," +
                        "    contact = ?," +
                        "    type = ?," +
                        "    url = ?," +
                        "    start = ?," +
                        "    end = ?," +
                        "    lastUpdateBy = ?" +
                        "        WHERE appointmentId = ?"
        );
        ps.setString(1, customerName);
        ps.setInt(2, userId);
        ps.setString(3, title);
        ps.setString(4, description);
        ps.setString(5, location);
        ps.setString(6, contact);
        ps.setString(7, type);
        ps.setString(8, url);
        ps.setString(9,
                convertTimeZone(ldtStart, TimeZone.getDefault().toZoneId(), ZoneOffset.UTC)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
        ps.setString(10,
                convertTimeZone(ldtEnd, TimeZone.getDefault().toZoneId(), ZoneOffset.UTC)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
        ps.setString(11, username);
        ps.setInt(12, appointmentId);
        ps.execute();
    }

    // Get a list of all usernames in the database.
    public ObservableList<String> getUsernames() throws SQLException {
        ObservableList<String> returnList = FXCollections.observableArrayList();

        rs = conn.prepareStatement("SELECT username FROM user").executeQuery();
        while (rs.next()) {
            returnList.add(rs.getString("username"));
        }

        return returnList;
    }

    // Get all appointments that are assigned to the username provided.
    public ObservableList<Appointment> getConsultantReport(String username) throws SQLException {
        ObservableList<Appointment> returnList = FXCollections.observableArrayList();

        ps = conn.prepareStatement(
                "SELECT a.appointmentId, c.customerName, a.userId, a.title, a.description, a.location, a.contact, a.type, a.url, a.start, end " +
                        "FROM appointment AS a, customer AS c, user AS u " +
                        "WHERE u.userName = ? AND a.userId = u.userId AND a.customerId = c.customerId"
        );
        ps.setString(1, username);
        rs = ps.executeQuery();

        while (rs.next()) {
            returnList.add(resultSetToAppointment(rs));
        }

        return returnList;
    }

    // Get a unique list of all contacts that appear in the Appointments table.
    public ObservableList<String> getUniqueContacts() throws SQLException {
        ObservableList<String> returnList = FXCollections.observableArrayList();

        rs = conn.prepareStatement("SELECT DISTINCT contact from appointment;").executeQuery();
        while (rs.next()) {
            returnList.add(rs.getString("contact"));
        }

        return returnList;
    }

    // Get all Appointments that have the passed contact.
    public ObservableList<Appointment> getContactReport(String contactName) throws SQLException {
        ObservableList<Appointment> returnList = FXCollections.observableArrayList();
        ps = conn.prepareStatement(
                "SELECT a.appointmentId, c.customerName, a.userId, a.title, a.description, a.location, a.contact, a.type, a.url, a.start, end " +
                        "FROM appointment AS a, customer AS c " +
                        "WHERE a.contact = ? AND a.customerId = c.customerId"
        );
        ps.setString(1, contactName);
        rs = ps.executeQuery();

        while (rs.next()) {
            returnList.add(resultSetToAppointment(rs));
        }

        return returnList;
    }

    // Get a cascading list of all IDs associated to a Customer.
    public ResultSet getIdsForCustomer(int customerId) throws SQLException{
        ps = conn.prepareStatement(
                "SELECT cs.customerId, ad.addressId, ci.cityId, co.countryId" +
                        "    FROM customer AS cs, address AS ad, city AS ci, country AS co " +
                        "    WHERE cs.customerId =  ? AND ad.addressId = cs.addressId AND ci.cityId = ad.cityId AND co.countryId = ci.countryId" +
                        "    LIMIT 1"
        );
        ps.setInt(1, customerId);
        return ps.executeQuery();
    }

    // Delete the referenced Appointment.
    public void deleteAppointment(int appointmentId) throws SQLException {
        ps = conn.prepareStatement("DELETE FROM appointment WHERE appointmentId = ?");
        ps.setInt(1, appointmentId);
        ps.execute();
    }

    // Delete the referenced Customer.
    public void deleteCustomer(int customerId) throws SQLException {
        ps = conn.prepareStatement("DELETE FROM customer WHERE customerId = ?");
        ps.setInt(1, customerId);
        ps.execute();
    }

    // Close all open connections, if any.
    public void close() {
        try {
            if (rs != null) {
                rs.close();
            }

            if (ps != null) {
                ps.close();
            }

            if (conn != null) {
                conn.close();
            }
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        }
    }

    // This is used to get the contents of the db.properties file.
    private Properties readPropertiesFile(String fileName) throws IOException {
        //FileInputStream fis = null;
        Properties prop = null;
        InputStream is = getClass().getResourceAsStream(fileName);
        try {
            //fis = new FileInputStream(getClass().getResource(fileName).toExternalForm());
            prop = new Properties();
            prop.load(is);
        } catch(FileNotFoundException fnfe) {
            fnfe.printStackTrace();
        } finally {
            is.close();
        }
        return prop;
    }

    // Used to parse a Result Set into a useable Appointment object.
    private Appointment resultSetToAppointment(ResultSet rs) throws SQLException {
        return new Appointment(
                rs.getInt("appointmentId"),
                rs.getString("customerName"),
                rs.getInt("userId"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("location"),
                rs.getString("contact"),
                rs.getString("type"),
                rs.getString("url"),
                convertTimeZone(LocalDateTime.parse(rs.getString("start") , DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), ZoneOffset.UTC, TimeZone.getDefault().toZoneId()),
                convertTimeZone(LocalDateTime.parse(rs.getString("end") , DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), ZoneOffset.UTC, TimeZone.getDefault().toZoneId())
        );
    }

    // Used to convert between two timezones.
    public ZonedDateTime convertTimeZone(LocalDateTime ldt, ZoneId originZID, ZoneId desiredZID) {
        return ZonedDateTime
                .now(originZID)
                .with(ldt)
                .withZoneSameInstant(desiredZID);
    }
}
