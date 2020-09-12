package appointmentManager;

import DAO.MySQL;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.io.*;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/*
    Author: Jonathan Knight
    Course: C195
    Date: 5-12-2020

    Requirements Explanations:

    A)  The displayLogin() method displays the login form and handles all error control, including checking for
        non-existent or incorrect username/password combos. This form will also change language to English or Spanish
        depending upon your system language. For easy testing, I also included a combobox to toggle between languages.

    B)  This is handled by the displayCustomers() and displayViewCustomer() methods.

    C)  This is handled via buttons on the main form, which call the displayAppointment() method.

    D)  This is provided by the cbViewAppointments combobox on the main form. When the combobox selection changes, the
        calendar view is altered to either viewing appointments by month or week.

    E)  This is accomplished when we query any appointments from the database using the DAO.MySQL.resultSetToAppointment()
        method, which calls the DAO.MySQL.convertTimeZone() method. All dates/times are converted to the user's system default timezone.

    F)  I opted to implement the following examples of exception control:
            * scheduling an appointment outside business hours
                (Business hours are defined as constants below in the Main class, and checked by an if statement in displayAppointment())
            * entering nonexistent or invalid customer data
                (This is handled by multiple if statements and regex checks in the displayViewCustomer() method.)
            * entering an incorrect username and password
                (This is handled by an if statement that leverages the DAO.MySQL.checkUser() method.)

    G)  I have used multiple lambdas throughout the program in event handlers/listeners and .foreach() methods. I have
        included an inline comment by each.

    H)  This is handled using the DAO.MySQL.checkForUpcomingAppointment() method inside the displayLogin() method.

    I)  These are each queried via three buttons on the main form.

    J)  Every time a user logs into the system, the appendLogHistory() method is called from the displayLogin() method
        and logs the details. The log file is named LoginHistory.txt and can be seen in the root of the project directory.

    K)  I'm going to hope this explanation and my program is capable of meeting this requirement.

 */

public class Main extends Application {
    // Define the business hours. No appointments can be created outside these hours.
    final LocalTime BUSINESS_HOURS_START = LocalTime.parse("09:00 AM", DateTimeFormatter.ofPattern("HH:mm a"));
    final LocalTime BUSINESS_HOURS_END = LocalTime.parse("06:00 PM", DateTimeFormatter.ofPattern("hh:mm a"));
    // This is the DAO object that will allow us to interact with the database.
    MySQL conn = new MySQL();
    // This object allows us easy access to the currently logged in user's data everywhere in the program.
    User currentUser;
    // As mutiple alerts are used throughout the program, it became more efficient to define a global object and customize
    // the message as necessary.
    Alert alert = new Alert(Alert.AlertType.NONE);
    // This is used on the main form to filter dates to the specified timespan. By default, we initialize the start and
    // end dates to the first and last days of the current month, respectively.
    // These are initialized as single item arrays to allow for interaction inside of lambdas thanks to the pass-by-reference nature of arrays.
    LocalDate[] startDate = {LocalDate.now().withDayOfMonth(1)};
    LocalDate[] endDate = {startDate[0].with(TemporalAdjusters.lastDayOfMonth())};

    @Override
    public void start(Stage primaryStage) {
        // Display the login form and store the resulting user in the global variable for easy access later.
        currentUser = displayLogin();

        // This if statement prevents the main form from loading if the login form is manually closed prior to login.
        if(null != currentUser.getUsername()) {
            // Define/initialize controls and necessary variables for the main form
            GridPane gpRoot = new GridPane();
            Scene scene = new Scene(gpRoot, 775, 500);
            ObservableList<String> olViewAppointments = FXCollections.observableArrayList(
                    "Month",
                    "Week"
            );
            Button btnViewCustomers = new Button(getLocaleString(Locale.getDefault(), "Calendar", "btnViewCustomers"));
            Label lblViewAppointments = new Label("View Appointments By:");
            ComboBox<String> cbViewAppointments = new ComboBox<>(olViewAppointments);
            Button btnNext = new Button("Next");
            Button btnLast = new Button("Previous");
            Button btnNewAppointment = new Button("New Appointment");
            Button btnModifyAppointment = new Button("Modify Appointment");
            Button btnDeleteAppointment = new Button("Delete Appointment");
            Label lblReports = new Label("Reports:");
            Button btnReportType = new Button("Appointments By Month");
            Button btnConsultantReport = new Button("Appointments By User");
            Button btnContactReport = new Button("Appointments By Contact");
            TableView<Appointment> tvAppointment = buildAppointmentTable();
            Label lblDateRange = new Label(startDate[0].format(DateTimeFormatter.ofPattern("MM/d/yyyy")) + " - " + endDate[0].format(DateTimeFormatter.ofPattern("MM/d/yyyy")));

            scene.getStylesheets().add(getClass().getResource("/root.css").toExternalForm());

            // Set layout and style for the main GridPane
            gpRoot.getStyleClass().add("gridpane");
            gpRoot.add(new VBox(20, btnNewAppointment, btnModifyAppointment, btnDeleteAppointment, btnViewCustomers, new VBox(10, lblViewAppointments, cbViewAppointments)), 0, 0);
            gpRoot.add(new VBox(20, lblReports, btnReportType, btnConsultantReport, btnContactReport), 0, 1);
            gpRoot.add(new VBox(10, tvAppointment, new HBox(10, btnLast, lblDateRange, btnNext)), 1, 0);

            // Initialize the Appointments TableView to the default timespan.
            tvAppointment.setItems(conn.getAppointmentsInRange(startDate[0], endDate[0], currentUser.getId()));
            tvAppointment.setMaxHeight(250);

            // Auto select the first option in the combobox to avoid blanks.
            cbViewAppointments.getSelectionModel().select(0);
            // When the appointments view is changed, update the timespan and view accordingly.
            cbViewAppointments.getSelectionModel().selectedItemProperty().addListener((options, oldValue, newValue) -> {
                if (newValue.equals("Month")) {
                    startDate[0] = LocalDate.now().withDayOfMonth(1);
                    endDate[0] = startDate[0].with(TemporalAdjusters.lastDayOfMonth());
                } else if (newValue.equals("Week")) {
                    startDate[0] = LocalDate.now().with(DayOfWeek.MONDAY);
                    endDate[0] = startDate[0].plusDays(7);
                }

                lblDateRange.setText(startDate[0].format(DateTimeFormatter.ofPattern("MM/d/yyyy")) + " - " + endDate[0].format(DateTimeFormatter.ofPattern("MM/d/yyyy")));
                tvAppointment.setItems(conn.getAppointmentsInRange(startDate[0], endDate[0], currentUser.getId()));
            });

            // When the "Last" button is clicked, decrease the timespan by the currently desired amount.
            btnLast.setOnAction(e -> {
                if (cbViewAppointments.getValue().equals("Month")) {
                    startDate[0] = startDate[0].minusMonths(1);
                    endDate[0] = endDate[0].minusMonths(1);
                } else if (cbViewAppointments.getValue().equals("Week")) {
                    startDate[0] = startDate[0].minusDays(7);
                    endDate[0] = endDate[0].minusDays(7);
                }

                lblDateRange.setText(startDate[0].format(DateTimeFormatter.ofPattern("MM/d/yyyy")) + " - " + endDate[0].format(DateTimeFormatter.ofPattern("MM/d/yyyy")));
                tvAppointment.setItems(conn.getAppointmentsInRange(startDate[0], endDate[0], currentUser.getId()));
            });

            // When the "Next" button is clicked, increase the timespan by the currently desired amount.
            btnNext.setOnAction(e -> {
                if (cbViewAppointments.getValue().equals("Month")) {
                    startDate[0] = startDate[0].plusMonths(1);
                    endDate[0] = endDate[0].plusMonths(1);
                } else if (cbViewAppointments.getValue().equals("Week")) {
                    startDate[0] = startDate[0].plusDays(7);
                    endDate[0] = endDate[0].plusDays(7);
                }

                lblDateRange.setText(startDate[0].format(DateTimeFormatter.ofPattern("MM/d/yyyy")) + " - " + endDate[0].format(DateTimeFormatter.ofPattern("MM/d/yyyy")));
                tvAppointment.setItems(conn.getAppointmentsInRange(startDate[0], endDate[0], currentUser.getId()));
            });

            // Display the new appointment form.
            btnNewAppointment.setOnAction(e -> {
                tvAppointment.setItems(displayAppointment(null));
            });

            // If user has selected an appointment from the TableView, display the edit appointment form.
            btnModifyAppointment.setOnAction(e -> {
                if (tvAppointment.getSelectionModel().getSelectedItem() != null) {
                    tvAppointment.setItems(displayAppointment(tvAppointment.getSelectionModel().getSelectedItem()));
                } else {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("No Selection");
                    alert.setHeaderText("");
                    alert.setContentText("Please select an appointment from the calendar.");
                    alert.showAndWait();
                }
            });

            // Prompt the user for confirmation to delete the selected appointment.
            btnDeleteAppointment.setOnAction(e -> {
                if (tvAppointment.getSelectionModel().getSelectedItem() != null) {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Confirm Deletion");
                    alert.setHeaderText("Are you sure you wish to delete this appointment?");

                    Optional<ButtonType> result = alert.showAndWait();
                    if (result.get() == ButtonType.OK) {
                        try {
                            conn.deleteAppointment(tvAppointment.getSelectionModel().getSelectedItem().getId());
                            tvAppointment.setItems(conn.getAppointmentsInRange(startDate[0], endDate[0], currentUser.getId()));
                        } catch (SQLException sqle) {
                            sqle.printStackTrace();
                        }
                    }
                } else {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("No Selection");
                    alert.setHeaderText("");
                    alert.setContentText("Please select an appointment from the calendar.");
                    alert.showAndWait();
                }
            });

            // Display an Alert box that details the number appointments by type for the current month.
            btnReportType.setOnAction(e -> {
                String alertBody = null;
                try {
                    alertBody = conn.getAppointmentsByType();
                } catch (SQLException sqle) {
                    sqle.printStackTrace();
                }

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Appointment Types By Month");
                alert.setHeaderText(
                        "The following data indicates the number of appointment\n" +
                                "types that occur in " + LocalDate.now().getMonth().getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()) + " " + LocalDate.now().getYear() + ":"
                );
                alert.setContentText(alertBody);
                alert.showAndWait();
            });

            // Display a report form that details the appointments for each user in the database.
            btnConsultantReport.setOnAction(e -> {
                displayConsultantReport();
            });

            // Display a report form that details the appointments associated to each contact in the appointments table.
            btnContactReport.setOnAction(e -> {
                displayContactReport();
            });

            // Display a pop-up window that allows the user to view a TableView of all customers in the database.
            btnViewCustomers.setOnAction(e -> {
                displayCustomers();
            });

            // Actually display the Stage
            primaryStage.setTitle("Appointment Manager");
            primaryStage.setScene(scene);
            primaryStage.show();
        }
    }

    // We override the stop() method of the Application class so we can clean up any residual connections when the app closes
    @Override
    public void stop(){
        conn.close();
    }

    // The login form. This is the first page seen by the user.
    public User displayLogin() {
        // Define necssary controls and variables.
        final String[] ACCEPTED_LANGUAGES = {"en", "es"};
        User userToReturn = new User();
        Stage loginStage = new Stage();
        GridPane gpRoot = new GridPane();
        Scene scene = new Scene(gpRoot, 350, 275);
        Label lblLoginHeading = new Label(getLocaleString(Locale.getDefault(), "Login", "heading"));
        Label lblUsername = new Label(getLocaleString(Locale.getDefault(), "Login", "username"));
        TextField tfUsername = new TextField();
        Label lblPassword = new Label(getLocaleString(Locale.getDefault(), "Login", "password"));
        PasswordField tfPassword = new PasswordField();
        Label lblLanguage = new Label(getLocaleString(Locale.getDefault(), "Login", "language"));
        ComboBox<String> cbLanguage = new ComboBox(FXCollections.observableArrayList(ACCEPTED_LANGUAGES));
        Button btnSubmit = new Button(getLocaleString(Locale.getDefault(), "Login", "btnSubmit"));
        DateTimeFormatter dtfDisplayDates = DateTimeFormatter.ofPattern("MM/d/yyyy h:mm a");

        // Bring in the stylesheet
        scene.getStylesheets().add(getClass().getResource("/root.css").toExternalForm());

        // Set layout and style for gridpane, and position controls inside.
        gpRoot.getStyleClass().add("gridpane");

        gpRoot.add(lblLoginHeading, 0, 0, 2, 1);
        gpRoot.add(lblUsername, 0, 1);
        gpRoot.add(tfUsername, 1, 1);
        gpRoot.add(lblPassword, 0, 2);
        gpRoot.add(tfPassword, 1, 2);
        gpRoot.add(lblLanguage, 0, 3);
        gpRoot.add(cbLanguage, 1, 3);
        gpRoot.add(btnSubmit, 0, 4, 2, 1);

        // Set style for heading.
        lblLoginHeading.getStyleClass().add("loginHeading");

        // Auto select the first element in the combobox to avoid nulls.
        cbLanguage.getSelectionModel().select(cbLanguage.getItems().indexOf(Locale.getDefault().getLanguage()));

        // Update the default Locale and update labels to reflect new language when combobox selection changes.
        cbLanguage.getSelectionModel().selectedItemProperty().addListener((options, oldValue, newValue) -> {
            Locale.setDefault(Locale.forLanguageTag(newValue));
            lblLoginHeading.setText(getLocaleString(new Locale(newValue), "Login", "heading"));
            lblUsername.setText(getLocaleString(new Locale(newValue), "Login", "username"));
            lblPassword.setText(getLocaleString(new Locale(newValue), "Login", "password"));
            lblLanguage.setText(getLocaleString(new Locale(newValue), "Login", "language"));
            btnSubmit.setText(getLocaleString(new Locale(newValue), "Login", "btnSubmit"));
        });

        btnSubmit.setPrefSize(300, 50);

        btnSubmit.setOnAction(e -> {
            // We'll use these a lot for comparison, so storing them seems more efficient.
            String username = tfUsername.getText();
            String password = tfPassword.getText();

            // If the user actually entered data...
            if(!username.isEmpty() && !password.isEmpty()) {
                try {
                    // Determine if a user account exists for the credentials provided.
                    Integer userId = conn.checkUser(username, password);

                    // If the user does exist in the database...
                    if(userId != null) {
                        // Build out a User variable to return
                        userToReturn.setId(userId);
                        userToReturn.setUsername(username);
                        userToReturn.setLanguage(new Locale(cbLanguage.getSelectionModel().getSelectedItem().toString()));

                        // Write this login to the login history.
                        appendLoginHistory(username);

                        // Check if there is an appointment occurring within 15 minutes of the login
                        Appointment upcomingAppointment = conn.checkForUpcomingAppointment(userId);
                        // If there is an upcoming appointment, show an Alert with it's data.
                        if(null != upcomingAppointment) {
                            alert.setAlertType(Alert.AlertType.INFORMATION);
                            alert.setTitle("Info");
                            alert.setHeaderText(getLocaleString(Locale.getDefault(), "Login", "infoAppointment1"));
                            alert.setContentText(
                                getLocaleString(Locale.getDefault(), "Login", "infoAppointment2") + ":    " + upcomingAppointment.getTitle() + "\n" +
                                getLocaleString(Locale.getDefault(), "Login", "infoAppointment3") + ":    " + upcomingAppointment.getStart().format(dtfDisplayDates) + "\n" +
                                getLocaleString(Locale.getDefault(), "Login", "infoAppointment4") + ":    " + upcomingAppointment.getEnd().format(dtfDisplayDates) + "\n"
                            );
                            alert.showAndWait();
                        }

                        loginStage.close();
                    } else {
                        alert.setAlertType(Alert.AlertType.ERROR);
                        alert.setTitle("Error");
                        alert.setHeaderText(null);
                        alert.setContentText(getLocaleString(Locale.getDefault(), "Login", "errNoMatch"));
                        alert.showAndWait();
                    }
                } catch (SQLException sqle) {
                    sqle.printStackTrace();
                }
            } else {
                alert.setAlertType(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setContentText(getLocaleString(Locale.getDefault(), "Login", "errBlank"));
                alert.showAndWait();
            }
        });

        loginStage.setTitle("Login");
        loginStage.setScene(scene);
        loginStage.showAndWait();

        return userToReturn;
    }

    // Builds and formats the customer table as needed
    public TableView<Customer> buildCustomerTable() {
        TableView<Customer> tvCustomers = new TableView<>();

        TableColumn<Customer, String> column1 = new TableColumn<>("Id");
        column1.setCellValueFactory(new PropertyValueFactory<>("id"));

        TableColumn<Customer, String> column2 = new TableColumn<>("Name");
        column2.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<Customer, String> column3 = new TableColumn<>("Address");
        column3.setCellValueFactory(new PropertyValueFactory<>("address"));

        TableColumn<Customer, String> column4 = new TableColumn<>("Phone Number");
        column4.setCellValueFactory(new PropertyValueFactory<>("phone"));

        tvCustomers.getColumns().addAll(column1,column2,column3,column4);

        tvCustomers.setItems(conn.getAllCustomers());

        return tvCustomers;
    }

    // Builds and formats the appointment table as needed, including formatting readable datetimes.
    public TableView<Appointment> buildAppointmentTable() {
        TableView<Appointment> tvAppointment = new TableView<Appointment>();
        DateTimeFormatter dtfDisplayDates = DateTimeFormatter.ofPattern("MM/d/yyyy h:mm a");

        TableColumn<Appointment, String> column1 = new TableColumn<>("Id");
        column1.setCellValueFactory(new PropertyValueFactory<>("id"));

        TableColumn<Appointment, Integer> column2 = new TableColumn<>("User");
        column2.setCellValueFactory(new PropertyValueFactory<>("userId"));
        // Custom Lambda expression that translates the userId into a human readable username
        column2.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);

                if (item == null || empty) {
                    setText(null);
                } else {
                    try {
                        setText(conn.getUsername(item));
                    } catch (SQLException sqle) {
                        sqle.printStackTrace();
                    }
                }
            }
        });

        TableColumn<Appointment, String> column3 = new TableColumn<>("Title");
        column3.setCellValueFactory(new PropertyValueFactory<>("title"));

        TableColumn<Appointment, String> column4 = new TableColumn<>("Type");
        column4.setCellValueFactory(new PropertyValueFactory<>("type"));

        TableColumn<Appointment, ZonedDateTime> column5 = new TableColumn<>("Start");
        column5.setCellValueFactory(new PropertyValueFactory<>("start"));
        // Custom Lambda that formats the date columns to be human readable.
        column5.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(ZonedDateTime item, boolean empty) {
                super.updateItem(item, empty);

                if (item == null || empty) {
                    setText(null);
                } else {
                    setText(item.format(dtfDisplayDates));
                }
            }
        });

        TableColumn<Appointment, ZonedDateTime> column6 = new TableColumn<>("End");
        column6.setCellValueFactory(new PropertyValueFactory<>("end"));
        // Custom Lambda that formats the date columns to be human readable.
        column6.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(ZonedDateTime item, boolean empty) {
                super.updateItem(item, empty);

                if (item == null || empty) {
                    setText(null);
                } else {
                    setText(item.format(dtfDisplayDates));
                }
            }
        });

        tvAppointment.getColumns().addAll(column1, column2, column3, column4, column5, column6);
        tvAppointment.setMinWidth(525);

        return tvAppointment;
    }

    // Display a pop-up window containing a TableView of all customers in the database.
    public void displayCustomers() {
        // Define necessary controls and variables.
        Stage customersStage = new Stage();
        GridPane gpRoot = new GridPane();
        Scene scene = new Scene(gpRoot, 350, 275);
        TableView<Customer> tvCustomer = buildCustomerTable();
        Button btnAdd = new Button("Add");
        Button btnEdit = new Button("View/Edit");
        Button btnDelete = new Button("Delete");

        // Bring in the stylesheet
        scene.getStylesheets().add(getClass().getResource("/root.css").toExternalForm());

        // Set layout and style for gridpane, and position controls inside.
        gpRoot.getStyleClass().add("gridpane");
        gpRoot.add(tvCustomer, 0, 0);
        gpRoot.add(new HBox(25, btnAdd, btnEdit, btnDelete), 0, 1);

        // Display the form to add a new customer.
        btnAdd.setOnAction(e -> {
            tvCustomer.setItems(displayViewCustomer(null));
        });

        // If a customer is selected in the TableView, launch the form to edit that customer.
        btnEdit.setOnAction(e -> {
            if(tvCustomer.getSelectionModel().getSelectedItem() != null) {
                tvCustomer.setItems(displayViewCustomer(tvCustomer.getSelectionModel().getSelectedItem()));
            }  else {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("No Selection");
                alert.setHeaderText("");
                alert.setContentText("Please select a customer from the table.");
                alert.showAndWait();
            }
        });

        // If a customer is selected in the TableView, prompt the user for confirmation of deletion, and launch the form
        // actually delete that customer.
        btnDelete.setOnAction(e -> {
            if(tvCustomer.getSelectionModel().getSelectedItem() != null) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Confirm Deletion");
                alert.setHeaderText("Are you sure you wish to delete this customer?");
                alert.setContentText(null);

                Optional<ButtonType> result = alert.showAndWait();
                if (result.get() == ButtonType.OK){
                    try {
                        conn.deleteCustomer(tvCustomer.getSelectionModel().getSelectedItem().getId());
                        tvCustomer.setItems(conn.getAllCustomers());
                    } catch (SQLException sqle) {
                        sqle.printStackTrace();
                    }
                }
            }  else {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("No Selection");
                alert.setHeaderText("");
                alert.setContentText("Please select a customer from the table.");
                alert.showAndWait();
            }
        });

        customersStage.setTitle("Customers");
        customersStage.setScene(scene);
        customersStage.show();
    }

    // Display a form which can be used to either edit or create customer(s) depending upon the data passed.
    public ObservableList<Customer> displayViewCustomer(Customer currentCustomer){
        Stage customersStage = new Stage();
        GridPane gpRoot = new GridPane();
        Scene scene = new Scene(gpRoot, 450, 500);
        Label lblName = new Label(getLocaleString(Locale.getDefault(), "Customer", "lblName"));
        TextField tfName = new TextField();
        Label lblAddress = new Label(getLocaleString(Locale.getDefault(), "Customer", "lblAddress"));
        TextField tfAddress = new TextField();
        Label lblAddress2 = new Label(getLocaleString(Locale.getDefault(), "Customer", "lblAddress2"));
        TextField tfAddress2 = new TextField();
        Label lblCity = new Label(getLocaleString(Locale.getDefault(), "Customer", "lblCity"));
        TextField tfCity = new TextField();
        Label lblCountry = new Label(getLocaleString(Locale.getDefault(), "Customer", "lblCountry"));
        TextField tfCountry = new TextField();
        Label lblPostalCode = new Label(getLocaleString(Locale.getDefault(), "Customer", "lblPostalCode"));
        TextField tfPostalCode = new TextField();
        Label lblPhone = new Label(getLocaleString(Locale.getDefault(), "Customer", "lblPhone"));
        TextField tfPhone = new TextField();
        Button btnSave = new Button(getLocaleString(Locale.getDefault(), "Customer", "btnSave"));
        Button btnCancel = new Button(getLocaleString(Locale.getDefault(), "Customer", "btnCancel"));

        // Bring in the stylesheet
        scene.getStylesheets().add(getClass().getResource("/root.css").toExternalForm());

        // Set layout and style for gridpane, and position controls inside.
        gpRoot.getStyleClass().add("gridpane");
        gpRoot.add(lblName, 0, 0);
        gpRoot.add(tfName, 1, 0);
        gpRoot.add(lblAddress, 0, 1);
        gpRoot.add(tfAddress, 1, 1);
        gpRoot.add(lblAddress2, 0, 2);
        gpRoot.add(tfAddress2, 1, 2);
        gpRoot.add(lblCity, 0, 3);
        gpRoot.add(tfCity, 1, 3);
        gpRoot.add(lblCountry, 0, 4);
        gpRoot.add(tfCountry, 1, 4);
        gpRoot.add(lblPostalCode, 0, 5);
        gpRoot.add(tfPostalCode, 1, 5);
        gpRoot.add(lblPhone, 0, 6);
        gpRoot.add(tfPhone, 1, 6);
        gpRoot.add(btnSave, 0, 7);
        gpRoot.add(btnCancel, 2, 7);

        // If an existing Customer was passed, we set all fields to the values of that Customer's properties.
        if(null != currentCustomer) {
            tfName.setText(currentCustomer.getName());
            tfAddress.setText(currentCustomer.getAddress());
            tfAddress2.setText(currentCustomer.getAddress2());
            tfCity.setText(currentCustomer.getCity());
            tfCountry.setText(currentCustomer.getCountry());
            tfPostalCode.setText(String.valueOf(currentCustomer.getPostalCode()));
            tfPhone.setText(currentCustomer.getPhone());
        }

        // Close the stage
        btnCancel.setOnAction(e -> {
            customersStage.close();
        });

        btnSave.setOnAction(e -> {
            // If none of the fields are blank...
            if(
                    !tfName.getText().isEmpty() &&
                    !tfAddress.getText().isEmpty() &&
                    !tfAddress2.getText().isEmpty() &&
                    !tfCity.getText().isEmpty() &&
                    !tfCountry.getText().isEmpty() &&
                    !tfPostalCode.getText().isEmpty() &&
                    !tfPhone.getText().isEmpty()
            ) {
                if(tfPhone.getText().matches("^\\([0-9]{3}\\) [0-9]{3}-[0-9]{4}$")) {
                    // If a Customer wasn't passed in...
                    if (currentCustomer == null) {
                        try {
                            // If a country with this name already exists, grab it's ID, else create it and return the newly created ID
                            int countryId = conn.insertCountry(tfCountry.getText(), currentUser.getUsername());
                            // If a city with this name already exists, grab it's ID, else create it and return the newly created ID
                            int cityId = conn.insertCity(tfCity.getText(), countryId, currentUser.getUsername());
                            // If an address with these details already exists, grab it's ID, else create it and return the newly created ID
                            int addressId = conn.insertAddress(tfAddress.getText(), tfAddress2.getText(), cityId, tfPostalCode.getText(), tfPhone.getText(), currentUser.getUsername());
                            // Create a new Customer using the data provided in the form.
                            conn.insertCustomer(tfName.getText(), addressId, currentUser.getUsername());
                        } catch (SQLException sqle) {
                            sqle.printStackTrace();
                        }
                    } else {
                        try {
                            // In order to do a cascading update, we query all the IDs associated to the customer either directly,
                            // or indirectly.
                            ResultSet rsCustomer = conn.getIdsForCustomer(currentCustomer.getId());
                            // Move ResultSet pointer to index that actually contains data.
                            rsCustomer.next();
                            // Initialize all id variables.
                            int countryId = rsCustomer.getInt("countryId");
                            int cityId = rsCustomer.getInt("cityId");
                            int addressId = rsCustomer.getInt("addressId");

                            // Update all data in the database.
                            conn.updateCountry(tfCountry.getText(), countryId, currentUser.getUsername());
                            conn.updateCity(tfCity.getText(), cityId, currentUser.getUsername());
                            conn.updateAddress(tfAddress.getText(), tfAddress2.getText(), tfPostalCode.getText(), tfPhone.getText(), addressId, currentUser.getUsername());
                            conn.updateCustomer(tfName.getText(), currentCustomer.getId(), currentUser.getUsername());
                        } catch (SQLException sqle) {
                            sqle.printStackTrace();
                        }
                    }

                    alert.setAlertType(Alert.AlertType.INFORMATION);
                    alert.setTitle("Saved");
                    alert.setHeaderText("Changes successfully saved.");
                    alert.setContentText(null);
                    alert.showAndWait();
                    customersStage.close();
                } else {
                    alert.setAlertType(Alert.AlertType.ERROR);
                    alert.setTitle("Error");
                    alert.setHeaderText("Please ensure the phone field is in the format: '(###) ###-####'");
                    alert.setContentText(null);
                    alert.showAndWait();
                }
            } else {
                alert.setAlertType(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("Please ensure all fields are complete.");
                alert.setContentText(null);
                alert.showAndWait();
            }
        });

        customersStage.setTitle("Customers");
        customersStage.setScene(scene);
        customersStage.showAndWait();

        return conn.getAllCustomers();
    }

    // Display a form which can be used to either edit or create appointment(s) depending upon the data passed.
    public ObservableList<Appointment> displayAppointment(Appointment currentAppointment) {
        ObservableList<Customer> olCustomer = conn.getAllCustomers();
        DateTimeFormatter dfTime = DateTimeFormatter.ofPattern("h:mm a");
        DateTimeFormatter dtfDisplayDates = DateTimeFormatter.ofPattern("MM/d/yyyy h:mm a");
        Stage appointmentStage = new Stage();
        GridPane gpRoot = new GridPane();
        Scene scene = new Scene(gpRoot, 450, 600);
        Label lblCustomer = new Label(getLocaleString(Locale.getDefault(), "Appointment", "lblCustomer"));
        ComboBox<String> cbCustomer = new ComboBox<>();
        Label lblTitle = new Label(getLocaleString(Locale.getDefault(), "Appointment", "lblTitle"));
        TextField tfTitle = new TextField();
        Label lblDescription = new Label(getLocaleString(Locale.getDefault(), "Appointment", "lblDescription"));
        TextField tfDescription = new TextField();
        Label lblLocation = new Label(getLocaleString(Locale.getDefault(), "Appointment", "lblLocation"));
        TextField tfLocation = new TextField();
        Label lblContact = new Label(getLocaleString(Locale.getDefault(), "Appointment", "lblContact"));
        TextField tfContact = new TextField();
        Label lblType = new Label(getLocaleString(Locale.getDefault(), "Appointment", "lblType"));
        TextField tfType = new TextField();
        Label lblUrl = new Label(getLocaleString(Locale.getDefault(), "Appointment", "lblUrl"));
        TextField tfUrl = new TextField();
        Label lblStart = new Label(getLocaleString(Locale.getDefault(), "Appointment", "lblStartDate"));
        DatePicker dpStartDate = new DatePicker(LocalDate.now());
        Label lblStartTime = new Label(getLocaleString(Locale.getDefault(), "Appointment", "lblStartTime"));
        TextField tfStartTime = new TextField(BUSINESS_HOURS_START.format(dfTime));
        Label lblEnd = new Label(getLocaleString(Locale.getDefault(), "Appointment", "lblEndDate"));
        DatePicker dpEndDate = new DatePicker(LocalDate.now());
        Label lblEndTime = new Label(getLocaleString(Locale.getDefault(), "Appointment", "lblEndTime"));
        TextField tfEndTime = new TextField(BUSINESS_HOURS_END.format(dfTime));
        Button btnSave = new Button(getLocaleString(Locale.getDefault(), "Appointment", "btnSave"));
        Button btnCancel = new Button(getLocaleString(Locale.getDefault(), "Appointment", "btnCancel"));

        // Bring in the stylesheet
        scene.getStylesheets().add(getClass().getResource("/root.css").toExternalForm());

        // Set layout and style for gridpane, and position controls inside.
        gpRoot.getStyleClass().add("gridpane");
        gpRoot.add(lblCustomer, 0, 0);
        gpRoot.add(cbCustomer, 1, 0);
        gpRoot.add(lblTitle, 0, 1);
        gpRoot.add(tfTitle, 1, 1);
        gpRoot.add(lblDescription, 0, 2);
        gpRoot.add(tfDescription, 1, 2);
        gpRoot.add(lblLocation, 0, 3);
        gpRoot.add(tfLocation, 1, 3);
        gpRoot.add(lblContact, 0, 4);
        gpRoot.add(tfContact, 1, 4);
        gpRoot.add(lblType, 0, 5);
        gpRoot.add(tfType, 1, 5);
        gpRoot.add(lblUrl, 0, 6);
        gpRoot.add(tfUrl, 1, 6);
        gpRoot.add(lblStart, 0, 7);
        gpRoot.add(dpStartDate, 1, 7);
        gpRoot.add(lblStartTime, 0, 8);
        gpRoot.add(tfStartTime, 1, 8);
        gpRoot.add(lblEnd, 0, 9);
        gpRoot.add(dpEndDate, 1, 9);
        gpRoot.add(lblEndTime, 0, 10);
        gpRoot.add(tfEndTime, 1, 10);
        gpRoot.add(btnSave, 0, 11);
        gpRoot.add(btnCancel, 2, 11);

        // We use a lambda in the foreach() method to populate the Customer combobox with all customer names.
        // This is more efficient and readable than simply using a for loop.
        olCustomer.forEach(customer -> {
            cbCustomer.getItems().add(customer.getName());
        });

        // If an Appoinment was passed, we populate the fields with the properties of that Appointment
        if(null != currentAppointment) {
            cbCustomer.getSelectionModel().select(currentAppointment.getCustomerName());
            tfTitle.setText(currentAppointment.getTitle());
            tfDescription.setText(currentAppointment.getDescription());
            tfLocation.setText(currentAppointment.getLocation());
            tfContact.setText(currentAppointment.getContact());
            tfType.setText(currentAppointment.getType());
            tfUrl.setText(String.valueOf(currentAppointment.getUrl()));
            dpStartDate.setValue(currentAppointment.getStart().toLocalDate());
            tfStartTime.setText(currentAppointment.getStart().format(dfTime));
            dpEndDate.setValue(currentAppointment.getEnd().toLocalDate());
            tfEndTime.setText(currentAppointment.getEnd().format(dfTime));
        }

        // Close the stage.
        btnCancel.setOnAction(e -> {
            appointmentStage.close();
        });

        btnSave.setOnAction(e -> {
            // If none of the fields are blank...
            if(
                !cbCustomer.getSelectionModel().isEmpty() &&
                !tfTitle.getText().isEmpty() &&
                !tfDescription.getText().isEmpty() &&
                !tfLocation.getText().isEmpty() &&
                !tfContact.getText().isEmpty() &&
                !tfType.getText().isEmpty() &&
                !tfUrl.getText().isEmpty() &&
                !dpStartDate.getValue().toString().isEmpty() &&
                !tfStartTime.getText().isEmpty() &&
                !dpEndDate.getValue().toString().isEmpty() &&
                !tfEndTime.getText().isEmpty()
            ) {
                // if dpStartDate is not after dpEndDate...
                if(!(dpStartDate.getValue().compareTo(dpEndDate.getValue()) > 0)) {
                    // If the time entered was a valid time
                    if(tfStartTime.getText().matches("\\d{1,2}:\\d{1,2} (AM|PM)") && tfEndTime.getText().matches("\\d{1,2}:\\d{1,2} (AM|PM)")) {
                        try {
                            LocalDateTime ldtStart = LocalDateTime.parse(dpStartDate.getValue().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " " + tfStartTime.getText(), DateTimeFormatter.ofPattern("yyyy-MM-dd h:mm a"));
                            LocalDateTime ldtEnd = LocalDateTime.parse(dpEndDate.getValue().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " " + tfEndTime.getText(), DateTimeFormatter.ofPattern("yyyy-MM-dd h:mm a"));
                            // If the start time is before the end time.
                            if (ldtStart.isBefore(ldtEnd)) {
                                // If time-slot begins on or after BUSINESS_HOURS_START and ends on or before BUSINESS_HOURS_END...
                                if (!ldtStart.toLocalTime().isBefore(BUSINESS_HOURS_START) && !ldtEnd.toLocalTime().isAfter(BUSINESS_HOURS_END)) {
                                    Appointment overlapAppointment = conn.checkForOverlappingAppointment(ldtStart, ldtEnd, currentUser.getId());
                                    try {
                                        // If an Appointment wasn't passed in, insert a new Appointment into the database.
                                        if (null == currentAppointment) {
                                            if (null == overlapAppointment) {
                                                conn.insertAppointment(
                                                        cbCustomer.getValue(),
                                                        currentUser.getId(),
                                                        tfTitle.getText(),
                                                        tfDescription.getText(),
                                                        tfLocation.getText(),
                                                        tfContact.getText(),
                                                        tfType.getText(),
                                                        tfUrl.getText(),
                                                        ldtStart,
                                                        ldtEnd,
                                                        currentUser.getUsername()
                                                );

                                                alert.setAlertType(Alert.AlertType.INFORMATION);
                                                alert.setTitle("Saved");
                                                alert.setHeaderText("Changes successfully saved.");
                                                alert.setContentText(null);
                                                alert.showAndWait();
                                                appointmentStage.close();
                                            } else {
                                                alert.setAlertType(Alert.AlertType.ERROR);
                                                alert.setTitle("Error");
                                                alert.setHeaderText("An existing appointment overlaps with the selected time slot.");
                                                alert.setContentText(
                                                        "Title:    " + overlapAppointment.getTitle() + "\n" +
                                                                "Start:    " + overlapAppointment.getStart().format(dtfDisplayDates) + "\n" +
                                                                "End:    " + overlapAppointment.getEnd().format(dtfDisplayDates) + "\n"
                                                );
                                                alert.showAndWait();
                                            }
                                        } else {
                                            // If an Appointment was passed in, update that Appointment in the database.
                                            if (null == overlapAppointment || overlapAppointment.getId() == currentAppointment.getId()) {
                                                conn.updateAppointment(
                                                        currentAppointment.getId(),
                                                        cbCustomer.getValue(),
                                                        currentUser.getId(),
                                                        tfTitle.getText(),
                                                        tfDescription.getText(),
                                                        tfLocation.getText(),
                                                        tfContact.getText(),
                                                        tfType.getText(),
                                                        tfUrl.getText(),
                                                        ldtStart,
                                                        ldtEnd,
                                                        currentUser.getUsername()
                                                );

                                                alert.setAlertType(Alert.AlertType.INFORMATION);
                                                alert.setTitle("Saved");
                                                alert.setHeaderText("Changes successfully saved.");
                                                alert.setContentText(null);
                                                alert.showAndWait();
                                                appointmentStage.close();
                                            } else {
                                                alert.setAlertType(Alert.AlertType.ERROR);
                                                alert.setTitle("Error");
                                                alert.setHeaderText("An existing appointment overlaps with the selected time slot.");
                                                alert.setContentText(
                                                        "Title:    " + overlapAppointment.getTitle() + "\n" +
                                                                "Start:    " + overlapAppointment.getStart().format(dtfDisplayDates) + "\n" +
                                                                "End:    " + overlapAppointment.getEnd().format(dtfDisplayDates) + "\n"
                                                );
                                                alert.showAndWait();
                                            }
                                        }
                                    } catch (SQLException sqle) {
                                        sqle.printStackTrace();
                                    }
                                } else {
                                    alert.setAlertType(Alert.AlertType.ERROR);
                                    alert.setTitle("Error");
                                    alert.setHeaderText(null);
                                    alert.setContentText(
                                            "You have selected a time slot outside of business hours. Please select a time slot between" +
                                                    BUSINESS_HOURS_START.format(DateTimeFormatter.ofPattern("h:mm a")) + " and " + BUSINESS_HOURS_END.format(DateTimeFormatter.ofPattern("h:mm a")) + "."
                                    );
                                    alert.showAndWait();
                                }
                            } else {
                                alert.setAlertType(Alert.AlertType.ERROR);
                                alert.setTitle("Error");
                                alert.setHeaderText(null);
                                alert.setContentText("Please ensure that the appointment start time occurs before the appointment end time.");
                                alert.showAndWait();
                            }
                        } catch (DateTimeParseException dtpe) {
                            alert.setAlertType(Alert.AlertType.ERROR);
                            alert.setTitle("Error");
                            alert.setHeaderText(null);
                            alert.setContentText("Please ensure that the start and/or end times you input are in the format \"9:00 AM\".");
                            alert.showAndWait();
                        }
                    } else {
                        alert.setAlertType(Alert.AlertType.ERROR);
                        alert.setTitle("Error");
                        alert.setHeaderText(null);
                        alert.setContentText("Please ensure that the start and/or end times you input are in the format \"9:00 AM\".");
                        alert.showAndWait();
                    }
                } else {
                    alert.setAlertType(Alert.AlertType.ERROR);
                    alert.setTitle("Error");
                    alert.setHeaderText(null);
                    alert.setContentText("Please ensure that the appointment start date occurs before the appointment end date.");
                    alert.showAndWait();
                }
            } else {
                alert.setAlertType(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(null);
                alert.setContentText("Please ensure that all fields are complete.");
                alert.showAndWait();
            }
        });

        appointmentStage.setTitle("Appointments");
        appointmentStage.setScene(scene);
        appointmentStage.showAndWait();

        return conn.getAppointmentsInRange(startDate[0], endDate[0], currentUser.getId());
    }

    // This is used to fetch the translation of a string from the specified ResourceBundle, for the specified Locale.
    public String getLocaleString(Locale locale, String bundleBaseName, String key) {
        ResourceBundle rb = ResourceBundle.getBundle(bundleBaseName, locale);
        return rb.getString(key);
    }

    // Display a report form containing a TableView that updates to show the appointments for the selected user.
    public void displayConsultantReport() {
        Stage reportStage = new Stage();
        GridPane gpRoot = new GridPane();
        Scene scene = new Scene(gpRoot, 580, 500);
        Label lblUsers = new Label("User: ");
        ComboBox<String> cbUsers = new ComboBox<>();
        TableView<Appointment> tvResult = buildAppointmentTable();

        // Bring in the stylesheet
        scene.getStylesheets().add(getClass().getResource("/root.css").toExternalForm());

        // Set layout and style for gridpane, and position controls inside.
        gpRoot.getStyleClass().add("gridpane");
        gpRoot.add(new HBox(10, lblUsers, cbUsers), 0, 0);
        gpRoot.add(tvResult, 0, 1);

        try {
            cbUsers.setItems(conn.getUsernames());
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        }

        // Lambda expression to update Appointment filter based on the current combobox selection.
        cbUsers.getSelectionModel().selectedItemProperty().addListener((options, oldValue, newValue) -> {
            tvResult.getItems().clear();
            if(!newValue.isEmpty() && !newValue.isBlank()) {
                try {
                    tvResult.setItems(conn.getConsultantReport(newValue));
                } catch (SQLException sqle) {
                    sqle.printStackTrace();
                }
            }
        });

        reportStage.setTitle("Consultant Report");
        reportStage.setScene(scene);
        reportStage.show();
    }

    // Display a report form containing a TableView that updates to show the appointments for the selected contact.
    public void displayContactReport() {
        Stage reportStage = new Stage();
        GridPane gpRoot = new GridPane();
        Scene scene = new Scene(gpRoot,580, 500);
        Label lblContacts = new Label("Contact: ");
        ComboBox<String> cbContacts = new ComboBox<>();
        TableView<Appointment> tvResult = buildAppointmentTable();

        // Bring in the stylesheet
        scene.getStylesheets().add(getClass().getResource("/root.css").toExternalForm());

        // Set layout and style for gridpane, and position controls inside.
        gpRoot.getStyleClass().add("gridpane");
        gpRoot.add(new HBox(10, lblContacts, cbContacts), 0, 0);
        gpRoot.add(tvResult, 0, 1);

        try {
            cbContacts.setItems(conn.getUniqueContacts());
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        }

        // Lambda expression to update Appointment filter based on the current combobox selection.
        cbContacts.getSelectionModel().selectedItemProperty().addListener((options, oldValue, newValue) -> {
            tvResult.getItems().clear();
            if(!newValue.isEmpty() && !newValue.isBlank()) {
                try {
                    tvResult.setItems(conn.getContactReport(newValue));
                } catch (SQLException sqle) {
                    sqle.printStackTrace();
                }
            }
        });

        reportStage.setTitle("Contact Report");
        reportStage.setScene(scene);
        reportStage.show();
    }

    // This is used to append to/create the LoginHistory.txt file.
    public  void appendLoginHistory(String username) {
        // Create a UTC ISO 8601 timestamp to insert into the file.
        String loginTimestamp = ZonedDateTime.now(TimeZone.getTimeZone("UTC").toZoneId()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'"));

        // Creates a reference to the LoginHistory.txt file in the current working directory.
        File loginHistoryFile = new File("LoginHistory.txt");
        FileWriter fw = null;

        try {
            // If the file already exists, and is actually a file...
            if (loginHistoryFile.exists() && loginHistoryFile.isFile()) {
                // Instantiate a FileWriter, use it to append the login info to out log, and close the FileWriter.
                fw = new FileWriter(loginHistoryFile, true);
                fw.append(loginTimestamp).append(" | User named: '").append(username).append("' logged into the appointment management system.");
                fw.close();
            } else {
                // Create the file if it doesn't exist. This is in an if statement to check that the file was actually created.
                if(loginHistoryFile.createNewFile()) {
                    // Instantiate a FileWriter, use it to write a heading message that explains the file, append the login
                    // info to out log, and close the FileWriter.
                    fw = new FileWriter(loginHistoryFile);
                    fw.append("// This file is meant to track every time a user logs into the appointment management system. All timestamps are stored in UTC for portability.\n\n");
                    fw.append(loginTimestamp).append(" | User named: '").append(username).append("' logged into the appointment management system.\n");
                    fw.close();
                } else {
                    System.err.println("Unable to create login history file.");
                }
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    // Default main() method required to start the program.
    public static void main(String[] args) {
        launch(args);
    }
}